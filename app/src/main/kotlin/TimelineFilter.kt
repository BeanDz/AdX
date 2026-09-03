package dev.doji.adx

import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/** Classifies promoted URT items and removes them from top-level and module lists. */
internal class TimelineFilter(
    private val itemClass: Class<*>,
    private val promotedMetadataClass: Class<*>,
    private val moduleClass: Class<*>,
    private val moduleItemClass: Class<*>,
    private val moduleContent: Field,
    private val moduleItemValue: Field,
    private val copyModule: (Any, List<Any?>) -> Any,
    private val warn: (String, Throwable) -> Unit,
) {
    private val promotedMetadataReaders = ConcurrentHashMap<Class<*>, (Any) -> Any?>()

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
                val nested =
                    if (wrapper != null && moduleItemClass.isInstance(wrapper)) {
                        moduleItemValue.get(wrapper)
                    } else {
                        null
                    }
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
            promotedMetadataReaders
                .computeIfAbsent(item.javaClass, ::createPromotedMetadataReader)
                .invoke(item) != null
        } catch (error: Throwable) {
            warn("Promoted classification failed for ${item.javaClass.name}; kept item", error)
            false
        }

    private fun createPromotedMetadataReader(type: Class<*>): (Any) -> Any? =
        type.fieldOfType(promotedMetadataClass)?.let { field ->
            { item -> field.get(item) }
        } ?: { null }

    class Result(
        val items: List<Any?>,
        val removed: Int,
    )

    private class ModuleResult(
        val item: Any?,
        val removed: Int = 0,
    )

    private companion object {
        val DROP = Any()
    }
}
