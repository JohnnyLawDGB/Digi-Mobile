# CMake configuration for the Digi-Mobile JNI bridge. This library is built as a
# shared object (libdigimobile_jni.so) that can be loaded by an Android app
# module. The top-level android/CMakeLists.txt adds this subdirectory.

add_library(digimobile_jni SHARED digimobile_jni.cpp)

find_library(ANDROID_LOG_LIB log)

# Link against Android system logging to surface diagnostics in logcat.
target_link_libraries(digimobile_jni PRIVATE ${ANDROID_LOG_LIB})

# Consumers can import the generated .so via Gradle's externalNativeBuild or by
# manually bundling it into their APK/AA Bundle. JNI headers are provided by the
# NDK toolchain configured in the parent project.
