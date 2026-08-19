package dev.doji.adx

import io.github.libxposed.api.XposedInterface.ExceptionMode
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Installs the two exact hooks validated for the supported X version. */
internal class XHooks(
    private val module: AdXModule,
    private val loader: ClassLoader,
) {
    fun installTimelineFilter() {
        val names = XTarget.Timeline
        val stateClass = loader.resolve(names.STATE)
        val itemClass = loader.resolve(names.ITEM)
        val immutableListClass = loader.resolve(names.IMMUTABLE_LIST)
        val moduleClass = loader.resolve(names.MODULE)
        val moduleItemClass = loader.resolve(names.MODULE_ITEM)

        val stateConstructor = stateClass.getDeclaredConstructor(
            loader.resolve(names.TYPE),
            immutableListClass,
            loader.resolve(names.REFRESH_STATE),
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE,
        ).accessible()
        val immutableAdapter = loader.resolve(names.IMMUTABLE_ADAPTER)
            .getDeclaredConstructor(List::class.java)
            .accessible()
        val moduleCopy = moduleClass.declaredMethods.singleOrNull {
            it.name == "copy\$default" && it.parameterCount == MODULE_COPY_PARAMETER_COUNT
        }?.accessible() ?: throw NoSuchMethodException("${names.MODULE}.copy\$default")

        val filter = TimelineFilter(
            itemClass = itemClass,
            moduleClass = moduleClass,
            moduleContent = moduleClass.getMethod("getInnerContent"),
            moduleItemValue = moduleItemClass.getMethod("getItem"),
            moduleCopy = moduleCopy,
            warn = module::warn,
        )

        module.hook(stateConstructor)
            .setId("x-urt-promoted-state-filter")
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val source = chain.getArg(1) as? List<*>
                    ?: return@intercept chain.proceed()
                if (source.isEmpty()) return@intercept chain.proceed()

                val result = filter.filter(source)
                if (result.removed == 0) return@intercept chain.proceed()

                val args = chain.args.toTypedArray()
                args[1] = immutableAdapter.newInstance(result.items)
                module.info(
                    "Filtered promoted URT entries: removed=${result.removed}, " +
                        "topLevel=${source.size}->${result.items.size}",
                )
                chain.proceed(args)
            }

        module.info("Installed URT data hook: $stateConstructor")
    }

    fun installRenderGuard() {
        val names = XTarget.Renderer
        val promotedMetadataClass = loader.resolve(names.PROMOTED_METADATA)
        val renderer = loader.resolve(names.OWNER).getDeclaredMethod(
            names.METHOD,
            loader.resolve(names.STATE),
            loader.resolve(names.MODIFIER),
            loader.resolve(names.LAYOUT_STATE),
            loader.resolve(names.CONTENT_INSETS),
            loader.resolve(names.COMPOSER),
            Integer.TYPE,
            Integer.TYPE,
        ).accessible()
        val fieldCache = ConcurrentHashMap<Class<*>, FieldLookup>()
        val observed = AtomicBoolean()
        val suppressionLogged = AtomicBoolean()

        module.hook(renderer)
            .setId("x-promoted-post-render-filter")
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                if (observed.compareAndSet(false, true)) {
                    module.info("Observed shared post renderer")
                }

                val state = chain.getArg(0)
                val promoted = try {
                    state != null && fieldCache.computeIfAbsent(state.javaClass) { type ->
                        FieldLookup(type.fieldOfType(promotedMetadataClass))
                    }.field?.get(state) != null
                } catch (error: Throwable) {
                    module.warn("Post renderer classification failed; rendering item", error)
                    false
                }

                if (!promoted) return@intercept chain.proceed()
                if (suppressionLogged.compareAndSet(false, true)) {
                    module.info("Suppressed promoted post renderer")
                }
                null
            }

        module.info("Installed promoted-post renderer hook: $renderer")
    }

    private class FieldLookup(val field: Field?)

    private companion object {
        const val MODULE_COPY_PARAMETER_COUNT = 10
    }
}
