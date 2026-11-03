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

# Room entities / DAOs are reflected on by the generated code
-keep class com.mbaliga.csapp.data.db.entities.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# JSON DTOs decoded by kotlinx.serialization
-keep class com.mbaliga.csapp.data.play.** { *; }
-keep class com.mbaliga.csapp.data.github.** { *; }
-keep class com.mbaliga.csapp.data.export.** { *; }

# Versioned AI prompts are read as Java resources by name
-keepclassmembers class com.mbaliga.csapp.domain.ai.ResourcePrompts { *; }
-dontwarn org.slf4j.**
