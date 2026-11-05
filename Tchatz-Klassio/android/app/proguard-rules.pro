# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.tchatz.klassio.** { *** Companion; }
-keepclasseswithmembers class com.tchatz.klassio.** { kotlinx.serialization.KSerializer serializer(...); }
