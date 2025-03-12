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

# Basic optimization flags
-optimizationpasses 15
-dontusemixedcaseclassnames
-verbose
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable

# Advanced compression options
-allowaccessmodification
-repackageclasses
-flattenpackagehierarchy
-overloadaggressively
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remove unused code
-dontwarn **
-ignorewarnings

# Aggressive shortening
-obfuscationdictionary compact-dictionary.txt
-classobfuscationdictionary compact-dictionary.txt
-packageobfuscationdictionary compact-dictionary.txt

