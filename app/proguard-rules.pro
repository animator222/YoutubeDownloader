# youtubedl-android (uses Jackson + reflection)
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.**
-dontwarn org.w3c.dom.bootstrap.**
