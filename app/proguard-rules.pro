# Compose, AndroidX and Kotlin ship their own consumer rules; nothing app-specific is needed yet.
# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
