# Keep all tslib JNI classes (native methods + classes created from native code)
-keep class dev.tslib.** { *; }

# Release builds drop verbose, debug and info logging: those lines name servers,
# nicknames and uids, and the system log is readable over USB debugging.
# Warnings and errors stay for crash reports.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
