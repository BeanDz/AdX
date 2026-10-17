package dev.doji.adx

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal class TimelineFilter(
    private val moduleClass: Class<*>,
    moduleItemClass: Class<*>,
    private val promotedUserClass: Class<*>,
    private val promotedMetadataMethod: Method,
) {
    private val moduleItemField =
        moduleItemClass.instanceFields().single { field ->
            field.type == promotedMetadataMethod.parameterTypes[0]
        }
    private val promotedUserMetadataField =
        promotedUserClass.instanceFields().single { it.type == promotedMetadataMethod.returnType }
    private val moduleContentField =
        moduleClass.instanceFields().single { it.type == List::class.java }

    init {
        promotedMetadataMethod.isAccessible = true
    }

    fun filter(source: List<*>): List<*> = source.filterNot(::isPromotedItem)

    // Drop the whole module instead of rebuilding it when one of its entries is promoted.
    private fun isPromotedItem(item: Any?): Boolean {
        if (isPromoted(item)) return true
        if (!moduleClass.isInstance(item)) return false
        val content = moduleContentField.get(item) as List<*>
        return content.any { isPromoted(moduleItemField.get(it)) }
    }

    private fun isPromoted(item: Any?): Boolean =
        if (promotedUserClass.isInstance(item)) {
            promotedUserMetadataField.get(item) != null
        } else {
            promotedMetadataMethod.invoke(null, item) != null
        }
}

private fun Class<*>.instanceFields(): List<Field> =
    declaredFields.filter { !Modifier.isStatic(it.modifiers) }.onEach { it.isAccessible = true }
