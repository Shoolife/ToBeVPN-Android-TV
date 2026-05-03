package com.tobevpn.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.tobevpn.tv.BuildConfig
import com.tobevpn.tv.data.remote.GithubReleasesApi
import com.tobevpn.tv.data.remote.dto.GithubAssetDto
import com.tobevpn.tv.data.remote.dto.GithubReleaseDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates "is there a newer build?" check against GitHub Releases.
 *
 * Best-effort: any error returns [UpdateCheckResult.UpToDate] so a failed
 * probe never blocks startup.
 *
 * Caching: GitHub's unauthenticated API has a 60-req/hour cap per IP. With
 * many users behind one carrier-grade NAT we'd burn that budget on cold-launch
 * spam. We cache the result for [CACHE_TTL_MS] (7 days) — that's the cadence
 * we expect to ship at, and the user can manually re-check from Settings.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val api: GithubReleasesApi,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult {
        if (!force) {
            val cached = readCached()
            if (cached != null) return cached
        }
        val fresh = fetchFromNetwork()
        writeCached(fresh)
        return fresh
    }

    private suspend fun fetchFromNetwork(): UpdateCheckResult = runCatching {
        val release = api.latestRelease(GITHUB_OWNER, GITHUB_REPO)
        if (release.draft || release.prerelease) {
            return@runCatching UpdateCheckResult.UpToDate
        }

        val latest = parseSemver(release.tagName) ?: return@runCatching UpdateCheckResult.UpToDate
        val current = parseSemver(BuildConfig.VERSION_NAME) ?: return@runCatching UpdateCheckResult.UpToDate
        if (compareSemver(latest, current) <= 0) {
            return@runCatching UpdateCheckResult.UpToDate
        }

        val apk = pickApkAsset(release) ?: return@runCatching UpdateCheckResult.UpToDate

        UpdateCheckResult.Available(
            versionName = release.tagName.removePrefix("v"),
            releaseNotes = release.body.orEmpty(),
            releasePageUrl = release.htmlUrl,
            apkUrl = apk.downloadUrl,
            apkSizeBytes = apk.size,
            apkFileName = apk.name,
        )
    }.getOrElse { e ->
        Log.w(TAG, "update check failed", e)
        UpdateCheckResult.UpToDate
    }

    /**
     * Picks the APK asset matching this device's primary ABI. See the phone
     * client for full reasoning — same algorithm here.
     *
     * On Android TV the typical SUPPORTED_ABIS lists are:
     *   * Mi Box / Nvidia Shield / Chromecast Google TV → [arm64-v8a, armeabi-v7a]
     *   * legacy Sony / Philips / older Mi Box           → [armeabi-v7a]
     *   * emulators / Chromebook ATV containers          → [x86_64, x86]
     */
    private fun pickApkAsset(release: GithubReleaseDto): GithubAssetDto? {
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        for (abi in Build.SUPPORTED_ABIS) {
            val token = "-$abi-"
            apks.firstOrNull { it.name.contains(token, ignoreCase = true) }?.let { return it }
        }
        apks.firstOrNull { it.name.contains("-universal-", ignoreCase = true) }?.let { return it }
        return apks.first()
    }

    // ── Cache ───────────────────────────────────────────────────────────

    private fun readCached(): UpdateCheckResult? {
        val ts = prefs.getLong(KEY_CACHED_AT, 0L)
        if (ts == 0L) return null
        if (System.currentTimeMillis() - ts > CACHE_TTL_MS) return null
        return when (prefs.getString(KEY_CACHED_KIND, null)) {
            "uptodate" -> UpdateCheckResult.UpToDate
            "available" -> {
                val version = prefs.getString(KEY_VERSION, null) ?: return null
                // After the user installs an update, BuildConfig.VERSION_NAME
                // catches up but the SharedPreferences cache still holds the
                // pre-install "Available v1.0.1" record. Drop it so the next
                // probe writes a fresh result instead of nagging about a
                // version the user has already installed.
                val cached = parseSemver(version)
                val current = parseSemver(BuildConfig.VERSION_NAME)
                if (cached == null || current == null || compareSemver(cached, current) <= 0) {
                    return null
                }
                UpdateCheckResult.Available(
                    versionName = version,
                    releaseNotes = prefs.getString(KEY_NOTES, "") ?: "",
                    releasePageUrl = prefs.getString(KEY_PAGE_URL, "") ?: "",
                    apkUrl = prefs.getString(KEY_APK_URL, null) ?: return null,
                    apkSizeBytes = prefs.getLong(KEY_APK_SIZE, 0L),
                    apkFileName = prefs.getString(KEY_APK_NAME, null) ?: return null,
                )
            }
            else -> null
        }
    }

    private fun writeCached(result: UpdateCheckResult) {
        val editor = prefs.edit()
            .putLong(KEY_CACHED_AT, System.currentTimeMillis())
        when (result) {
            UpdateCheckResult.UpToDate -> {
                editor.putString(KEY_CACHED_KIND, "uptodate")
                    .remove(KEY_VERSION)
                    .remove(KEY_NOTES)
                    .remove(KEY_PAGE_URL)
                    .remove(KEY_APK_URL)
                    .remove(KEY_APK_SIZE)
                    .remove(KEY_APK_NAME)
            }
            is UpdateCheckResult.Available -> {
                editor.putString(KEY_CACHED_KIND, "available")
                    .putString(KEY_VERSION, result.versionName)
                    .putString(KEY_NOTES, result.releaseNotes)
                    .putString(KEY_PAGE_URL, result.releasePageUrl)
                    .putString(KEY_APK_URL, result.apkUrl)
                    .putLong(KEY_APK_SIZE, result.apkSizeBytes)
                    .putString(KEY_APK_NAME, result.apkFileName)
            }
        }
        editor.apply()
    }

    private companion object {
        const val TAG = "UpdateRepository"
        const val GITHUB_OWNER = "Shoolife"
        const val GITHUB_REPO = "ToBeVPN-Android-TV"

        const val PREFS_NAME = "tobevpn_update_cache"
        const val KEY_CACHED_AT = "cached_at"
        const val KEY_CACHED_KIND = "cached_kind"
        const val KEY_VERSION = "version"
        const val KEY_NOTES = "notes"
        const val KEY_PAGE_URL = "page_url"
        const val KEY_APK_URL = "apk_url"
        const val KEY_APK_SIZE = "apk_size"
        const val KEY_APK_NAME = "apk_name"

        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000  // 7 days
    }
}

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(
        val versionName: String,
        val releaseNotes: String,
        val releasePageUrl: String,
        val apkUrl: String,
        val apkSizeBytes: Long,
        val apkFileName: String,
    ) : UpdateCheckResult
}

internal fun parseSemver(raw: String): IntArray? {
    val cleaned = raw.removePrefix("v").substringBefore('-').substringBefore('+')
    val parts = cleaned.split('.')
    if (parts.size !in 1..3) return null
    val nums = IntArray(3)
    for (i in 0 until 3) {
        val piece = parts.getOrNull(i) ?: "0"
        nums[i] = piece.toIntOrNull() ?: return null
    }
    return nums
}

internal fun compareSemver(a: IntArray, b: IntArray): Int {
    for (i in 0 until 3) {
        val diff = a[i] - b[i]
        if (diff != 0) return diff
    }
    return 0
}
