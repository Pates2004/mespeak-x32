# mespeak x32

`mespeak x32` is the 32-bit Android Text-to-Speech edition of
[`mespeak`](https://github.com/Pates2004/mespeak), based on eSpeak
1.44.05-r27. It keeps the same TTS service, settings, JNI integration, native
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

The resulting APK is `build/outputs/apk/debug/mespeak-x32-debug.apk`.

## Licensing

The eSpeak synthesizer sources and data are distributed under GPLv3; see
`LICENSE-eSpeak.txt`. Android integration files retain their original Apache
2.0 copyright and license notices.
