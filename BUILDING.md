# Building TeleSpeak

The release APKs are built natively on [Chimera Linux](https://chimera-linux.org) (x86_64, musl)
with the scripts in [`scripts/`](scripts). Those scripts and the pinned versions below are what
produced the published builds.

## What gets built

- `tslib_multi/` (git submodule): the Rust core, built as `libtslib_jni.so` for arm64-v8a. Its
  `Cargo.lock` pins every dependency, and the build runs with `--locked`. The TeamSpeak protocol
  comes from [tsclientlib](https://github.com/IcaroPablo/tsclientlib/tree/telespeak), our fork
  without the server-nickname lookup; the only C code left is opus, built with CMake.
- `app/`: the Kotlin app, built by Gradle into the release APK. The library built above is
  committed at `app/src/main/jniLibs/arm64-v8a/`, so a Gradle-only build packages it as is.

## Versions

| Tool | Version | Installed by |
|---|---|---|
| Rust | 1.99.1, target `aarch64-linux-android` | `scripts/setup`, in `~/.rustup` and `~/.cargo` |
| Android NDK | 27.2.12479018 | `scripts/setup`, in `~/.local/share/android-sdk` |
| Android SDK | platform 35, build-tools 35.0.0 | `scripts/setup`, same place |
| Android command-line tools | 20.0 (build 14742923) | `scripts/setup`; later builds need glibc |
| JDK | 21 | host package |
| clang, lld | Chimera's (22), for the host side and for linking | host package |
| Gradle | 8.14, from the wrapper | `./gradlew`, in `~/.gradle` |

The versions live in [`scripts/env.sh`](scripts/env.sh).

## Steps

1. Host packages:

   ```
   apk add git curl unzip gmake cmake clang lld llvm-binutils openjdk21 gcompat libgcc-chimera-devel
   ```

   `gcompat` runs the NDK's clang and the SDK's `aapt2`, which are glibc programs, and
   `libgcc-chimera-devel` provides the `libgcc_s` that they and the musl Rust toolchain need.

2. Clone with the submodule and run the one-time setup:

   ```
   git clone --recursive https://github.com/IcaroPablo/TS6_Droid_CN.git
   cd TS6_Droid_CN
   scripts/setup
   ```

   It changes no shell profile; the scripts set `PATH` themselves.

3. Build:

   ```
   scripts/build
   ```

   This builds the library, runs the unit tests and writes `out/telespeak-<version>.apk`.

To run `./gradlew` or `cargo` by hand, first source the environment from the repository root:
`. scripts/env.sh`.

## Signing

`scripts/build` reads the release keystore from `~/.local/share/telespeak/signing/`
(`telespeak-release.keystore` and `keystore.properties` with `storePassword`, `keyAlias` and
`keyPassword`), or from `$TS_SIGNING_DIR`. Without it, Gradle signs with the debug key and warns.
The keystore is never in the repository.

## Why the Chimera-specific parts

The NDK and the SDK tools are glibc programs. `gcompat` runs them, with three exceptions:

- The NDK's `ld.lld` crashes under `gcompat`, so the NDK's clang links with Chimera's
  (`--ld-path=/usr/bin/ld.lld`).
- The Android command-line tools after 20.0 replaced the Java `sdkmanager` with a glibc program
  that does not run, so `scripts/setup` pins 20.0.
- Gradle loads its glibc native libraries on musl and crashes, so `scripts/env.sh` turns them off
  (`-Dorg.gradle.native=false`).

Chimera's `make` is BSD make; CMake finds `gmake` and uses it for opus.

## Reproducibility

Build paths are kept out of the library: `--remap-path-prefix` for Rust and `-ffile-prefix-map`
for C rewrite the paths under the repository and `~/.cargo` to `/src` and `/cargo`.

With that, the library is byte-for-byte reproducible: the same commit built in two different
directories gives the same `libtslib_jni.so`. To check a release, build its tag and compare
`sha256sum app/src/main/jniLibs/arm64-v8a/libtslib_jni.so` with the library inside the published
APK (`unzip -p telespeak-<version>.apk lib/arm64-v8a/libtslib_jni.so | sha256sum`). The APK as a
whole differs, because it carries the release signature.
