package dev.doji.adx

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
        if (processName != XTarget.PACKAGE) detach()
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (processName != XTarget.PACKAGE ||
            param.packageName != XTarget.PACKAGE ||
            !param.isFirstPackage
        ) {
            return
        }

        try {
            XHooks(this, param.classLoader).installTimelineFilter()
        } catch (error: Throwable) {
            error("Failed to install URT data hook", error)
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
    }
}
