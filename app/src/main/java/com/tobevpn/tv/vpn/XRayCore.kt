package com.tobevpn.tv.vpn

import android.content.Context
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

object XRayCore {

    private var controller: CoreController? = null
    private val initialized = AtomicBoolean(false)

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        Seq.setContext(context.applicationContext)

        val assetDir = copyAssetsIfNeeded(context)
        Libv2ray.initCoreEnv(assetDir, "")
    }

    fun createController(callback: CoreCallbackHandler): CoreController {
        val ctrl = Libv2ray.newCoreController(callback)
        controller = ctrl
        return ctrl
    }

    fun startLoop(configJson: String, tunFd: Int) {
        controller?.startLoop(configJson, tunFd)
    }

    fun stopLoop() {
        controller?.stopLoop()
    }

    val isRunning: Boolean
        get() = controller?.isRunning == true

    fun queryStats(tag: String, direct: String): Long {
        return try {
            controller?.queryStats(tag, direct) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun getVersion(): String {
        return try {
            Libv2ray.checkVersionX()
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun copyAssetsIfNeeded(context: Context): String {
        val targetDir = context.filesDir.absolutePath
        val versionFile = File(targetDir, "assets_version")
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString()
        } catch (_: Exception) { "0" }
        val needsCopy = !versionFile.exists() || versionFile.readText().trim() != currentVersion

        if (needsCopy) {
            val assets = listOf("geoip.dat", "geosite.dat")
            for (asset in assets) {
                context.assets.open(asset).use { input ->
                    FileOutputStream(File(targetDir, asset)).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            versionFile.writeText(currentVersion)
        }
        return targetDir
    }
}
