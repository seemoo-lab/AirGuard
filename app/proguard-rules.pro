# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Retrofit does reflection on generic parameters. InnerClasses is required to use Signature and
# EnclosingMethod is required to use InnerClasses.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep annotation default values (e.g., retrofit2.http.Field.encoded).
-keepattributes AnnotationDefault

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keepclassmembers class * {
      @com.google.gson.annotations.SerializedName <fields>;
      @de.seemoo.at_tracking_detection.database.relations.DeviceBeaconNotification <fields>;
}

-keepclassmembers class de.seemoo.at_tracking_detection.database.** { <fields>; }


# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# With R8 full mode, it sees no subtypes of Retrofit interfaces since they are created with a Proxy
# and replaces all potential values with null. Explicitly keeping the interfaces prevents this.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Keep generic signature of Call (R8 full mode strips signatures from non-kept items).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call

# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# R8 full mode strips generic signatures from return types if not kept.
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Keep Gson classes
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.Gson { *; }
-keep class com.google.gson.TypeAdapter { *; }
-keep class com.google.gson.stream.JsonReader { *; }
-keep class com.google.gson.stream.JsonWriter { *; }

# Ensure that the classes related to Article are not stripped
-keep class de.seemoo.at_tracking_detection.ui.dashboard.Article { *; }

# Keep ProGuard/R8 from stripping out important methods or classes
-keep class * implements com.google.gson.reflect.TypeToken { *; }

# Ensure that the DeviceType class is not stripped or obfuscated
-keep class de.seemoo.at_tracking_detection.database.models.device.DeviceType { *; }

# Keep all data classes
-keep class de.seemoo.at_tracking_detection.database.models.** { *; }

# Keep all fields in data classes
-keepclassmembers class de.seemoo.at_tracking_detection.database.models.** {
    <fields>;
}

# Keep all methods in data classes
-keepclassmembers class de.seemoo.at_tracking_detection.database.models.** {
    <methods>;
}

# Keep the SendStatisticsWorker class and its methods
-keep class de.seemoo.at_tracking_detection.statistics.SendStatisticsWorker { *; }

# Keep the Api class and its methods
-keep class de.seemoo.at_tracking_detection.statistics.api.Api { *; }

# Keep the SharedPrefs class and its methods
-keep class de.seemoo.at_tracking_detection.util.SharedPrefs { *; }
