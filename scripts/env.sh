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
export CARGO_HOME=${CARGO_HOME:-$HOME/.cargo}
export RUSTUP_HOME=${RUSTUP_HOME:-$HOME/.rustup}
export RUSTUP_TOOLCHAIN=$RUST_VERSION
TS_SIGNING_DIR=${TS_SIGNING_DIR:-$HOME/.local/share/telespeak/signing}
TS_OUT=${TS_OUT:-$ts_repo/out}
export PATH=$ts_repo/scripts/chimera:$CARGO_HOME/bin:$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

# Cross-compile for Android with Chimera's clang instead of the NDK's glibc one
# (by name, through PATH: OpenSSL bakes its compiler command into the library)
ts_cc=aarch64-linux-android29-clang
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER=$ts_cc
export CC_aarch64_linux_android=$ts_cc
export CXX_aarch64_linux_android=$ts_cc++
export AR_aarch64_linux_android=llvm-ar
export RANLIB_aarch64_linux_android=llvm-ranlib
export CMAKE_TOOLCHAIN_FILE_aarch64_linux_android=$ts_repo/scripts/chimera/android-aarch64.cmake
export CMAKE_POLICY_VERSION_MINIMUM=3.5
# Keep build paths, and with them the builder's home and user name, out of the
# library: Rust's and the C wrappers' prefix maps rewrite the repository and
# ~/.cargo, and the cargo target directory, which OpenSSL records as its install
# directory, is at a fixed path outside $HOME. OpenSSL's build time is replaced
# by the native source's commit time.
export TS_REPO=$ts_repo
export CARGO_TARGET_DIR=${CARGO_TARGET_DIR:-/var/tmp/telespeak-target}
export SOURCE_DATE_EPOCH=${SOURCE_DATE_EPOCH:-$(git -C "$ts_repo/tslib_multi" log -1 --format=%ct 2>/dev/null || echo 0)}
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="--remap-path-prefix=$CARGO_HOME=/cargo --remap-path-prefix=$ts_repo=/src"
# Chimera's make is BSD make; OpenSSL's and CMake's makefiles need GNU make
export MAKE=gmake

# The wrappers combine the host clang's headers with the NDK's compiler-rt in one
# resource directory. It is recreated on every source, so a clang upgrade on the
# host is picked up.
export TS_CLANG_RES=${XDG_CACHE_HOME:-$HOME/.cache}/telespeak/clang-res
mkdir -p "$TS_CLANG_RES"
ln -sfn "$(clang -print-resource-dir)/include" "$TS_CLANG_RES/include"
ln -sfn "$(echo "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/*/lib)" "$TS_CLANG_RES/lib"

# Gradle detects musl by looking for "-musl-" in mapped library paths; Chimera's
# loader is /usr/lib/libc.so, so it would load glibc natives and crash. Only
# JAVA_TOOL_OPTIONS reaches the daemon JVM.
export JAVA_TOOL_OPTIONS=-Dorg.gradle.native=false
