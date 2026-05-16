# ─── JNI — must keep exact native method signatures ───
-keep class com.keyboardmasterpiece.nativebridge.NativeGestureBridge {
    public static native java.lang.String nativeClassify(float[], int);
    public static boolean available;
    public static java.lang.String classify(float[], int);
}
-keepclasseswithmembernames class * { native <methods>; }

# ─── IME service — referenced by manifest ───
-keep class com.keyboardmasterpiece.ime.KeyboardImeService

# ─── Settings activity — referenced by manifest ───
-keep class com.keyboardmasterpiece.settings.SettingsActivity

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
