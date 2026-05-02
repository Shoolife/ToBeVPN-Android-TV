# XRay / V2Ray native library
-keep class go.Seq { *; }
-keep class libv2ray.** { *; }

# Retrofit / Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.tobevpn.tv.data.remote.dto.** { *; }
-keep class com.tobevpn.tv.data.remote.ExchangeRateResponse { *; }

# Room + SQLCipher
-keep class * extends androidx.room.RoomDatabase
-keep class net.zetetic.database.** { *; }

# Strip android.util.Log calls in release. Without this, Log.d/i/w/e calls
# remain in the compiled bytecode and write to logcat on every device. R8
# treats these as side-effect-free given the rule, so the call sites are
# pruned along with their argument expressions.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
