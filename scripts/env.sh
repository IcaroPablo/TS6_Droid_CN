# Environment for building TeleSpeak natively on Chimera Linux. scripts/setup and
# scripts/build source it; to run ./gradlew or cargo by hand, source it from the
# repository root: . scripts/env.sh
# Every location below can be overridden by setting it before sourcing.

ts_repo=${ts_repo:-$PWD}
[ -f "$ts_repo/scripts/env.sh" ] || { echo "env.sh: source it from the repository root" >&2; return 1; }

# Pinned versions; scripts/setup installs exactly these
RUST_VERSION=1.99.1
NDK_VERSION=27.2.12479018
SDK_PACKAGES="platforms;android-35 build-tools;35.0.0 platform-tools ndk;$NDK_VERSION"
# 20.0, the last build whose sdkmanager is Java; later ones call a glibc binary
CMDLINE_TOOLS_BUILD=14742923
CMDLINE_TOOLS_SHA256=04453066b540409d975c676d781da1477479dde3761310f1a7eb92a1dfb15af7

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/.local/share/android-sdk}
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/$NDK_VERSION
# CMake's Android support finds the NDK through this one
export ANDROID_NDK_ROOT=$ANDROID_NDK_HOME
export CARGO_HOME=${CARGO_HOME:-$HOME/.cargo}
export RUSTUP_HOME=${RUSTUP_HOME:-$HOME/.rustup}
export RUSTUP_TOOLCHAIN=$RUST_VERSION
TS_SIGNING_DIR=${TS_SIGNING_DIR:-$HOME/.local/share/telespeak/signing}
TS_OUT=${TS_OUT:-$ts_repo/out}
export PATH=$CARGO_HOME/bin:$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

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
# The NDK's clang packs relocations (RELR) only from API 30; Android reads
# them from API 28
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="-C link-arg=$LDFLAGS -C link-arg=-Wl,--pack-dyn-relocs=relr -C link-arg=-Wl,--use-android-relr-tags --remap-path-prefix=$CARGO_HOME=/cargo --remap-path-prefix=$ts_repo=/src"

# Gradle detects musl by looking for "-musl-" in mapped library paths; Chimera's
# loader is /usr/lib/libc.so, so it would load glibc natives and crash. Only
# JAVA_TOOL_OPTIONS reaches the daemon JVM.
export JAVA_TOOL_OPTIONS=-Dorg.gradle.native=false
