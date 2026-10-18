# youtubedl-android unpacks python/ffmpeg with commons-compress and parses yt-dlp output with
# Jackson; both look classes up reflectively, so they must not be renamed or stripped.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**
