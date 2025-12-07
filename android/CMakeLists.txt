cmake_minimum_required(VERSION 3.22)
project(DigiByteAndroid C CXX)

# Experimental, pruned DigiByte Core build for Android. This CMake project wraps
# the upstream Autotools build so Android developers can cross-compile the
# daemon easily. It expects DigiByte Core sources under ../core and intentionally
# disables GUI/tests/manual pages to keep the mobile footprint small. Consensus
# logic is unchanged.

include(ExternalProject)

# ABIs can be overridden via -DANDROID_ABI=... or ARCH/ABI env vars passed to the
# helper scripts. Defaults favor 64-bit devices but 32-bit is supported for
# legacy hardware.
if(NOT DEFINED ANDROID_ABI)
  set(ANDROID_ABI "arm64-v8a" CACHE STRING "Android ABI to target")
endif()
set_property(CACHE ANDROID_ABI PROPERTY STRINGS "arm64-v8a" "armeabi-v7a")
# TODO: Add support for additional ABIs (x86/x86_64) once tested.

# Allow API level overrides for newer/older devices.
if(NOT DEFINED ANDROID_PLATFORM)
  set(ANDROID_PLATFORM 24 CACHE STRING "Android API level")
endif()

# Core source location is a cache variable for easier overrides when testing
# alternate trees. By default it points to ../core relative to this file.
set(DIGIBYTE_CORE_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/../core" CACHE PATH "Path to DigiByte Core sources")
set(DIGIBYTE_CORE_BUILD_DIR "${CMAKE_BINARY_DIR}/core-build-${ANDROID_ABI}")
set(DIGIBYTE_CORE_PREFIX "${CMAKE_BINARY_DIR}/android-prefix/${ANDROID_ABI}")
set(DGB_DEPENDS_DIR "${DIGIBYTE_CORE_SOURCE_DIR}/depends")

set(DGB_HOST_TRIPLE "")
if(ANDROID_ABI STREQUAL "arm64-v8a")
  set(DGB_HOST_TRIPLE "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
  set(DGB_HOST_TRIPLE "arm-linux-androideabi")
else()
  message(FATAL_ERROR "Unsupported ANDROID_ABI: ${ANDROID_ABI}")
endif()

set(DGB_DEPENDS_CONFIG_SITE "${DGB_DEPENDS_DIR}/${DGB_HOST_TRIPLE}/share/config.site")

# When CMAKE_SYSROOT is not provided, attempt to infer it from the Android NDK
# pointed to by ANDROID_NDK or ANDROID_NDK_HOME. Autotools' configure will then
# receive an explicit --with-sysroot flag, preventing accidental host sysroot
# usage that can result in x86_64 outputs.
if((NOT DEFINED CMAKE_SYSROOT OR CMAKE_SYSROOT STREQUAL "") AND DEFINED ENV{ANDROID_NDK_HOME})
  set(_ndk_sysroot "$ENV{ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot")
  if(EXISTS "${_ndk_sysroot}")
    set(CMAKE_SYSROOT "${_ndk_sysroot}" CACHE PATH "Android sysroot" FORCE)
  endif()
endif()

# Detect the build machine triple for explicit --build flag when cross-compiling.
# Prefer config.guess from the upstream tree; fall back to CMake's host values.
set(DGB_BUILD_TRIPLE "")
if(EXISTS "${DIGIBYTE_CORE_SOURCE_DIR}/build-aux/config.guess")
  execute_process(
    COMMAND ${DIGIBYTE_CORE_SOURCE_DIR}/build-aux/config.guess
    WORKING_DIRECTORY ${DIGIBYTE_CORE_SOURCE_DIR}
    OUTPUT_VARIABLE DGB_BUILD_TRIPLE
    OUTPUT_STRIP_TRAILING_WHITESPACE
    RESULT_VARIABLE DGB_CONFIG_GUESS_RESULT
  )
  if(NOT DGB_CONFIG_GUESS_RESULT EQUAL 0)
    set(DGB_BUILD_TRIPLE "")
  endif()
endif()

if(DGB_BUILD_TRIPLE STREQUAL "")
  string(TOLOWER "${CMAKE_HOST_SYSTEM_NAME}" DGB_HOST_SYSNAME_LOWER)
  set(DGB_BUILD_TRIPLE "${CMAKE_HOST_SYSTEM_PROCESSOR}-pc-${DGB_HOST_SYSNAME_LOWER}")
endif()

# Common flags aim for small binaries; GUI and bench/test targets are disabled to
# shorten Android build times.
set(DGB_COMMON_CFLAGS "-fPIC -Os -ffunction-sections -fdata-sections")
set(DGB_COMMON_LDFLAGS "-Wl,--gc-sections")

option(DGB_ENABLE_LTO "Enable LTO for DigiByte Core" OFF)
if(DGB_ENABLE_LTO)
  list(APPEND DGB_COMMON_CFLAGS "-flto")
  list(APPEND DGB_COMMON_LDFLAGS "-flto")
endif()

option(DGB_ENABLE_ZMQ "Enable ZMQ interface" OFF)
set(DGB_ZMQ_ARG "--disable-zmq")
if(DGB_ENABLE_ZMQ)
  set(DGB_ZMQ_ARG "--enable-zmq")
endif()

set(DGB_EXTRA_CONFIGURE_ARGS "" CACHE STRING "Additional configure flags passed to DigiByte Core")

# CRITICAL: Prioritize environment variables (set by build-android.sh) over CMake detected compilers
# to ensure we always use the NDK cross-compiler (aarch64-clang), not the host compiler (gcc/clang).
set(DGB_CC "$ENV{CC}")
if(DGB_CC STREQUAL "" AND NOT CMAKE_C_COMPILER STREQUAL "")
  set(DGB_CC "${CMAKE_C_COMPILER}")
elseif(DGB_CC STREQUAL "")
  message(FATAL_ERROR "CC environment variable not set and CMAKE_C_COMPILER is empty. This indicates the NDK toolchain was not properly loaded or build-android.sh did not set CC. Check your NDK path and CMake configuration.")
endif()

set(DGB_CXX "$ENV{CXX}")
if(DGB_CXX STREQUAL "" AND NOT CMAKE_CXX_COMPILER STREQUAL "")
  set(DGB_CXX "${CMAKE_CXX_COMPILER}")
elseif(DGB_CXX STREQUAL "")
  message(FATAL_ERROR "CXX environment variable not set and CMAKE_CXX_COMPILER is empty. Ensure build-android.sh passes CXX env var correctly.")
endif()

set(DGB_AR "$ENV{AR}")
if(DGB_AR STREQUAL "")
  if(NOT CMAKE_AR STREQUAL "")
    set(DGB_AR "${CMAKE_AR}")
  else()
    message(FATAL_ERROR "AR environment variable not set and CMAKE_AR is empty.")
  endif()
endif()

set(DGB_RANLIB "$ENV{RANLIB}")
if(DGB_RANLIB STREQUAL "")
  if(NOT CMAKE_RANLIB STREQUAL "")
    set(DGB_RANLIB "${CMAKE_RANLIB}")
  else()
    message(FATAL_ERROR "RANLIB environment variable not set and CMAKE_RANLIB is empty.")
  endif()
endif()

set(DGB_LD "$ENV{LD}")
if(DGB_LD STREQUAL "")
  if(NOT CMAKE_LINKER STREQUAL "")
    set(DGB_LD "${CMAKE_LINKER}")
  else()
    message(FATAL_ERROR "LD environment variable not set and CMAKE_LINKER is empty.")
  endif()
endif()

# Prefer toolchain bin directory (derived from CC) when spawning configure so that
# auxiliary tools such as strip come from the Android NDK, not the host toolchain.
get_filename_component(DGB_TOOLCHAIN_BIN "${DGB_CC}" DIRECTORY)
if(NOT EXISTS "${DGB_TOOLCHAIN_BIN}")
  message(FATAL_ERROR "Toolchain bin directory not found for CC: ${DGB_CC}")
endif()

# Warn if compiler doesn't look like a cross-compiler
if(NOT DGB_CC MATCHES "aarch64|armv7a" AND NOT DGB_CC MATCHES "clang")
  message(WARNING "WARNING: DGB_CC does not appear to be an ARM Android cross-compiler: ${DGB_CC}")
endif()

# Log the actual compilers being used for debugging
message(STATUS "[Digi-Mobile] DGB_CC = ${DGB_CC}")
message(STATUS "[Digi-Mobile] DGB_CXX = ${DGB_CXX}")
message(STATUS "[Digi-Mobile] DGB_HOST_TRIPLE = ${DGB_HOST_TRIPLE}")
message(STATUS "[Digi-Mobile] DGB_CORE_CFLAGS = ${DGB_CORE_CFLAGS}")

string(REGEX REPLACE "(^| )-D_FORTIFY_SOURCE=2($| )" " " DGB_CORE_CFLAGS "${CMAKE_C_FLAGS} ${DGB_COMMON_CFLAGS}")
string(REGEX REPLACE "(^| )-D_FORTIFY_SOURCE=2($| )" " " DGB_CORE_CXXFLAGS "${CMAKE_CXX_FLAGS} ${DGB_COMMON_CFLAGS}")
string(STRIP "${DGB_CORE_CFLAGS}" DGB_CORE_CFLAGS)
string(STRIP "${DGB_CORE_CXXFLAGS}" DGB_CORE_CXXFLAGS)

set(DGB_TOOLCHAIN_ENV
  "CC=${DGB_CC}"
  "CXX=${DGB_CXX}"
  # Force libtool to link via the C++ driver (which already targets aarch64
  # via the NDK triple) instead of falling back to the host ld.
  "CXXLD=${DGB_CXX}"
  "AR=${DGB_AR}"
  "RANLIB=${DGB_RANLIB}"
  "LD=${DGB_LD}"
  "STRIP=${DGB_TOOLCHAIN_BIN}/llvm-strip"
  "PATH=${DGB_TOOLCHAIN_BIN}:$ENV{PATH}"
  # When building for Android we rely on the depends/ tree to provide
  # cross-compiled libraries (libevent, etc.). Point pkg-config at the
  # depends package dirs so `configure` can locate them instead of searching
  # the host system.
  "PKG_CONFIG_LIBDIR=${DGB_DEPENDS_DIR}/${DGB_HOST_TRIPLE}/lib/pkgconfig:${DGB_DEPENDS_DIR}/${DGB_HOST_TRIPLE}/share/pkgconfig"
  "PKG_CONFIG_PATH=${DGB_DEPENDS_DIR}/${DGB_HOST_TRIPLE}/lib/pkgconfig:${DGB_DEPENDS_DIR}/${DGB_HOST_TRIPLE}/share/pkgconfig"
  "CFLAGS=${DGB_CORE_CFLAGS}"
  "CXXFLAGS=${DGB_CORE_CXXFLAGS}"
  "LDFLAGS=${CMAKE_EXE_LINKER_FLAGS} ${DGB_COMMON_LDFLAGS}"
)

if(EXISTS "${DGB_DEPENDS_DIR}")
  list(APPEND DGB_TOOLCHAIN_ENV "CONFIG_SITE=${DGB_DEPENDS_CONFIG_SITE}")
endif()

set(DGB_CONFIGURE_ARGS
  "--host=${DGB_HOST_TRIPLE}"
  "--build=${DGB_BUILD_TRIPLE}"
  "--prefix=${DIGIBYTE_CORE_PREFIX}"
  "--disable-bench"
  "--disable-man"
  "--disable-tests"
  "--disable-fuzz"
  "--disable-gui-tests"
  "--without-gui"
  "--with-miniupnpc=no"
  "--with-fuzz-binary=no"
  "${DGB_ZMQ_ARG}"
  "--enable-reduce-exports"
  ${DGB_EXTRA_CONFIGURE_ARGS}
)

message(STATUS "[Digi-Mobile] DigiByte Core configure args: ${DGB_CONFIGURE_ARGS}")

if(DEFINED CMAKE_SYSROOT AND NOT CMAKE_SYSROOT STREQUAL "")
  list(APPEND DGB_CONFIGURE_ARGS "--with-sysroot=${CMAKE_SYSROOT}")
endif()

cmake_host_system_information(RESULT DGB_JOB_COUNT QUERY NUMBER_OF_LOGICAL_CORES)
if(NOT DGB_JOB_COUNT)
  set(DGB_JOB_COUNT 4)
endif()

find_program(DGB_MAKE_PROGRAM NAMES make gmake REQUIRED)

# Only configure DigiByte Core if the sources are present. This keeps the JNI
# build usable even when the upstream tree is not yet synced.
if(EXISTS "${DIGIBYTE_CORE_SOURCE_DIR}/src")
  message(STATUS "[Digi-Mobile] Experimental Android build. Not production-ready.")

  if(NOT EXISTS "${DGB_DEPENDS_DIR}/Makefile")
    message(FATAL_ERROR "DigiByte Core depends tree not found at ${DGB_DEPENDS_DIR}; run scripts/setup-core.sh to fetch upstream sources.")
  endif()

  ExternalProject_Add(digibyte_depends
    SOURCE_DIR "${DGB_DEPENDS_DIR}"
    CONFIGURE_COMMAND ""
    BUILD_COMMAND ${CMAKE_COMMAND} -E env ${DGB_TOOLCHAIN_ENV} ${DGB_MAKE_PROGRAM} -C ${DGB_DEPENDS_DIR} HOST=${DGB_HOST_TRIPLE} NO_QT=1 -j${DGB_JOB_COUNT}
    INSTALL_COMMAND ""
    BUILD_ALWAYS 1
    BUILD_IN_SOURCE 1
    USES_TERMINAL_BUILD 1
  )

  ExternalProject_Add(digibyte_core
    SOURCE_DIR "${DIGIBYTE_CORE_SOURCE_DIR}"
    BINARY_DIR "${DIGIBYTE_CORE_BUILD_DIR}"
    CONFIGURE_COMMAND
      ${CMAKE_COMMAND} -E env
      ${DGB_TOOLCHAIN_ENV}
      ${DIGIBYTE_CORE_SOURCE_DIR}/autogen.sh
      COMMAND ${CMAKE_COMMAND} -E env
      ${DGB_TOOLCHAIN_ENV}
      ${DIGIBYTE_CORE_SOURCE_DIR}/configure ${DGB_CONFIGURE_ARGS}
    BUILD_COMMAND ${CMAKE_COMMAND} -E env
      ${DGB_TOOLCHAIN_ENV}
      ${DGB_MAKE_PROGRAM} -j${DGB_JOB_COUNT}
    INSTALL_COMMAND ${CMAKE_COMMAND} -E env
      ${DGB_TOOLCHAIN_ENV}
      ${DGB_MAKE_PROGRAM} install
    DEPENDS digibyte_depends
    USES_TERMINAL_CONFIGURE 1
    USES_TERMINAL_BUILD 1
    USES_TERMINAL_INSTALL 1
  )

  add_custom_target(digibyted ALL
    COMMENT "Building DigiByte Core daemon for Android (${ANDROID_ABI})"
    DEPENDS digibyte_core
  )

  install(CODE "message(STATUS \"digibyted artifacts are installed under ${DIGIBYTE_CORE_PREFIX}\")")
else()
  message(WARNING "DigiByte Core sources not found at ${DIGIBYTE_CORE_SOURCE_DIR}; skipping core build")
endif()

# ---------------------------------------------------------------------------
# JNI bridge for Android clients
#
# The JNI layer is built as libdigimobile_jni.so and can be consumed from an
# Android app module via Gradle's externalNativeBuild or by bundling the
# resulting shared library directly. This does not yet package an APK; it only
# prepares the native layer and Java wrapper sources under android/java/.
add_subdirectory(jni)
