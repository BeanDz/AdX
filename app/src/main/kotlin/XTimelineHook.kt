package dev.doji.adx

import io.github.libxposed.api.XposedInterface.ExceptionMode

/** Installs the X 12.22 timeline interceptor. */
internal fun AdXModule.installXTimelineHook(loader: ClassLoader) {
    val binding = X1222Binding(loader)
    val filter = TimelineFilter(binding, ::warn)

    hook(binding.updateMethod)
        .setId("x-urt-promoted-update-filter")
        .setExceptionMode(ExceptionMode.PROTECTIVE)
        .intercept { chain ->
            val payload = chain.getArg(0) ?: return@intercept chain.proceed()
            val source =
                binding.timelineItems(payload)
                    ?: return@intercept chain.proceed()
            if (source.isEmpty()) return@intercept chain.proceed()

            val result = filter.filter(source)
            if (result.removed == 0) return@intercept chain.proceed()

            val args = chain.args.toTypedArray()
            args[0] = binding.copyPayload(payload, result.items)
            info(
                "Filtered promoted URT entries: removed=${result.removed}, " +
                    "topLevel=${source.size}->${result.items.size}",
            )
            chain.proceed(args)
        }

    info("Installed URT update hook: ${binding.updateMethod}")
}
