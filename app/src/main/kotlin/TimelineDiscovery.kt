package dev.doji.adx

import java.lang.reflect.Modifier
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.result.ClassData

/** Semantic queries checked against X 12.28.0-prod.01. */
internal object TimelineDiscovery {
    fun discover(apkPath: String): TimelineSymbols {
        System.loadLibrary("dexkit")
        return DexKitBridge.create(apkPath).use { bridge ->
            val module = bridge.modelClass("UrtTimelineModule(innerContent=")
            val wrapper = bridge.modelClass("UrtTimelineModuleItem(item=")
            val user = bridge.modelClass("UrtTimelineUser(user=")
            val metadata = bridge.modelClass("TimelinePromotedMetadata(impressionId=")
            val payload = bridge.modelClass("Tuple4[")
            val item =
                module.interfaces.filter { candidate ->
                    user.interfaces.any { it.name == candidate.name } &&
                        wrapper.fields.any {
                            !Modifier.isStatic(it.modifiers) && it.typeName == candidate.name
                        }
                }.unique("Timeline item interface")
            val accessor =
                bridge.findMethod {
                    matcher {
                        modifiers(Modifier.STATIC)
                        paramTypes(item.name)
                        returnType(metadata.name)
                    }
                }.unique("Promoted metadata accessor")
            val collector =
                bridge.findMethod {
                    matcher {
                        name("emit")
                        paramTypes("java.lang.Object", "kotlin.coroutines.Continuation")
                        returnType("java.lang.Object")
                        usingStrings(
                            listOf("received_", "promoted", "organic"),
                            StringMatchType.Equals,
                        )
                    }
                }.unique("Timeline update collector")
            val tupleFields = payload.fields.filter { !Modifier.isStatic(it.modifiers) }
            val usedFields = collector.usingFields.map { it.field.descriptor }.toSet()
            check(tupleFields.size == 4 && tupleFields.all { it.descriptor in usedFields }) {
                "Timeline collector does not read the complete four-field payload"
            }

            TimelineSymbols(
                collectorClass = collector.className,
                moduleClass = module.name,
                moduleItemClass = wrapper.name,
                promotedUserClass = user.name,
                promotedMetadataClass = metadata.name,
                promotedAccessorClass = accessor.className,
                promotedAccessorMethod = accessor.methodName,
                payloadClass = payload.name,
            )
        }
    }

    private fun DexKitBridge.modelClass(marker: String): ClassData {
        val method = findMethod {
            matcher {
                name("toString")
                paramTypes()
                returnType("java.lang.String")
                usingStrings(listOf(marker), StringMatchType.Equals)
            }
        }.unique(marker)
        return checkNotNull(method.declaredClass) { "$marker: missing declaring class" }
    }

    private fun <T> List<T>.unique(label: String): T {
        check(size == 1) { "$label: expected one match, found $size" }
        return first()
    }
}

internal data class TimelineSymbols(
    val collectorClass: String,
    val moduleClass: String,
    val moduleItemClass: String,
    val promotedUserClass: String,
    val promotedMetadataClass: String,
    val promotedAccessorClass: String,
    val promotedAccessorMethod: String,
    val payloadClass: String,
)
