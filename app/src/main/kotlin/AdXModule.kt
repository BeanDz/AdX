package dev.doji.adx

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean

/** Removes promoted content from X without modifying the target APK. */
class AdXModule : XposedModule() {
    private val installed = AtomicBoolean()
    private var processName = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        info("Loaded in $processName using $frameworkName API $apiVersion")
        if (processName != XTarget.PACKAGE) detach()
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (processName != XTarget.PACKAGE ||
            param.packageName != XTarget.PACKAGE ||
            !param.isFirstPackage ||
            !installed.compareAndSet(false, true)
        ) {
            return
        }

        val hooks = XHooks(this, param.classLoader)
        if (!installSafely("URT data", hooks::installTimelineFilter)) installed.set(false)
    }

    private fun installSafely(
        name: String,
        install: () -> Unit,
    ): Boolean =
        try {
            install()
            true
        } catch (error: Throwable) {
            error("Failed to install $name hook", error)
            false
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
