package com.tobevpn.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a downloaded APK to the system package installer.
 *
 * Permission model on Android TV is the same as on phone: API 26+ requires
 * the user to flip "Allow from this source" exactly once. The system Settings
 * screen for that toggle is keyboard/D-pad navigable, so TV remotes work
 * fine — just not as polished as on touch.
 *
 * The APK is exposed via FileProvider. ACTION_VIEW with a content:// URI
 * triggers the system installer (PackageInstaller), which prompts
 * "Установить ToBeVPN TV?". The user navigates with D-pad and taps OK.
 */
@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun canInstallSilently(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildPermissionIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun install(apkUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun resolveContentUri(localUri: Uri): Uri {
        val path = localUri.path ?: return localUri
        val file = File(path)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
