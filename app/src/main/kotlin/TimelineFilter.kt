package dev.doji.adx

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Classifies promoted URT items and removes them from top-level and module lists. */
internal class TimelineFilter(
    private val itemClass: Class<*>,
    private val moduleClass: Class<*>,
    private val moduleContent: Method,
    private val moduleItemValue: Method,
    private val moduleCopy: Method,
    private val warn: (String, Throwable) -> Unit,
) {
    private val inspectors = ConcurrentHashMap<Class<*>, ItemInspector>()

    fun filter(source: List<*>): Result {
        val output = ArrayList<Any?>(source.size)
        var removed = 0

        for (item in source) {
            when {
                item == null || !itemClass.isInstance(item) -> output += item
                isPromoted(item) -> removed++
                moduleClass.isInstance(item) -> {
                    val result = filterModule(item)
                    if (result.item !== DROP) output += result.item
                    removed += result.removed
                }
                else -> output += item
            }
        }
        return Result(output, removed)
    }

    private fun filterModule(module: Any): ModuleResult {
        return try {
            val content = moduleContent.invoke(module) as? List<*>
                ?: return ModuleResult(module)
            if (content.isEmpty()) return ModuleResult(module)

            val output = ArrayList<Any?>(content.size)
            var removed = 0
            for (wrapper in content) {
                val nested = moduleItemValue.invoke(wrapper)
                if (nested != null && isPromoted(nested)) removed++ else output += wrapper
            }
            if (removed == 0) return ModuleResult(module)

            val replacement = if (output.isEmpty()) {
                DROP
            } else {
                moduleCopy.invoke(
                    null,
                    module,
                    output,
                    null,
                    null,
                    null,
                    0L,
                    null,
                    null,
                    MODULE_COPY_DEFAULT_MASK,
                    null,
                )
            }
            ModuleResult(replacement, removed)
        } catch (error: Throwable) {
            warn("Nested module filtering failed; keeping module", error)
            ModuleResult(module)
        }
    }

    private fun isPromoted(item: Any): Boolean = try {
        inspectors.computeIfAbsent(item.javaClass, ::createInspector).isPromoted(item)
    } catch (error: Throwable) {
        warn("Promoted classification failed for ${item.javaClass.name}; keeping item", error)
        false
    }

    private fun createInspector(type: Class<*>): ItemInspector {
        val direct = type.noArgMethod("getPromotedMetadata")
        val nested = if (direct == null) {
            type.noArgMethod("getTimelineTrend") ?: type.noArgMethod("getEventSummary")
        } else {
            null
        }
        return ItemInspector(
            alwaysPromoted = type.name == XTarget.Timeline.RTB_IMAGE_AD,
            direct = direct,
            nested = nested,
            nestedPromoted = nested?.returnType?.noArgMethod("getPromotedMetadata"),
            entryId = type.noArgMethod("getEntryId"),
        )
    }

    class Result(val items: List<Any?>, val removed: Int)

    private class ModuleResult(
        val item: Any?,
        val removed: Int = 0,
    )

    private class ItemInspector(
        private val alwaysPromoted: Boolean,
        private val direct: Method?,
        private val nested: Method?,
        private val nestedPromoted: Method?,
        private val entryId: Method?,
    ) {
        fun isPromoted(item: Any): Boolean {
            if (alwaysPromoted || direct?.invoke(item) != null) return true
            val nestedValue = nested?.invoke(item)
            if (nestedValue != null && nestedPromoted?.invoke(nestedValue) != null) return true
            return (entryId?.invoke(item) as? String)?.startsWith(PROMOTED_ENTRY_PREFIX) == true
        }
    }

    private companion object {
        const val MODULE_COPY_DEFAULT_MASK = 126
        const val PROMOTED_ENTRY_PREFIX = "promoted-"
        val DROP = Any()
    }
}
