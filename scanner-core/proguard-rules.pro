# Proguard rules for the Scanner SDK library

# Keep everything in the core package that is public
-keep public class com.enoc.sdk.scanner.core.* {
    public protected *;
}

-keep public interface com.enoc.sdk.scanner.core.* {
    public protected *;
}

# Explicitly keep ScannerViewKt and its Composables
-keep class com.enoc.sdk.scanner.core.ScannerViewKt {
    public static *** ScannerView(...);
}

# Explicitly keep Companion objects and their members (Constants like SCANNER_PLAY_BEEP)
-keep class com.enoc.sdk.scanner.core.*$Companion {
    public *;
}

# Keep all data models
-keep class com.enoc.sdk.scanner.core.model.** {
    public protected *;
}

# Keep enum methods
-keepclassmembers enum com.enoc.sdk.scanner.core.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Activity Result related classes for Permission handling
-keep class androidx.activity.result.** { *; }
-keep class androidx.activity.compose.** { *; }

# Essential attributes for Kotlin and library compatibility
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Support for the @Keep annotation
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
