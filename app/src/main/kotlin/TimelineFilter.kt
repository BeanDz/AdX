package dev.doji.adx

/** Classifies promoted URT items and removes them from top-level and module lists. */
internal class TimelineFilter(
    private val binding: X1222Binding,
    private val warn: (String, Throwable) -> Unit,
) {
    fun filter(source: List<*>): Result {
        val output = ArrayList<Any?>(source.size)
        var removed = 0

        for (item in source) {
            if (item == null || !binding.isTimelineItem(item)) {
                output += item
                continue
            }

            if (isPromoted(item)) {
                removed++
                continue
            }

            if (!binding.isTimelineModule(item)) {
                output += item
                continue
            }

            val result = filterModule(item)
            if (result.item != null) output += result.item
            removed += result.removed
        }
        return Result(output, removed)
    }

    private fun filterModule(module: Any): ModuleResult {
        return try {
            val content =
                binding.moduleContent(module)
                    ?: return ModuleResult(module)
            if (content.isEmpty()) return ModuleResult(module)

            val output = ArrayList<Any?>(content.size)
            var removed = 0
            for (wrapper in content) {
                val nested = binding.unwrapModuleItem(wrapper)
                if (nested != null && isPromoted(nested)) {
                    removed++
                    continue
                }
                output += wrapper
            }
            if (removed == 0) return ModuleResult(module)

            val replacement =
                if (output.isEmpty()) {
                    null
                } else {
                    binding.copyModule(module, output)
                }
            ModuleResult(replacement, removed)
        } catch (error: Exception) {
            warn("Nested module filter failed; kept module", error)
            ModuleResult(module)
        }
    }

    private fun isPromoted(item: Any): Boolean =
        try {
            binding.isPromoted(item)
        } catch (error: Exception) {
            warn("Promoted classification failed for ${item.javaClass.name}; kept item", error)
            false
        }

    class Result(
        val items: List<Any?>,
        val removed: Int,
    )

    private class ModuleResult(
        val item: Any?,
        val removed: Int = 0,
    )
}
