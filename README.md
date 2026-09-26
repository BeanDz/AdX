# AdX

AdX removes promoted entries from the X timeline before X shows them. It requires a framework with libxposed API 102 and scope for `com.twitter.android`.

At each X start, [TimelineDiscovery](app/src/main/kotlin/TimelineDiscovery.kt) uses [DexKit](https://github.com/LuckyPray/DexKit) to find the timeline models and update method in the base APK. [TimelineBinding](app/src/main/kotlin/TimelineBinding.kt) checks the discovered members before the hook starts. The filter keeps entries it cannot classify. If filtering fails, X receives the original update.

The current rules and hook were checked on X `12.28.0-prod.01`. Discovery took about 0.3 seconds on the tested phone. AdX does not store the discovered names.

## Build

Install the Java version in [mise.toml](mise.toml). Set `ANDROID_HOME` to the Android SDK directory. Then run:

```sh
mise install
mise exec -- ./gradlew :app:assembleRelease :app:lintRelease
```

The Gradle release APK is unsigned. Sign it before you install it.

## Check the hook

Force-stop X and open it. Then read the AdX log:

```sh
adb logcat -s 'AdX:*'
```

Look for `Discovered and validated`, `Installed URT update hook`, `Observed timeline update`, and `Filtered promoted URT entries`. The removal count applies to one update. X can send the same entries more than once.
