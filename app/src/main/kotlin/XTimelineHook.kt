package dev.doji.adx

import io.github.libxposed.api.XposedInterface.ExceptionMode
import java.util.concurrent.atomic.AtomicBoolean

/** Installs one validated interceptor and reports binding, update, and removal separately. */
internal fun AdXModule.installXTimelineHook(binding: TimelineBinding) {
    val filter = TimelineFilter(binding, ::warn)
    val observedUpdate = AtomicBoolean()

    fun filterPayload(payload: Any): Any? {
        val source = binding.timelineItems(payload) ?: return null
        if (observedUpdate.compareAndSet(false, true)) {
            info("Observed timeline update: entries=${source.size}")
        }
        if (source.isEmpty()) return null

        val result = filter.filter(source)
        if (result.removed == 0) return null
        val replacement = binding.copyPayload(payload, result.items)
        info(
            "Filtered promoted URT entries: removed=${result.removed}, " +
                "topLevel=${source.size}->${result.items.size}",
        )
        return replacement
    }

    hook(binding.updateMethod)
        .setId("x-urt-promoted-update-filter")
        .setExceptionMode(ExceptionMode.PROTECTIVE)
        .intercept { chain ->
            val payload = chain.getArg(0) ?: return@intercept chain.proceed()
            val replacement = try {
                filterPayload(payload)
            } catch (error: Exception) {
                warn("Timeline filtering failed; kept original update", error)
                null
            } ?: return@intercept chain.proceed()

            val args = chain.args.toTypedArray()
            args[0] = replacement
            chain.proceed(args)
        }

    info("Installed URT update hook: ${binding.updateMethod}")
}
