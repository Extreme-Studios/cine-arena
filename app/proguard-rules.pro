# Keep model/repository package names readable in stacktraces while still allowing obfuscation.
-keepattributes SourceFile,LineNumberTable

# Keep Compose tool-generated metadata needed at runtime.
-keep class kotlin.Metadata { *; }

# Keep app package entry points.
-keep class com.extremestudios.cinearena.MainActivity { *; }
-keep class com.extremestudios.cinearena.playback.PlaybackNotificationService { *; }

# Keep enum values and names when used in UI state.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
