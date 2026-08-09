# Keep rules for common module components used by other modules
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
-keep class com.equalizer.common.MyMediaService$Companion { *; }
-keep class com.equalizer.common.Equalizer { *; }
-keep class com.equalizer.common.Equalizer$Companion { *; }
-keep class * extends androidx.media3.session.MediaSession$Callback { *; }
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-keep class * extends androidx.media3.session.MediaLibraryService { *; }
-keep class * extends android.service.media.MediaBrowserService { *; }
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
-keep class com.google.common.collect.ImmutableList { *; }
