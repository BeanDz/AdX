package dev.doji.adx

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Exact symbols validated against X 12.19.1-release.0 (versionCode 312191000). */
internal object XTarget {
    const val PACKAGE = "com.twitter.android"

    object Timeline {
        const val STATE = "com.x.urt.q0\$d"
        const val TYPE = "com.x.models.timelines.TimelineType"
        const val ITEM = "com.x.models.timelines.items.UrtTimelineItem"
        const val REFRESH_STATE = "com.x.urt.q0\$d\$a"
        const val IMMUTABLE_LIST = "kotlinx.collections.immutable.c"
        const val IMMUTABLE_ADAPTER = "kotlinx.collections.immutable.adapters.a"
        const val MODULE = "com.x.models.timelines.items.UrtTimelineModule"
        const val MODULE_ITEM = "com.x.models.timelines.items.UrtTimelineModuleItem"
        const val RTB_IMAGE_AD = "com.x.models.timelines.items.UrtTimelineRtbImageAd"
    }

    object Renderer {
        const val OWNER = "com.x.urt.items.post.w7"
        const val METHOD = "e"
        const val STATE = "com.x.urt.items.post.n6"
        const val PROMOTED_METADATA = "com.x.models.TimelinePromotedMetadata"
        const val MODIFIER = "androidx.compose.ui.Modifier\$a"
        const val LAYOUT_STATE = "com.x.urt.items.post.o6"
        const val CONTENT_INSETS = "androidx.compose.foundation.layout.b4"
        const val COMPOSER = "androidx.compose.runtime.Composer"
    }
}

internal fun ClassLoader.resolve(name: String): Class<*> = Class.forName(name, false, this)

internal fun <T : AccessibleObject> T.accessible(): T = apply { isAccessible = true }

internal fun Class<*>.noArgMethod(name: String): Method? = try {
    getMethod(name).accessible()
} catch (_: ReflectiveOperationException) {
    null
}

internal fun Class<*>.fieldOfType(fieldType: Class<*>): Field? {
    var current: Class<*>? = this
    while (current != null) {
        current.declaredFields.firstOrNull { it.type === fieldType }?.let {
            return it.accessible()
        }
        current = current.superclass
    }
    return null
}
