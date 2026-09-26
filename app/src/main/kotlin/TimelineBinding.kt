package dev.doji.adx

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Validates discovered types before exposing reflection operations to the filter. */
internal class TimelineBinding(
    loader: ClassLoader,
    symbols: TimelineSymbols,
) {
    private val moduleClass = loader.loadClass(symbols.moduleClass)
    private val moduleItemClass = loader.loadClass(symbols.moduleItemClass)
    private val promotedUserClass = loader.loadClass(symbols.promotedUserClass)
    private val metadataClass = loader.loadClass(symbols.promotedMetadataClass)
    private val payloadClass = loader.loadClass(symbols.payloadClass)

    private val moduleItemField =
        moduleItemClass.timelineInstanceFields().single { field ->
            field.type.isInterface &&
                field.type.isAssignableFrom(moduleClass) &&
                field.type.isAssignableFrom(promotedUserClass)
        }
    private val itemClass = moduleItemField.type
    private val moduleFields = moduleClass.timelineInstanceFields()
    private val moduleContentField = moduleFields.single { it.type == List::class.java }
    private val moduleConstructor =
        moduleClass.declaredConstructors.single { constructor ->
            val types = constructor.parameterTypes
            !constructor.isSynthetic &&
                types.count { it == List::class.java } == 1 &&
                types.distinct().size == types.size &&
                types.all { type -> moduleFields.count { it.type == type } == 1 }
        }.timelineAccessible()
    private val moduleConstructorFields =
        moduleConstructor.parameterTypes.map { type -> moduleFields.single { it.type == type } }

    private val promotedUserMetadataField =
        promotedUserClass.timelineInstanceFields().single { it.type == metadataClass }
    private val promotedMetadataMethod =
        loader.loadClass(symbols.promotedAccessorClass).declaredMethods.single { method ->
            method.name == symbols.promotedAccessorMethod &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == metadataClass &&
                method.parameterTypes.contentEquals(arrayOf(itemClass))
        }.timelineAccessible()

    val updateMethod: Method =
        loader.loadClass(symbols.collectorClass).declaredMethods.single { method ->
            method.name == "emit" &&
                !Modifier.isStatic(method.modifiers) &&
                method.returnType == Any::class.java &&
                method.parameterTypes.contentEquals(
                    arrayOf(
                        Any::class.java,
                        loader.loadClass("kotlin.coroutines.Continuation"),
                    ),
                )
        }.timelineAccessible()

    private val payloadConstructor =
        payloadClass.getDeclaredConstructor(
            Any::class.java,
            Any::class.java,
            Any::class.java,
            Any::class.java,
        ).timelineAccessible()

    // Constructor sentinels establish tuple order without relying on field names or reflection order.
    private val payloadFields: List<Field> =
        run {
            val fields = payloadClass.timelineInstanceFields()
            require(fields.size == 4 && fields.all { it.type == Any::class.java }) {
                "Timeline payload must have exactly four object fields"
            }
            val sentinels = Array(4) { Any() }
            val probe = payloadConstructor.newInstance(*sentinels)
            sentinels.map { sentinel -> fields.single { it.get(probe) === sentinel } }
        }

    fun timelineItems(payload: Any): List<*>? {
        if (!payloadClass.isInstance(payload)) return null
        val items = payloadFields.first().get(payload)
        check(items is List<*>) { "Timeline payload no longer contains an item list" }
        return items
    }

    fun copyPayload(
        payload: Any,
        items: List<Any?>,
    ): Any =
        payloadConstructor.newInstance(
            items,
            *payloadFields.drop(1).map { it.get(payload) }.toTypedArray(),
        )

    fun isTimelineItem(value: Any): Boolean = itemClass.isInstance(value)

    fun isTimelineModule(value: Any): Boolean = moduleClass.isInstance(value)

    fun moduleContent(module: Any): List<*>? = moduleContentField.get(module) as? List<*>

    fun unwrapModuleItem(wrapper: Any?): Any? {
        if (wrapper == null || !moduleItemClass.isInstance(wrapper)) return null
        return moduleItemField.get(wrapper)
    }

    fun copyModule(
        module: Any,
        items: ArrayList<Any?>,
    ): Any =
        moduleConstructor.newInstance(
            *moduleConstructorFields.map { field ->
                if (field == moduleContentField) items else field.get(module)
            }.toTypedArray(),
        )

    fun isPromoted(item: Any): Boolean =
        if (promotedUserClass.isInstance(item)) {
            promotedUserMetadataField.get(item) != null
        } else {
            promotedMetadataMethod.invoke(null, item) != null
        }
}

private fun Class<*>.timelineInstanceFields(): List<Field> =
    declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map { it.timelineAccessible() }

private fun <T : AccessibleObject> T.timelineAccessible(): T = apply { isAccessible = true }
