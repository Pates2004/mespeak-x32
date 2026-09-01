# mespeak x32

`mespeak x32` is the 32-bit Android Text-to-Speech edition of
[`mespeak`](https://github.com/Pates2004/mespeak), based on eSpeak
1.44.05-r22. It keeps the same TTS service, settings, JNI integration, native
engine and current Polish dictionary as the 64-bit edition.

The speech-rate dialog includes an optional Sonic time-compression boost. The
normal eSpeak rate remains unchanged; when enabled, rates above the native
450 WPM limit are compressed in the PCM stream with pitch preserved as far as
Sonic allows.

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
