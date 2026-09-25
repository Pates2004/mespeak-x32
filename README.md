# mespeak x32

`mespeak x32` is the 32-bit Android Text-to-Speech edition of
[`mespeak`](https://github.com/Pates2004/mespeak), based on eSpeak
1.44.05-r31. It keeps the same TTS service, settings, JNI integration, native
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

Release r29 synchronizes the Polish dictionary with the Windows editions and
keeps hard `z` in the complete *bezinteres-* word family.

Release r30 stabilizes launcher visibility by sharing the same preference
storage between Settings and TTS, and gives Recents a stable settings activity
even when the launcher icon is hidden. The shortcut to Android TTS settings
handles missing or restricted OEM activities, with Accessibility and general
Settings as fallbacks. The existing pitch-range slider is labelled Inflection
(Modulacja in Polish). An inline text field with Speak and Stop buttons previews
the current mespeak settings without changing Android's default engine.

The native core and voice catalog are initialized once per process, so opening
Settings or checking voice data does not reset a running synthesis. Voice data
is unchanged since r29 and keeps its existing data-version marker. Signed APKs
are also copied to `installfiles` by `build-release.ps1`.

Release r31 updates the Polish dictionary to keep `ci` in forms such as
*druciana*, *bociana*, *starcia* and *tarcia*. The native speech behavior and
settings are otherwise the same as r30. The changed dictionary increments the
voice-data marker so an upgrade installs the corrected data.
The refresh also retains manually imported dictionaries, including overrides
with the same filename as a bundled dictionary, and migrates imports left in
older credential-protected storage.

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
