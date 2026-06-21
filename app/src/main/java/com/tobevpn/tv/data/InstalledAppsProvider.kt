package com.tobevpn.tv.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

@Singleton
class InstalledAppsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun listApps(includeSystem: Boolean): List<InstalledAppItem> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val all = mutableMapOf<String, ApplicationInfo>()

            val installed = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (app in installed) all[app.packageName] = app

            val launcherCategories = listOf(
                Intent.CATEGORY_LAUNCHER,
                Intent.CATEGORY_LEANBACK_LAUNCHER,
            )
            for (category in launcherCategories) {
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(category)
                }
                val resolved = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(launcherIntent, 0)
                }
                for (info in resolved) {
                    val app = info.activityInfo.applicationInfo
                    all.putIfAbsent(app.packageName, app)
                }
            }

            all.values.asSequence()
                .filter { it.packageName != context.packageName }
                .filter { includeSystem || !it.isSystemNonUpdated() }
                .map { app ->
                    InstalledAppItem(
                        packageName = app.packageName,
                        label = app.loadLabel(pm).toString(),
                        isSystem = app.isSystemNonUpdated(),
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }

    private fun ApplicationInfo.isSystemNonUpdated(): Boolean {
        val isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0
        val isUpdatedSystem = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return isSystem && !isUpdatedSystem
    }
}
