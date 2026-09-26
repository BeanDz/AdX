package dev.doji.adx

import android.os.SystemClock
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/** Removes promoted content from X without modifying the target APK. */
class AdXModule : XposedModule() {
    private var processName = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        info("Loaded in $processName using $frameworkName API $apiVersion")
        if (processName != TARGET_PACKAGE) detach()
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (processName != TARGET_PACKAGE ||
            param.packageName != TARGET_PACKAGE ||
            !param.isFirstPackage
        ) {
            return
        }

        try {
            val started = SystemClock.elapsedRealtime()
            val symbols = TimelineDiscovery.discover(param.applicationInfo.sourceDir)
            val binding = TimelineBinding(param.classLoader, symbols)
            info("Discovered and validated timeline binding in ${SystemClock.elapsedRealtime() - started} ms")
            installXTimelineHook(binding)
        } catch (error: Throwable) {
            error("Timeline binding unavailable; filtering is inactive for this APK", error)
        }
    }

    internal fun info(message: String) = log(Log.INFO, TAG, message)

    internal fun warn(
        message: String,
        error: Throwable,
    ) = log(Log.WARN, TAG, message, error)

    private fun error(
        message: String,
        error: Throwable,
    ) = log(Log.ERROR, TAG, message, error)

    private companion object {
        const val TAG = "AdX"
        const val TARGET_PACKAGE = "com.twitter.android"
    }
}
