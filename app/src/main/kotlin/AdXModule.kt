package dev.doji.adx

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field

private const val TARGET_PACKAGE = "com.twitter.android"

class AdXModule : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        if (param.processName != TARGET_PACKAGE) detach()
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE) return

        System.loadLibrary("dexkit")
        DexKitBridge.create(param.applicationInfo.sourceDir).use { bridge ->
            val loader = param.classLoader

            fun modelClass(marker: String): Class<*> =
                bridge
                    .findClass { matcher { usingEqStrings(marker) } }
                    .single()
                    .getInstance(loader)

            val moduleClass = modelClass("UrtTimelineModule(innerContent=")
            val itemType = moduleClass.interfaces.single()
            val moduleContentField = moduleClass.fieldOfType(List::class.java)
            val moduleItemField = modelClass("UrtTimelineModuleItem(item=").fieldOfType(itemType)
            val userClass = modelClass("UrtTimelineUser(user=")
            val metadataClass = modelClass("TimelinePromotedMetadata(impressionId=")
            val userMetadataField = userClass.fieldOfType(metadataClass)
            val metadataMethod =
                bridge
                    .findMethod {
                        matcher {
                            paramTypes(itemType)
                            returnType(metadataClass)
                        }
                    }.single()
                    .getMethodInstance(loader)
            // URT applies this to the merged timeline list before rendering.
            val listMethod =
                bridge
                    .findMethod {
                        matcher {
                            returnType(List::class.java)
                            usingEqStrings(
                                "x_sports_post_context_enabled",
                                "x_sports_post_context_min_spacing",
                                "x_sports_post_context_same_game_min_spacing",
                            )
                        }
                    }.single()
                    .getMethodInstance(loader)

            fun isPromoted(item: Any?): Boolean =
                if (userClass.isInstance(item)) {
                    userMetadataField.get(item) != null
                } else {
                    metadataMethod.invoke(null, item) != null
                }

            // Drop the whole module instead of rebuilding it when one of its entries is promoted.
            fun isPromotedItem(item: Any?): Boolean {
                if (isPromoted(item)) return true
                if (!moduleClass.isInstance(item)) return false
                val content = moduleContentField.get(item) as List<*>
                return content.any { isPromoted(moduleItemField.get(it)) }
            }

            hook(listMethod).intercept { chain ->
                (chain.proceed() as List<*>).filterNot(::isPromotedItem)
            }
            log(Log.INFO, "AdX", "Installed URT list hook: $listMethod")
        }
    }
}

private fun Class<*>.fieldOfType(type: Class<*>): Field = declaredFields.single { it.type == type }
