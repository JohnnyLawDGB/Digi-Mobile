# Minimal wrapper around the official Android NDK toolchain for DigiByte Core.
# Usage:
#   cmake -S android -B android/build \
#     -DCMAKE_TOOLCHAIN_FILE=android/toolchain-android.cmake \
#     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=24

set(ANDROID_NDK "" CACHE PATH "Path to the Android NDK root")
if(NOT ANDROID_NDK AND DEFINED ENV{ANDROID_NDK_HOME})
  set(ANDROID_NDK "$ENV{ANDROID_NDK_HOME}")
elseif(NOT ANDROID_NDK AND DEFINED ENV{ANDROID_NDK_ROOT})
  set(ANDROID_NDK "$ENV{ANDROID_NDK_ROOT}")
endif()

if(NOT ANDROID_NDK)
  message(FATAL_ERROR "ANDROID_NDK_HOME/ANDROID_NDK_ROOT is not set; pass -DANDROID_NDK=/path/to/ndk")
endif()

if(NOT EXISTS "${ANDROID_NDK}/build/cmake/android.toolchain.cmake")
  message(FATAL_ERROR "Could not locate android.toolchain.cmake under ${ANDROID_NDK}")
endif()

# Must be set BEFORE including the NDK toolchain
if(NOT DEFINED ANDROID_ABI OR ANDROID_ABI STREQUAL "")
  set(ANDROID_ABI "arm64-v8a" CACHE STRING "Android ABI")
endif()

if(NOT DEFINED ANDROID_PLATFORM OR ANDROID_PLATFORM STREQUAL "")
  set(ANDROID_PLATFORM 24 CACHE STRING "Android API level")
endif()

# Set system-level variables BEFORE including NDK toolchain
set(CMAKE_SYSTEM_NAME Android)
set(CMAKE_ANDROID_NDK "${ANDROID_NDK}")
set(CMAKE_ANDROID_ARCH_ABI "${ANDROID_ABI}")
set(CMAKE_SYSTEM_VERSION "${ANDROID_PLATFORM}")
set(CMAKE_ANDROID_STL_TYPE "c++_static" CACHE STRING "Android STL" FORCE)

# CRITICAL: Include the NDK's official CMake toolchain FIRST
# This sets CMAKE_C_COMPILER, CMAKE_CXX_COMPILER, and other cross-compile settings
include("${ANDROID_NDK}/build/cmake/android.toolchain.cmake")

# Verify that the NDK toolchain was properly loaded by checking if a compiler was set
if(NOT CMAKE_C_COMPILER)
  message(FATAL_ERROR "CMAKE_C_COMPILER not set after including NDK toolchain. NDK toolchain may not have been loaded correctly.")
endif()

message(STATUS "[Digi-Mobile] Android NDK Toolchain Loaded")
message(STATUS "[Digi-Mobile] CMAKE_C_COMPILER: ${CMAKE_C_COMPILER}")
message(STATUS "[Digi-Mobile] CMAKE_CXX_COMPILER: ${CMAKE_CXX_COMPILER}")
message(STATUS "[Digi-Mobile] Android ABI: ${ANDROID_ABI}")
message(STATUS "[Digi-Mobile] Android Platform: ${ANDROID_PLATFORM}")
message(STATUS "[Digi-Mobile] CMAKE_SYSTEM_NAME: ${CMAKE_SYSTEM_NAME}")

# Default to position-independent code; additional Android-friendly flags are
# applied in the top-level android/CMakeLists.txt.
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
