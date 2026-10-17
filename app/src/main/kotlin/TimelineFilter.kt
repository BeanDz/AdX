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
    private val moduleFields = moduleClass.instanceFields()
    private val moduleContentField = moduleFields.single { it.type == List::class.java }
    private val moduleConstructor =
        moduleClass.declaredConstructors
            .single { constructor ->
                constructor.parameterTypes.all { type -> moduleFields.any { it.type == type } }
            }.apply { isAccessible = true }
    private val moduleConstructorFields =
        moduleConstructor.parameterTypes.map { type -> moduleFields.single { it.type == type } }

    init {
        promotedMetadataMethod.isAccessible = true
    }

    fun filter(source: List<*>): List<*> =
        source.mapNotNull { item ->
            if (isPromoted(item)) return@mapNotNull null
            if (!moduleClass.isInstance(item)) return@mapNotNull item

            val content = moduleContentField.get(item) as List<*>
            val filtered = content.filterNot { isPromoted(moduleItemField.get(it)) }
            if (filtered.size == content.size) return@mapNotNull item
            if (filtered.isEmpty()) return@mapNotNull null

            moduleConstructor.newInstance(
                *moduleConstructorFields
                    .map { field ->
                        if (field == moduleContentField) filtered else field.get(item)
                    }.toTypedArray(),
            )
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
