package dev.doji.adx

import io.github.libxposed.api.XposedInterface.ExceptionMode
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** Installs the exact data hook validated for the supported X version. */
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
        val promotedMetadataClass = loader.resolve(names.PROMOTED_METADATA)
        val rtbImageAdClass = loader.resolve(names.RTB_IMAGE_AD)

        val stateConstructor =
            stateClass
                .getDeclaredConstructor(
                    loader.resolve(names.TYPE),
                    immutableListClass,
                    loader.resolve(names.REFRESH_STATE),
                    java.lang.Boolean.TYPE,
                    java.lang.Boolean.TYPE,
                ).accessible()
        val immutableAdapter =
            loader
                .resolve(names.IMMUTABLE_ADAPTER)
                .getDeclaredConstructor(List::class.java)
                .accessible()
        val moduleFields =
            moduleClass.declaredFields
                .filterNot { Modifier.isStatic(it.modifiers) }
                .map(Field::accessible)
        val moduleCopy =
            moduleClass.declaredConstructors
                .mapNotNull { constructor ->
                    constructor.mapFields(moduleFields)?.let { fields ->
                        constructor.accessible() to fields
                    }
                }.singleOrNull() ?: throw NoSuchMethodException("${names.MODULE} data constructor")
        val moduleConstructor = moduleCopy.first
        val moduleConstructorFields = moduleCopy.second
        val moduleContent =
            moduleConstructorFields.firstOrNull {
                List::class.java.isAssignableFrom(it.type)
            } ?: throw NoSuchFieldException("${names.MODULE} content")
        val moduleItemValue =
            moduleItemClass.fieldOfType(itemClass)
                ?: throw NoSuchFieldException("${names.MODULE_ITEM} item")

        val filter =
            TimelineFilter(
                itemClass = itemClass,
                promotedMetadataClass = promotedMetadataClass,
                rtbImageAdClass = rtbImageAdClass,
                moduleClass = moduleClass,
                moduleContent = moduleContent,
                moduleItemValue = moduleItemValue,
                copyModule = { module, content ->
                    moduleConstructor.newInstance(
                        *moduleConstructorFields
                            .map { field ->
                                if (field === moduleContent) content else field.get(module)
                            }.toTypedArray(),
                    )
                },
                warn = module::warn,
            )

        module
            .hook(stateConstructor)
            .setId("x-urt-promoted-state-filter")
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val source =
                    chain.getArg(1) as? List<*>
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
}

private fun Constructor<*>.mapFields(candidates: List<Field>): List<Field>? {
    val remaining = candidates.toMutableList()
    return parameterTypes.map { type ->
        val matches = remaining.filter { field -> field.type === type }
        if (matches.size != 1) return null
        matches.single().also(remaining::remove)
    }
}
