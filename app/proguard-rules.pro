# Keep kotlinx.serialization classes (they're accessed via reflection/generated code)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.matejgroombridge.turntable.**$$serializer { *; }
-keepclassmembers class dev.matejgroombridge.turntable.** {
    *** Companion;
}
-keepclasseswithmembers class dev.matejgroombridge.turntable.** {
    kotlinx.serialization.KSerializer serializer(...);
}
