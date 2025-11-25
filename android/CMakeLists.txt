cmake_minimum_required(VERSION 3.22)
project(DigiByteAndroid C CXX)

# This top-level CMake project orchestrates cross-compilation of the DigiByte Core
# daemon for Android. It expects the DigiByte Core sources to live in ../core and
# builds the daemon from the core/src subtree. GUI components and host-only
# tooling are explicitly disabled to keep the build Android-friendly.

include(ExternalProject)

if(NOT DEFINED ANDROID_ABI)
  set(ANDROID_ABI "arm64-v8a" CACHE STRING "Android ABI to target")
endif()
set_property(CACHE ANDROID_ABI PROPERTY STRINGS "arm64-v8a" "armeabi-v7a")

if(NOT DEFINED ANDROID_PLATFORM)
  set(ANDROID_PLATFORM 24 CACHE STRING "Android API level")
endif()

set(DIGIBYTE_CORE_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/../core")
set(DIGIBYTE_CORE_BUILD_DIR "${CMAKE_BINARY_DIR}/core-build-${ANDROID_ABI}")
set(DIGIBYTE_CORE_PREFIX "${CMAKE_BINARY_DIR}/android-prefix/${ANDROID_ABI}")

# DigiByte Core sources are expected in ../core, with the daemon living under
# core/src/. CMake drives the existing Autotools project rather than mirroring
# its source layout.
if(NOT EXISTS "${DIGIBYTE_CORE_SOURCE_DIR}/src")
  message(FATAL_ERROR "DigiByte Core sources not found at ${DIGIBYTE_CORE_SOURCE_DIR}")
endif()

set(DGB_HOST_TRIPLE "")
if(ANDROID_ABI STREQUAL "arm64-v8a")
  set(DGB_HOST_TRIPLE "aarch64-linux-android")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
  set(DGB_HOST_TRIPLE "arm-linux-androideabi")
else()
  message(FATAL_ERROR "Unsupported ANDROID_ABI: ${ANDROID_ABI}")
endif()

set(DGB_COMMON_CFLAGS "-fPIC -Os -ffunction-sections -fdata-sections")
set(DGB_COMMON_LDFLAGS "-Wl,--gc-sections")

# Optional: enable link-time optimisation. Some NDK/Clang revisions may need
# additional configuration, so this is off by default.
option(DGB_ENABLE_LTO "Enable LTO for DigiByte Core" OFF)
if(DGB_ENABLE_LTO)
  list(APPEND DGB_COMMON_CFLAGS "-flto")
  list(APPEND DGB_COMMON_LDFLAGS "-flto")
endif()

set(DGB_CONFIGURE_ENV
  "CC=${CMAKE_C_COMPILER}"
  "CXX=${CMAKE_CXX_COMPILER}"
  "AR=${CMAKE_AR}"
  "RANLIB=${CMAKE_RANLIB}"
  "LD=${CMAKE_LINKER}"
  "PKG_CONFIG_LIBDIR="
  "PKG_CONFIG_PATH="
  "CFLAGS=${CMAKE_C_FLAGS} ${DGB_COMMON_CFLAGS}"
  "CXXFLAGS=${CMAKE_CXX_FLAGS} ${DGB_COMMON_CFLAGS}"
  "LDFLAGS=${CMAKE_EXE_LINKER_FLAGS} ${DGB_COMMON_LDFLAGS}"
)

set(DGB_CONFIGURE_ARGS
  "--host=${DGB_HOST_TRIPLE}"
  "--prefix=${DIGIBYTE_CORE_PREFIX}"
  "--disable-bench"
  "--disable-man"
  "--disable-tests"
  "--disable-gui-tests"
  "--without-gui"
  "--with-miniupnpc=no"
  "--disable-zmq"
  "--enable-reduce-exports"
)

if(DEFINED CMAKE_SYSROOT AND NOT CMAKE_SYSROOT STREQUAL "")
  list(APPEND DGB_CONFIGURE_ARGS "--with-sysroot=${CMAKE_SYSROOT}")
endif()

cmake_host_system_information(RESULT DGB_JOB_COUNT QUERY NUMBER_OF_LOGICAL_CORES)
if(NOT DGB_JOB_COUNT)
  set(DGB_JOB_COUNT 4)
endif()

find_program(DGB_MAKE_PROGRAM NAMES make gmake REQUIRED)

ExternalProject_Add(digibyte_core
  SOURCE_DIR "${DIGIBYTE_CORE_SOURCE_DIR}"
  BINARY_DIR "${DIGIBYTE_CORE_BUILD_DIR}"
  CONFIGURE_COMMAND
    ${CMAKE_COMMAND} -E env ${DGB_CONFIGURE_ENV}
    ${DIGIBYTE_CORE_SOURCE_DIR}/autogen.sh
    COMMAND ${CMAKE_COMMAND} -E env ${DGB_CONFIGURE_ENV}
    ${DIGIBYTE_CORE_SOURCE_DIR}/configure ${DGB_CONFIGURE_ARGS}
  BUILD_COMMAND ${CMAKE_COMMAND} -E env ${DGB_CONFIGURE_ENV} ${DGB_MAKE_PROGRAM} -j${DGB_JOB_COUNT}
  INSTALL_COMMAND ${CMAKE_COMMAND} -E env ${DGB_CONFIGURE_ENV} ${DGB_MAKE_PROGRAM} install
  USES_TERMINAL_CONFIGURE 1
  USES_TERMINAL_BUILD 1
  USES_TERMINAL_INSTALL 1
)

add_custom_target(digibyted ALL
  COMMENT "Building DigiByte Core daemon for Android (${ANDROID_ABI})"
  DEPENDS digibyte_core
)

install(CODE "message(STATUS \"digibyted artifacts are installed under ${DIGIBYTE_CORE_PREFIX}\")")
