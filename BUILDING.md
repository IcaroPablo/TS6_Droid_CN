# Building TeleSpeak

The release APKs are built natively on [Chimera Linux](https://chimera-linux.org) (x86_64, musl)
with the scripts in [`scripts/`](scripts). Those scripts and the pinned versions below are what
produced the published builds.

## What gets built

- `tslib_multi/` (git submodule): the Rust core, built as `libtslib_jni.so` for arm64-v8a. Its
  `Cargo.lock` pins every dependency, and the build runs with `--locked`.
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
| clang, lld | Chimera's (22) | host package |
| Gradle | 8.14, from the wrapper | `./gradlew`, in `~/.gradle` |

The versions live in [`scripts/env.sh`](scripts/env.sh).

## Steps

1. Host packages:

   ```
   apk add git curl unzip perl gmake cmake clang lld llvm-binutils openjdk21 gcompat libgcc-chimera-devel
   ```

   `gcompat` runs the SDK's glibc `aapt2`, and `libgcc-chimera-devel` provides the `libgcc_s` that
   the musl Rust toolchain needs.

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

The NDK's compilers and the Android command-line tools after 20.0 are glibc programs. Instead, the
scripts use Chimera's clang with the NDK's sysroot and compiler runtime:

- `scripts/chimera/aarch64-linux-android29-clang{,++}` are wrappers that point clang at the NDK
  sysroot and compiler runtime.
- `scripts/chimera/android-aarch64.cmake` is the CMake toolchain file for the C dependencies
  (opus, OpenSSL).
- `scripts/chimera/include/stdc-predef.h` is an empty header, because Chimera's clang always
  includes one and bionic has none.

## Reproducibility

Build paths are kept out of the library:

- `--remap-path-prefix` for Rust and `-ffile-prefix-map` for C rewrite the paths under the
  repository and `~/.cargo`.
- The cargo target directory is a fixed `/var/tmp/telespeak-target`, because OpenSSL records its
  install path.
- `SOURCE_DATE_EPOCH` replaces OpenSSL's build time with the native source's commit time.

Two builds in different directories differ only in the crates that depend on `ts-bookkeeping`, from
tsclientlib. Its build script generates a `match` by iterating a `HashMap`, so the arms come out in
a different order on every build. Fixing that upstream (a `BTreeMap`) would make the library
byte-for-byte reproducible.
