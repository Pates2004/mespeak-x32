# mespeak x32

`mespeak x32` is the 32-bit Android Text-to-Speech edition of
[`mespeak`](https://github.com/Pates2004/mespeak), based on eSpeak
1.44.05-r28. It keeps the same TTS service, settings, JNI integration, native
engine and current Polish dictionary as the 64-bit edition.

The speech-rate dialog includes an optional Sonic time-compression boost. The
normal eSpeak rate remains unchanged; above 450 WPM the legacy core uses the
clarity-oriented timing model from eSpeak NG before Sonic performs the
remaining pitch-preserving compression.

The installed application is labelled `mespeak`, like the 64-bit edition. Its
settings interface follows the system language in Polish and uses English for
every other locale. A checked-by-default setting can hide the launcher icon
without disabling the TTS engine or its Android TTS settings entry.

On first launch, bundled voice data is installed before the settings lists are
built, all languages start selected, and a shortcut opens Android's system TTS
settings. The application includes the complete 104-variant collection from
eSpeak NG 1.52.0, with the `fast` file adapted to classic-eSpeak syntax.

Starting with r28, published APKs are optimized release builds. Their signing
lineage preserves updates from the r27 Android Debug certificate while moving
modern Android devices to the permanent Pates2004 release certificate. Its
SHA-256 fingerprint is
`2928C21E152E9FD245A5F423F0A11BD8FD658C09282684481C82EDF599E9E055`.
Verbose Java and JNI diagnostic logging is disabled in release builds.

This repository is intended for older 32-bit Android devices and builds:

- `armeabi-v7a`
- `x86`

The application identifier is `com.pates2004.mespeak.x32`, allowing it to
coexist with the 64-bit edition on Android systems that support both ABI
families.

## Build

Use JDK 17 and the Android SDK, NDK and CMake versions declared in
`build.gradle`:

```text
gradlew.bat assembleDebug
```

For an installable development APK, use `gradlew.bat assembleDebug`. For a
locally signed release that preserves the signing lineage from r27, run:

```text
powershell -ExecutionPolicy Bypass -File build-release.ps1
```

The release script reads the private signing material from the sibling
`signing` directory, which must never be committed to this public repository.

## Licensing

The eSpeak synthesizer sources and data are distributed under GPLv3; see
`LICENSE-eSpeak.txt`. Android integration files retain their original Apache
2.0 copyright and license notices.
