# ─── JNI — must keep exact native method signatures ───
-keep class com.keyboardmasterpiece.nativebridge.NativeGestureBridge {
    public static native java.lang.String nativeClassify(float[], int);
    public static java.lang.String classify(float[], int);
}
# FIX: FINAL-010 — Keep native methods via class member rule, not incorrect field rule
-keepclasswithmembers class com.keyboardmasterpiece.nativebridge.NativeGestureBridge {
    native <methods>;
}
-keepclasseswithmembernames class * { native <methods>; }

# ─── IME service — referenced by manifest ───
-keep class com.keyboardmasterpiece.ime.KeyboardImeService

# ─── Settings activity — referenced by manifest ───
-keep class com.keyboardmasterpiece.settings.SettingsActivity

# ─── Boot receiver — referenced by manifest ───
-keep class com.keyboardmasterpiece.ime.BootReceiver

# ─── Enums used in SharedPreferences (valueOf) ───
-keepclassmembers enum com.keyboardmasterpiece.engine.Panel { *; }
-keepclassmembers enum com.keyboardmasterpiece.engine.LayoutMode { *; }

# ─── Data classes used across components ───
-keepclassmembers class com.keyboardmasterpiece.engine.KeyboardKey {
    public <init>(...);
    public final java.lang.String getLabel();
    public final java.lang.String getOutput();
    public final int getCode();
    public final float getWeight();
    public final java.lang.String getAlt();
}

# ─── Kotlin metadata ───
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keep class * extends android.inputmethodservice.InputMethodService
