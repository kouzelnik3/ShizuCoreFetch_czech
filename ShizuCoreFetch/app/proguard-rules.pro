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

# ==========================================
# قواعد الحماية الخاصة بتطبيقك (تمت إضافتها هنا)
# ==========================================

# 1. حماية كلاسات البيانات الخاصة بك (مهم جداً لمنع الانهيار بعد تسجيل الدخول)
-keep class xyz.siwane.shizucorefetch.AppModel { *; }
-keep class xyz.siwane.shizucorefetch.LocalApkModel { *; }

# 2. حماية مكتبة جلب البيانات (Retrofit & OkHttp)
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, Exceptions, *Annotation*
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# 3. حماية مكتبة فك تشفير البيانات (Gson)
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# 4. حماية مكتبة التحميل والصور (Coil & Okio)
-dontwarn okio.**
-keep class coil.** { *; }

# 5. حماية محرك التطبيق الأساسي (Shizuku)
-keep class rikka.shizuku.** { *; }
