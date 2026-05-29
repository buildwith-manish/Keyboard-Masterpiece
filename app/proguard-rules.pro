# --- JNI - must keep exact native method signatures ---
-keep class com.keyboardmasterpiece.nativebridge.NativeGestureBridge {
    public static native java.lang.String nativeClassify(float[], int);
    public static java.lang.String classify(float[], int);
}
# Keep native methods via class member rule
-keepclasseswithmembers class com.keyboardmasterpiece.nativebridge.NativeGestureBridge {
    native <methods>;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- IME service - referenced by manifest ---
-keep class com.keyboardmasterpiece.ime.KeyboardImeService

# --- Settings activity - referenced by manifest ---
-keep class com.keyboardmasterpiece.settings.SettingsActivity

# --- Boot receiver - referenced by manifest ---
-keep class com.keyboardmasterpiece.ime.BootReceiver

# --- File picker activity - referenced by manifest ---
-keep class com.keyboardmasterpiece.ime.FilePickerActivity

# --- Enums used in SharedPreferences (valueOf) ---
-keepclassmembers enum com.keyboardmasterpiece.engine.Panel { *; }
-keepclassmembers enum com.keyboardmasterpiece.engine.LayoutMode { *; }
-keepclassmembers enum com.keyboardmasterpiece.engine.EmojiCategory { *; }

# --- Data classes used across components ---
-keepclassmembers class com.keyboardmasterpiece.engine.KeyboardKey {
    public <init>(...);
    public final java.lang.String getLabel();
    public final java.lang.String getOutput();
    public final int getCode();
    public final float getWeight();
    public final java.lang.String getAlt();
}

# --- Kotlin metadata ---
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keep class * extends android.inputmethodservice.InputMethodService

# --- Security Crypto / Tink (used by EncryptedSharedPreferences) ---
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
