package dev.doji.adx

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import java.lang.reflect.Modifier

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
            val module = bridge.modelClass("UrtTimelineModule(innerContent=")
            val wrapper = bridge.modelClass("UrtTimelineModuleItem(item=")
            val user = bridge.modelClass("UrtTimelineUser(user=")
            val metadata = bridge.modelClass("TimelinePromotedMetadata(impressionId=")
            val accessor =
                bridge
                    .findMethod {
                        matcher {
                            modifiers(Modifier.STATIC)
                            paramTypes(module.interfaces.single().name)
                            returnType(metadata.name)
                        }
                    }.single()
            // URT applies this to the merged timeline list before rendering.
            val listMethod =
                bridge
                    .findMethod {
                        matcher {
                            returnType("java.util.List")
                            usingEqStrings(
                                "x_sports_post_context_enabled",
                                "x_sports_post_context_min_spacing",
                                "x_sports_post_context_same_game_min_spacing",
                            )
                        }
                    }.single()
                    .getMethodInstance(loader)
            val filter =
                TimelineFilter(
                    moduleClass = module.getInstance(loader),
                    moduleItemClass = wrapper.getInstance(loader),
                    promotedUserClass = user.getInstance(loader),
                    promotedMetadataMethod = accessor.getMethodInstance(loader),
                )

            hook(listMethod).intercept { chain -> filter.filter(chain.proceed() as List<*>) }
            log(Log.INFO, "AdX", "Installed URT list hook: $listMethod")
        }
    }
}

private fun DexKitBridge.modelClass(marker: String): ClassData =
    findClass {
        matcher { usingEqStrings(marker) }
    }.single()
