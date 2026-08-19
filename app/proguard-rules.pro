-dontwarn io.github.libxposed.annotation.**

# Vector resolves this exact name from META-INF/xposed/java_init.list.
-keep public class dev.doji.adx.AdXModule extends io.github.libxposed.api.XposedModule {
    public <init>();
}
