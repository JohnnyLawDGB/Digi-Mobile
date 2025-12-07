cmake_minimum_required(VERSION 3.22)
project(DigiByteAndroid C CXX)

# Experimental, pruned DigiByte Core build for Android. This CMake project wraps
# the upstream Autotools build so Android developers can cross-compile the
# daemon easily. It expects DigiByte Core sources under ../core and intentionally
# disables GUI/tests/manual pages to keep the mobile footprint small. Consensus
# logic is unchanged.

include(ExternalProject)

# Force a single supported ABI. Any attempt to override this should fail fast so
# the Android pipeline never emits x86/other artifacts.
if(NOT DEFINED ANDROID_ABI)
  set(ANDROID_ABI "arm64-v8a" CACHE STRING "Android ABI to target")
endif()

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
  message(FATAL_ERROR "Android pipeline only supports ANDROID_ABI=arm64-v8a; received ${ANDROID_ABI}")
endif()

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

set(DGB_HOST_TRIPLE "aarch64-linux-android")
if(NOT DGB_HOST_TRIPLE STREQUAL "aarch64-linux-android")
  message(FATAL_ERROR "Android pipeline only supports host aarch64-linux-android")
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
set(DGB_COMMON_LDFLAGS "-static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--fatal-warnings -Wl,--gc-sections -Wl,--no-undefined -Qunused-arguments")

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

# Prefer explicit Android NDK-provided target drivers to avoid mixing host
# toolchains during the Autotools/libtool link stage.
set(DGB_ANDROID_NDK_ROOT "")
if(DEFINED ENV{ANDROID_NDK_HOME} AND NOT "$ENV{ANDROID_NDK_HOME}" STREQUAL "")
  set(DGB_ANDROID_NDK_ROOT "$ENV{ANDROID_NDK_HOME}")
elseif(DEFINED ANDROID_NDK AND NOT "${ANDROID_NDK}" STREQUAL "")
  set(DGB_ANDROID_NDK_ROOT "${ANDROID_NDK}")
elseif(DEFINED CMAKE_ANDROID_NDK AND NOT "${CMAKE_ANDROID_NDK}" STREQUAL "")
  set(DGB_ANDROID_NDK_ROOT "${CMAKE_ANDROID_NDK}")
endif()

if(DGB_ANDROID_NDK_ROOT STREQUAL "")
  message(FATAL_ERROR "ANDROID_NDK_HOME/ANDROID_NDK not provided. Android pipeline requires the NDK to cross-compile arm64.")
endif()

set(DGB_ANDROID_API_LEVEL "${ANDROID_PLATFORM}")
string(REGEX REPLACE "^android-" "" DGB_ANDROID_API_LEVEL "${DGB_ANDROID_API_LEVEL}")

set(DGB_DEFAULT_TOOLCHAIN_BIN "${DGB_ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin")
if(NOT EXISTS "${DGB_DEFAULT_TOOLCHAIN_BIN}")
  message(FATAL_ERROR "Android NDK toolchain bin directory missing at ${DGB_DEFAULT_TOOLCHAIN_BIN}")
endif()

# CRITICAL: Prefer explicit Android NDK target drivers (aarch64-linux-androidXX-clang)
# to avoid libtool falling back to a host x86_64 linker when LDFLAGS omits --target.
set(DGB_CC "${DGB_DEFAULT_TOOLCHAIN_BIN}/${DGB_HOST_TRIPLE}${DGB_ANDROID_API_LEVEL}-clang")
set(DGB_CXX "${DGB_DEFAULT_TOOLCHAIN_BIN}/${DGB_HOST_TRIPLE}${DGB_ANDROID_API_LEVEL}-clang++")
set(DGB_AR "${DGB_DEFAULT_TOOLCHAIN_BIN}/llvm-ar")
set(DGB_RANLIB "${DGB_DEFAULT_TOOLCHAIN_BIN}/llvm-ranlib")

foreach(_required_tool IN LISTS DGB_CC DGB_CXX DGB_AR DGB_RANLIB)
  if(NOT EXISTS "${_required_tool}")
    message(FATAL_ERROR "Required Android NDK tool not found: ${_required_tool}")
  endif()
endforeach()

if(NOT DGB_CC MATCHES "${DGB_HOST_TRIPLE}")
  message(FATAL_ERROR "DGB_CC must target ${DGB_HOST_TRIPLE}; detected ${DGB_CC}")
endif()

if(NOT DGB_CXX MATCHES "${DGB_HOST_TRIPLE}")
  message(FATAL_ERROR "DGB_CXX must target ${DGB_HOST_TRIPLE}; detected ${DGB_CXX}")
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

string(REGEX REPLACE "(^| )-D_FORTIFY_SOURCE=2($| )" " " DGB_CORE_CFLAGS "${CMAKE_C_FLAGS} ${DGB_COMMON_CFLAGS}")
string(REGEX REPLACE "(^| )-D_FORTIFY_SOURCE=2($| )" " " DGB_CORE_CXXFLAGS "${CMAKE_CXX_FLAGS} ${DGB_COMMON_CFLAGS}")
string(REGEX REPLACE "(^| )--target=[^ ]+" " " DGB_CORE_CFLAGS "${DGB_CORE_CFLAGS}")
string(REGEX REPLACE "(^| )--target=[^ ]+" " " DGB_CORE_CXXFLAGS "${DGB_CORE_CXXFLAGS}")
string(REGEX REPLACE "(^| )--sysroot=[^ ]+" " " DGB_CORE_CFLAGS "${DGB_CORE_CFLAGS}")
string(REGEX REPLACE "(^| )--sysroot=[^ ]+" " " DGB_CORE_CXXFLAGS "${DGB_CORE_CXXFLAGS}")
string(STRIP "${DGB_CORE_CFLAGS}" DGB_CORE_CFLAGS)
string(STRIP "${DGB_CORE_CXXFLAGS}" DGB_CORE_CXXFLAGS)

# Log the actual compilers being used for debugging
message(STATUS "[Digi-Mobile] DGB_CC = ${DGB_CC}")
message(STATUS "[Digi-Mobile] DGB_CXX = ${DGB_CXX}")
message(STATUS "[Digi-Mobile] DGB_HOST_TRIPLE = ${DGB_HOST_TRIPLE}")
message(STATUS "[Digi-Mobile] DGB_CORE_CFLAGS = ${DGB_CORE_CFLAGS}")

  set(DGB_TOOLCHAIN_ENV
    "CC=${DGB_CC}"
    "CXX=${DGB_CXX}"
    # Force libtool to link via the C++ driver (which already targets aarch64
    # via the NDK triple) instead of falling back to the host ld.
    "CXXLD=${DGB_CXX}"
    "AR=${DGB_AR}"
    "RANLIB=${DGB_RANLIB}"
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

string(REPLACE ";" " " DGB_CONFIGURE_ARGS_STRING "${DGB_CONFIGURE_ARGS}")

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
      /bin/sh -c [=[if [ "${DGB_HOST_TRIPLE}" != "aarch64-linux-android" ]; then
        echo 'Android pipeline only supports aarch64-linux-android host for digibyte_core.';
        exit 1;
      fi;
      exec ${DIGIBYTE_CORE_SOURCE_DIR}/configure ${DGB_CONFIGURE_ARGS_STRING}]=]
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
