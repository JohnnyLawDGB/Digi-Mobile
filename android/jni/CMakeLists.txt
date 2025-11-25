# JNI bridge for Digi-Mobile. Builds libdigimobile_jni.so so Android clients can
# call into the native layer. This target is intentionally small and experimental
# and does not embed DigiByte Core directly; it is meant to pair with the
# packaged daemon launched by app code.

add_library(digimobile_jni SHARED digimobile_jni.cpp)

find_library(ANDROID_LOG_LIB log)

# Link against Android system logging to surface diagnostics in logcat.
target_link_libraries(digimobile_jni PRIVATE ${ANDROID_LOG_LIB})

# Consumers can import the generated .so via Gradle's externalNativeBuild or by
# manually bundling it into their APK/AAB. JNI headers are provided by the NDK
# toolchain configured in the parent project.
