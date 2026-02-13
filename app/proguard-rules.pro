# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
# Keep InputMethodService for reflection
-keep class * extends android.inputmethodservice.InputMethodService { *; }
