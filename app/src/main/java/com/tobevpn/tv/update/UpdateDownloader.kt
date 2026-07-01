package com.tobevpn.tv.update

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.tobevpn.tv.BuildConfig
import com.tobevpn.tv.vpn.VpnConfig
import com.tobevpn.tv.vpn.XRayCore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * Downloads the update APK inside the app process.
 *
 * The phone client uses the same in-process downloader. Android's system
 * DownloadManager runs under a separate system package, which can bypass the
 * route the app just used to discover the update on some devices/network
 * policies. Keeping the HTTP stream here makes phone and TV behaviour match.
 */
@Singleton
class UpdateDownloader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicLong(System.currentTimeMillis())
    private val tasks = ConcurrentHashMap<Long, DownloadTask>()
    private val directClient = buildHttpClient()
    private val tunnelClient = buildHttpClient(
        Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", VpnConfig.LOCAL_SOCKS_PORT)),
    )

    fun start(url: String, fileName: String): Long {
        cleanupStaleDownloads(removeRecent = true)
        val id = nextId.incrementAndGet()
        val targetFile = File(updateDirectory(), safeFileName(fileName))
        val task = DownloadTask(
            state = MutableStateFlow(DownloadProgress.Running(downloadedBytes = 0L, totalBytes = 0L)),
            targetFile = targetFile,
        )
        tasks[id] = task
        task.job = scope.launch { download(id, url, task) }
        return id
    }

    fun cancel(downloadId: Long) {
        cleanupDownload(downloadId)
    }

    /**
     * Removes APKs left by an older update attempt. A successful APK has to
     * remain available while Android's package installer is open, so startup
     * cleanup only removes old files. A replacement download clears them all.
     */
    fun cleanupStaleDownloads(removeRecent: Boolean = false) {
        if (removeRecent) {
            tasks.keys.toList().forEach(::cleanupDownload)
        }
        cleanupUpdateDirectories(removeRecent)
    }

    private fun cleanupDownload(downloadId: Long) {
        tasks.remove(downloadId)?.let { task ->
            task.call?.cancel()
            task.job?.cancel()
            task.targetFile.delete()
        }
    }

    fun observe(downloadId: Long): Flow<DownloadProgress> = flow {
        val task = tasks[downloadId] ?: run {
            emit(DownloadProgress.Failed("Download task disappeared"))
            return@flow
        }
        var previous: DownloadProgress? = null
        while (true) {
            val progress = task.state.first { it != previous }
            emit(progress)
            if (progress is DownloadProgress.Done || progress is DownloadProgress.Failed) {
                tasks.remove(downloadId)
                return@flow
            }
            previous = progress
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun download(downloadId: Long, url: String, task: DownloadTask) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", APK_MIME)
            .header("User-Agent", "ToBeVPN/${BuildConfig.VERSION_NAME}/android-tv/${BuildConfig.VERSION_CODE}")
            .build()
        try {
            val clients = if (XRayCore.isRunning) {
                listOf(tunnelClient, directClient)
            } else {
                listOf(directClient)
            }
            var lastError: Exception? = null
            for (client in clients) {
                try {
                    downloadOnce(client, request, task)
                    task.state.value = DownloadProgress.Done(Uri.fromFile(task.targetFile))
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    task.targetFile.delete()
                }
            }
            throw lastError ?: IOException("Download failed")
        } catch (e: CancellationException) {
            task.targetFile.delete()
            throw e
        } catch (e: Exception) {
            task.targetFile.delete()
            if (tasks.containsKey(downloadId)) {
                task.state.value = DownloadProgress.Failed(downloadFailureMessage(e))
            }
        } finally {
            task.call = null
        }
    }

    private suspend fun downloadOnce(
        client: OkHttpClient,
        request: Request,
        task: DownloadTask,
    ) {
        task.state.value = DownloadProgress.Running(downloadedBytes = 0L, totalBytes = 0L)
        task.targetFile.parentFile?.mkdirs()
        task.targetFile.delete()
        val call = client.newCall(request)
        task.call = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body
                val total = body.contentLength().coerceAtLeast(0L)
                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(task.targetFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            task.state.value = DownloadProgress.Running(downloaded, total)
                        }
                        output.fd.sync()
                    }
                }
            }
            if (task.targetFile.length() <= 0L) {
                throw IOException("Downloaded file is empty")
            }
        } finally {
            task.call = null
        }
    }

    private fun cleanupUpdateDirectories(removeRecent: Boolean) {
        listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            updateDirectory(),
        ).forEach { directory ->
            directory.listFiles()
                ?.filter { removeRecent || System.currentTimeMillis() - it.lastModified() >= STALE_DOWNLOAD_MS }
                ?.forEach { it.deleteRecursively() }
        }
    }

    private fun updateDirectory(): File = File(context.filesDir, "updates")

    private fun safeFileName(fileName: String): String {
        val base = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .takeIf { it.isNotBlank() }
            ?: "ToBeVPN-update.apk"
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun downloadFailureMessage(error: Exception): String {
        val message = error.message?.takeIf { it.isNotBlank() }
        return message ?: error::class.java.simpleName
    }

    private fun buildHttpClient(proxy: Proxy? = null): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .apply {
                if (proxy != null) this.proxy(proxy)
            }
            .build()

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val STALE_DOWNLOAD_MS = 24 * 60 * 60 * 1000L
    }

    private class DownloadTask(
        val state: MutableStateFlow<DownloadProgress>,
        val targetFile: File,
        var job: Job? = null,
        @Volatile var call: Call? = null,
    )
}

sealed interface DownloadProgress {
    data class Running(val downloadedBytes: Long, val totalBytes: Long) : DownloadProgress
    data class Done(val localUri: Uri) : DownloadProgress
    data class Failed(val reason: String) : DownloadProgress
}
