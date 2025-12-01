cmake_minimum_required(VERSION 3.22)
project(digimobile_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/jni-lib/${ANDROID_ABI})

add_library(digimobile_jni SHARED digimobile_jni.cpp)

find_library(ANDROID_LOG_LIB log REQUIRED)

target_compile_options(digimobile_jni PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-fPIC>
    $<$<COMPILE_LANGUAGE:C>:-fPIC>
)

target_link_libraries(digimobile_jni PRIVATE ${ANDROID_LOG_LIB})
