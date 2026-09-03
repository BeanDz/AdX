package dev.doji.adx

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** Exact symbols validated against X 12.22.0-prod.01 (versionCode 312220001). */
internal object XTarget {
    const val PACKAGE = "com.twitter.android"

    object Timeline {
        const val STATE = "com.x.urt.s1"
        const val TYPE = "com.x.models.timelines.v"
        const val ITEM = "com.x.models.timelines.items.p0"
        const val REFRESH_STATE = "com.x.urt.r1"
        const val IMMUTABLE_LIST = "kotlinx.collections.immutable.b"
        const val IMMUTABLE_ADAPTER = "kotlinx.collections.immutable.adapters.a"
        const val MODULE = "com.x.models.timelines.items.c1"
        const val MODULE_ITEM = "com.x.models.timelines.items.f1"
        const val PROMOTED_METADATA = "com.x.models.ze"
    }
}

internal fun ClassLoader.resolve(name: String): Class<*> = Class.forName(name, false, this)

internal fun <T : AccessibleObject> T.accessible(): T = apply { isAccessible = true }

internal fun Class<*>.fieldOfType(fieldType: Class<*>): Field? {
    var current: Class<*>? = this
    while (current != null) {
        current.declaredFields
            .firstOrNull {
                !Modifier.isStatic(it.modifiers) && it.type === fieldType
            }?.let {
                return it.accessible()
            }
        current = current.superclass
    }
    return null
}
