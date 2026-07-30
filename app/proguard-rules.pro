# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\mike\AppData\Local\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
-dontwarn android.support.**

-keep class !android.support.v7.internal.view.menu.**,android.support.** {*;}
-ignorewarnings
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

-keep public class * extends android.support.v4.** {*;}
-keep public class * extends android.app.Fragment

# Glance AppWidget
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
# DetermineBasalBoost has a very large method that triggers R8 optimizer bugs
-dontoptimize class app.aaps.plugins.aps.openAPSBoost.DetermineBasalBoost

# Boost plugin — large methods trigger R8 VerifyError
-keep class app.aaps.plugins.aps.openAPSBoost.** { *; }
-dontoptimize class app.aaps.plugins.aps.openAPSBoost.**

# R8-specific: disable optimization for Boost (different from ProGuard -dontoptimize)
-optimizations !code/simplification/*,!field/*,!class/merging/*
-keepclassmembers,allowshrinking,allowobfuscation class app.aaps.plugins.aps.openAPSBoost.DetermineBasalBoost {
    app.aaps.core.interfaces.aps.RT determine_basal(...);
}
