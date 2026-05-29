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

# AUDIT-R8a: WebView JS bridge. @JavascriptInterface methods are invoked reflectively by the
# WebView from android_asset/map.html; R8 cannot see those call sites and will strip or rename
# them the moment minifyEnabled is turned on (see audit BLD-1), silently breaking map
# long-press (setPosition) and zoom (setZoom). No-op while minify is off.
-keepclassmembers class cl.coders.faketraveler.WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}
