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
-keep,includedescriptorclasses class com.asoc.ping.**$$serializer { *; }
-keepclassmembers class com.asoc.ping.** {
    *** Companion;
}
-keepclasseswithmembers class com.asoc.ping.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase

# Room entities / DAOs are reflected on by the generated code
-keep class com.asoc.ping.data.db.entities.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# JSON DTOs decoded by kotlinx.serialization
-keep class com.asoc.ping.data.play.** { *; }
-keep class com.asoc.ping.data.github.** { *; }
-keep class com.asoc.ping.data.export.** { *; }

# Versioned AI prompts are read as Java resources by name
-keepclassmembers class com.asoc.ping.domain.ai.ResourcePrompts { *; }
-dontwarn org.slf4j.**
