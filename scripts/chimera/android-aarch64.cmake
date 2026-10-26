# Plain cross build through the clang wrappers. Declaring Linux instead of
# Android keeps CMake's Android module from looking for the NDK's own
# (glibc-only) toolchain binaries. Needs ANDROID_NDK_HOME from scripts/env.sh.
get_filename_component(here "${CMAKE_CURRENT_LIST_DIR}" ABSOLUTE)

set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER "${here}/aarch64-linux-android29-clang")
set(CMAKE_CXX_COMPILER "${here}/aarch64-linux-android29-clang++")
set(CMAKE_ASM_COMPILER "${here}/aarch64-linux-android29-clang")
set(CMAKE_AR llvm-ar CACHE FILEPATH "")
set(CMAKE_RANLIB llvm-ranlib CACHE FILEPATH "")
set(CMAKE_MAKE_PROGRAM gmake CACHE FILEPATH "")

set(CMAKE_FIND_ROOT_PATH "$ENV{ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/sysroot")
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
