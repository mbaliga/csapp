# Add project specific ProGuard rules here.
# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mbaliga.csapp.**$$serializer { *; }
-keepclassmembers class com.mbaliga.csapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.mbaliga.csapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
