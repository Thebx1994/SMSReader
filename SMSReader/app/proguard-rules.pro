# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep the database classes
-keep class com.example.smsreader.data.** { *; }
-keep class org.postgresql.** { *; }

# Keep Retrofit and GSON classes
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep WorkManager classes
-keep class androidx.work.** { *; }

# General Android rules
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}