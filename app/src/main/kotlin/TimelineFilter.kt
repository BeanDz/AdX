package dev.doji.adx

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Classifies promoted URT items and removes them from top-level and module lists. */
internal class TimelineFilter(
    private val itemClass: Class<*>,
    private val promotedMetadataClass: Class<*>,
    private val rtbImageAdClass: Class<*>,
    private val moduleClass: Class<*>,
    private val moduleContent: Field,
    private val moduleItemValue: Field,
    private val copyModule: (Any, List<Any?>) -> Any,
    private val warn: (String, Throwable) -> Unit,
) {
    private val inspectors = ConcurrentHashMap<Class<*>, ItemInspector>()

    fun filter(source: List<*>): Result {
        val output = ArrayList<Any?>(source.size)
        var removed = 0

        for (item in source) {
            when {
                item == null || !itemClass.isInstance(item) -> {
                    output += item
                }

                isPromoted(item) -> {
                    removed++
                }

                moduleClass.isInstance(item) -> {
                    val result = filterModule(item)
                    if (result.item !== DROP) output += result.item
                    removed += result.removed
                }

                else -> {
                    output += item
                }
            }
        }
        return Result(output, removed)
    }

    private fun filterModule(module: Any): ModuleResult {
        return try {
            val content =
                moduleContent.get(module) as? List<*>
                    ?: return ModuleResult(module)
            if (content.isEmpty()) return ModuleResult(module)

            val output = ArrayList<Any?>(content.size)
            var removed = 0
            for (wrapper in content) {
                val nested = wrapper?.let(moduleItemValue::get)
                if (nested != null && isPromoted(nested)) removed++ else output += wrapper
            }
            if (removed == 0) return ModuleResult(module)

            val replacement =
                if (output.isEmpty()) {
                    DROP
                } else {
                    copyModule(module, output)
                }
            ModuleResult(replacement, removed)
        } catch (error: Throwable) {
            warn("Nested module filter failed; kept module", error)
            ModuleResult(module)
        }
    }

    private fun isPromoted(item: Any): Boolean =
        try {
            inspectors.computeIfAbsent(item.javaClass, ::createInspector).isPromoted(item)
        } catch (error: Throwable) {
            warn("Promoted classification failed for ${item.javaClass.name}; kept item", error)
            false
        }

    private fun createInspector(type: Class<*>): ItemInspector {
        val direct = type.fieldOfType(promotedMetadataClass)
        val nested = if (direct == null) type.nestedFieldOfType(promotedMetadataClass) else null
        return ItemInspector(
            alwaysPromoted = rtbImageAdClass.isAssignableFrom(type),
            direct = direct,
            nested = nested,
        )
    }

    class Result(
        val items: List<Any?>,
        val removed: Int,
    )

    private class ModuleResult(
        val item: Any?,
        val removed: Int = 0,
    )

    private class ItemInspector(
        private val alwaysPromoted: Boolean,
        private val direct: Field?,
        private val nested: NestedField?,
    ) {
        fun isPromoted(item: Any): Boolean {
            if (alwaysPromoted || direct?.get(item) != null) return true
            return nested?.get(item) != null
        }
    }

    private class NestedField(
        private val owner: Field,
        private val value: Field,
    ) {
        fun get(item: Any): Any? = owner.get(item)?.let(value::get)
    }

    private companion object {
        val DROP = Any()
    }

    private fun Class<*>.nestedFieldOfType(fieldType: Class<*>): NestedField? {
        for (field in declaredFields) {
            if (Modifier.isStatic(field.modifiers)) continue
            val nested = field.type.fieldOfType(fieldType) ?: continue
            return NestedField(field.accessible(), nested)
        }
        return null
    }
}
