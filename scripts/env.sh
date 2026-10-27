# Environment for building TeleSpeak natively on Chimera Linux. scripts/setup and
# scripts/build source it; to run ./gradlew or cargo by hand, source it from the
# repository root: . scripts/env.sh
# Every location below can be overridden by setting it before sourcing.

ts_repo=${ts_repo:-$PWD}
[ -f "$ts_repo/scripts/env.sh" ] || { echo "env.sh: source it from the repository root" >&2; return 1; }

# Pinned versions; scripts/setup installs exactly these
RUST_VERSION=1.99.1
NDK_VERSION=29.0.14206865
# The Android Gradle plugin's default
BUILD_TOOLS_VERSION=34.0.0
# The Android SDK packages Gradle and cargo need, straight from Google's
# repository (no sdkmanager): directory under $ANDROID_HOME, archive, sha256.
# The Android Gradle plugin insists on platform-tools.
SDK_PACKAGES="\
platforms/android-35 platform-35_r02.zip 0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0
build-tools/$BUILD_TOOLS_VERSION build-tools_r34-linux.zip e858c4b60069d0431051b225d384413b1643e1289b00a4825aed347f25bd510f
platform-tools platform-tools_r37.0.1-linux.zip d230f13842f60f782a8645f9c813f8f845bf36089ea7289f28c48f17979313f1
ndk/$NDK_VERSION android-ndk-r29-linux.zip 4abbbcdc842f3d4879206e9695d52709603e52dd68d3c1fff04b3b5e7a308ecf"

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/.local/share/android-sdk}
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/$NDK_VERSION
# CMake's Android support finds the NDK through this one
export ANDROID_NDK_ROOT=$ANDROID_NDK_HOME
export CARGO_HOME=${CARGO_HOME:-$HOME/.cargo}
export RUSTUP_HOME=${RUSTUP_HOME:-$HOME/.rustup}
export RUSTUP_TOOLCHAIN=$RUST_VERSION
TS_SIGNING_DIR=${TS_SIGNING_DIR:-$HOME/.local/share/telespeak/signing}
# SHA-256 of the release signing certificate (public; apksigner verify --print-certs)
TS_SIGNER_SHA256=4396de8d21ed855f387b2d1fcf87d627417c4b2224ddb16212a0e17866cace9d
TS_OUT=${TS_OUT:-$ts_repo/out}
export PATH=$CARGO_HOME/bin:$JAVA_HOME/bin:$PATH

# The NDK's clang runs through gcompat, but its ld.lld crashes there, so it
# links with Chimera's
ts_cc=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$ts_cc
export CC_aarch64_linux_android=$ts_cc
export CXX_aarch64_linux_android=$ts_cc++
export AR_aarch64_linux_android=llvm-ar
export RANLIB_aarch64_linux_android=llvm-ranlib
# CMake reads LDFLAGS when it configures opus
export LDFLAGS=--ld-path=/usr/bin/ld.lld
export CMAKE_POLICY_VERSION_MINIMUM=3.5
# Keep build paths, and with them the builder's home and user name, out of the
# library: prefix maps rewrite the repository and ~/.cargo
ts_map="-ffile-prefix-map=$CARGO_HOME=/cargo -ffile-prefix-map=$ts_repo=/src"
export CFLAGS_aarch64_linux_android="$ts_map" CXXFLAGS_aarch64_linux_android="$ts_map"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="-C link-arg=$LDFLAGS --remap-path-prefix=$CARGO_HOME=/cargo --remap-path-prefix=$ts_repo=/src"

# Gradle detects musl by looking for "-musl-" in mapped library paths; Chimera's
# loader is /usr/lib/libc.so, so it would load glibc natives and crash. Only
# JAVA_TOOL_OPTIONS reaches the daemon JVM.
export JAVA_TOOL_OPTIONS=-Dorg.gradle.native=false
