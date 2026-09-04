package dev.doji.adx

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Method

/** Reflection binding validated against X 12.22.0-prod.01 (versionCode 312220001). */
internal class X1222Binding(loader: ClassLoader) {
    private val itemClass = loader.resolve(ITEM)
    private val moduleClass = loader.resolve(MODULE)
    private val moduleItemClass = loader.resolve(MODULE_ITEM)
    private val promotedUserClass = loader.resolve(PROMOTED_USER)
    private val payloadClass = loader.resolve(UPDATE_PAYLOAD)

    val updateMethod: Method =
        loader
            .resolve(UPDATE_COLLECTOR)
            .getDeclaredMethod(
                "emit",
                Any::class.java,
                loader.resolve(CONTINUATION),
            ).accessible()

    private val payloadConstructor =
        payloadClass
            .getDeclaredConstructor(
                Any::class.java,
                Any::class.java,
                Any::class.java,
                Any::class.java,
            ).accessible()
    private val payloadFields =
        PAYLOAD_FIELDS.map { payloadClass.getDeclaredField(it).accessible() }
    private val payloadItemsField = payloadFields.first()
    private val payloadContextFields = payloadFields.drop(1)

    private val moduleContentField =
        moduleClass.getDeclaredField(MODULE_CONTENT_FIELD).accessible()
    private val moduleCopyMethod =
        moduleClass
            .getDeclaredMethod(MODULE_COPY_METHOD, moduleClass, ArrayList::class.java)
            .accessible()
    private val moduleItemField =
        moduleItemClass.getDeclaredField(MODULE_ITEM_FIELD).accessible()
    private val promotedMetadataMethod =
        loader
            .resolve(PROMOTED_METADATA_ACCESSOR)
            .getDeclaredMethod(PROMOTED_METADATA_METHOD, itemClass)
            .accessible()
    private val promotedUserMetadataField =
        promotedUserClass.getDeclaredField(PROMOTED_USER_METADATA_FIELD).accessible()

    fun timelineItems(payload: Any): List<*>? {
        if (!payloadClass.isInstance(payload)) return null
        return payloadItemsField.get(payload) as? List<*>
    }

    fun copyPayload(
        payload: Any,
        items: List<Any?>,
    ): Any =
        payloadConstructor.newInstance(
            items,
            *payloadContextFields.map { field -> field.get(payload) }.toTypedArray(),
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
        moduleCopyMethod.invoke(null, module, items)
            ?: throw IllegalStateException("$MODULE_COPY_METHOD returned null")

    fun isPromoted(item: Any): Boolean =
        if (promotedUserClass.isInstance(item)) {
            promotedUserMetadataField.get(item) != null
        } else {
            promotedMetadataMethod.invoke(null, item) != null
        }

    companion object {
        const val PACKAGE = "com.twitter.android"

        private const val ITEM = "com.x.models.timelines.items.p0"
        private const val MODULE = "com.x.models.timelines.items.c1"
        private const val MODULE_ITEM = "com.x.models.timelines.items.f1"
        private const val PROMOTED_METADATA_ACCESSOR =
            "com.x.jetfuel.v2.element.attribute.n"
        private const val PROMOTED_METADATA_METHOD = "y"
        private const val PROMOTED_USER = "com.x.models.timelines.items.d2"
        private const val PROMOTED_USER_METADATA_FIELD = "h"
        private const val UPDATE_COLLECTOR = "com.x.urt.i"
        private const val UPDATE_PAYLOAD = "com.zhuinden.tupleskt.b"
        private const val CONTINUATION = "kotlin.coroutines.Continuation"
        private const val MODULE_CONTENT_FIELD = "a"
        private const val MODULE_COPY_METHOD = "a"
        private const val MODULE_ITEM_FIELD = "a"
        private val PAYLOAD_FIELDS = listOf("a", "b", "c", "d")
    }
}

private fun ClassLoader.resolve(name: String): Class<*> = Class.forName(name, false, this)

private fun <T : AccessibleObject> T.accessible(): T = apply { isAccessible = true }
