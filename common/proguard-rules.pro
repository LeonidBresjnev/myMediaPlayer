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

-keep class com.equalizer.common.metadata.M4aMeta { *; }
-keep class com.equalizer.common.metadata.MetaFactory  { *; }
-keep class com.equalizer.common.metadata.Mp3Meta { *; }
-keep class com.equalizer.common.metadata.MusicMetaInterface  { *; }
-keep class com.equalizer.common.metadata.WavMeta  { *; }
-keep class com.equalizer.common.OnlineMetadataManager   { *; }
-keep class com.equalizer.common.OnlineInfo  { *; }
-keep class com.equalizer.common.MediaThumbnailProvider { *; }
-keep class com.equalizer.common.MediaThumbnailProvider$Companion { *; }
-keepclassmembers class com.equalizer.common.MediaThumbnailProvider {
    public static *** AUTHORITY;
    public static *** CONTENT_URI;
    public static void init(android.content.Context);
}

# Protect Media3 components and custom service
-keep class com.equalizer.common.MyMediaService { *; }
-keep class com.equalizer.common.Equalizer { *; }
-keep class * extends androidx.media3.session.MediaSession$Callback { *; }
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-keep class * extends androidx.media3.session.MediaLibraryService { *; }
-keep class * extends android.service.media.MediaBrowserService { *; }
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
-keep class com.google.common.collect.ImmutableList { *; }
