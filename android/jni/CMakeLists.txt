cmake_minimum_required(VERSION 3.10)
project(digi_mobile_jni LANGUAGES CXX)

# Placeholder JNI target; extend with actual bindings when integrating with Android app layer.
add_library(digi_mobile_jni STATIC
    # Add JNI source files here
)

target_include_directories(digi_mobile_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

set_target_properties(digi_mobile_jni PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)
