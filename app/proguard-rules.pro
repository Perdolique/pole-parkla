-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class ai.onnxruntime.** { *; }

# Proj4j registers projection classes directly, then creates them reflectively.
# R8 cannot otherwise see that their public no-argument constructors are used.
-keepclassmembers class org.locationtech.proj4j.proj.** {
    public <init>();
}
