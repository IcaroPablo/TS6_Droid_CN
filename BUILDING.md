# Building TeleSpeak

The release APKs are built natively on [Chimera Linux](https://chimera-linux.org) (x86_64, musl)
with the scripts in [`scripts/`](scripts). Those scripts and the pinned versions below are what
produced the published builds.

## What gets built

- `tslib_multi/` (git submodule): the Rust core, built as `libtslib_jni.so` for arm64-v8a. Its
  `Cargo.lock` pins every dependency, and the build runs with `--locked`. The TeamSpeak protocol
  comes from [tsclientlib](https://github.com/IcaroPablo/tsclientlib/tree/telespeak), our fork
  without the server-nickname lookup. The only C code is opus 1.6.1, from xiph's repository as a
  submodule inside `tslib_multi/` (`crates/audiopus-sys/opus`), compiled by the Rust build without
  CMake.
- `app/`: the Kotlin app, built by Gradle into the release APK. The library built above is
  committed at `app/src/main/jniLibs/arm64-v8a/`, so a Gradle-only build packages it as is.

## Versions

| Tool | Version | Installed by |
|---|---|---|
| Rust | 1.99.1, target `aarch64-linux-android` | `scripts/setup`, in `~/.rustup` and `~/.cargo` |
| Android NDK | r29 (29.0.14206865) | `scripts/setup`, in `~/.local/share/android-sdk` |
| Android SDK | platform 35, build-tools 34.0.0, platform-tools 37.0.1 | `scripts/setup`, same place |
| JDK | 21 | host package |
| clang, lld | Chimera's (22), for the host side and for linking | host package |
| Gradle | 8.14, from the wrapper | `./gradlew`, in `~/.gradle` |

The versions live in [`scripts/env.sh`](scripts/env.sh).

## Steps

1. Host packages:

   ```
   apk add git curl unzip clang lld llvm-binutils openjdk21 gcompat libgcc-chimera-devel
   ```

   `gcompat` runs the NDK's clang and the SDK's `aapt2`, which are glibc programs, and
   `libgcc-chimera-devel` provides the `libgcc_s` that they and the musl Rust toolchain need.

2. Clone with the submodules and run the one-time setup:

   ```
   git clone --recursive https://github.com/IcaroPablo/TS6_Droid_CN.git
   cd TS6_Droid_CN
   scripts/setup
   ```

   It changes no shell profile; the scripts set `PATH` themselves. The Android packages are
   downloaded straight from Google's repository and checked against the SHA-256 sums in
   `scripts/env.sh`; using them means accepting the
   [Android SDK License](https://developer.android.com/studio/terms).

3. Build:

   ```
   scripts/build
   ```

   This builds the library, runs the unit tests and writes `out/telespeak-<version>.apk`. It
   checks out the `tslib_multi` commit this repository records, also after a `git pull`; if
   `tslib_multi` has commits or uncommitted changes of its own, it builds them as they are and
   says so.

To run `./gradlew` or `cargo` by hand, first source the environment from the repository root:
`. scripts/env.sh`.

## Signing

`scripts/build` reads the release keystore from `~/.local/share/telespeak/signing/`
(`telespeak-release.keystore` and `keystore.properties` with `storePassword`, `keyAlias` and
`keyPassword`), or from `$TS_SIGNING_DIR`. Without it, Gradle signs with the debug key and warns.
The keystore is never in the repository.

## Releasing

1. Bump `versionCode` (always up, or Android refuses the update) and `versionName` in
   `app/build.gradle.kts`, commit and push to `main`.
2. Write the release notes in a Markdown file and run:

   ```
   scripts/release notes.md
   ```

   It checks that `main` matches GitHub and the version is new, runs `scripts/build`, checks that
   the committed library is what the build produces and that the APK is signed with the release
   key (`TS_SIGNER_SHA256` in `scripts/env.sh`), then shows the notes and asks before tagging
   `vX.Y.Z` and publishing the release with the APK. The notes get the library's SHA-256 appended.
   It needs the GitHub CLI (`gh`, package `github-cli`), logged in.

The in-app updater reads the latest release: its tag gives the version, and it downloads the
`.apk` attached to it.

## Why the Chimera-specific parts

The NDK and the SDK tools are glibc programs. `gcompat` runs them, with these exceptions:

- The NDK's `ld.lld` crashes under `gcompat`, so the NDK's clang links with Chimera's
  (`--ld-path=/usr/bin/ld.lld`).
- The Android command-line tools' `sdkmanager` is a glibc program since 21.0 and does not run, so
  `scripts/setup` downloads the SDK packages itself.
- Gradle loads its glibc native libraries on musl and crashes, so `scripts/env.sh` turns them off
  (`-Dorg.gradle.native=false`).
- NDK r30's clang crashes under `gcompat` at startup; r29 is the newest that runs. Check the
  compiler (`clang --version`) before moving to a newer NDK.

## Reproducibility

Build paths are kept out of the library: `--remap-path-prefix` for Rust and `-ffile-prefix-map`
for C rewrite the paths under the repository and `~/.cargo` to `/src` and `/cargo`.

With that, the library is byte-for-byte reproducible: the same commit built in two different
directories gives the same `libtslib_jni.so`. To check a release, build its tag and compare
`sha256sum app/src/main/jniLibs/arm64-v8a/libtslib_jni.so` with the library inside the published
APK (`unzip -p telespeak-<version>.apk lib/arm64-v8a/libtslib_jni.so | sha256sum`). The APK as a
whole differs, because it carries the release signature.
