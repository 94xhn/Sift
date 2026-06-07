# kotlinx.serialization 生成的序列化器需保留
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class app.sift.data.**$$serializer { *; }
-keepclassmembers class app.sift.data.** {
    *** Companion;
}
-keepclasseswithmembers class app.sift.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
