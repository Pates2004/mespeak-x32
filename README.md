# mespeak x32

[Polska dokumentacja](README.pl.md)

`mespeak x32` is the 32-bit Android Text-to-Speech edition of
[`mespeak`](https://github.com/Pates2004/mespeak), based on eSpeak
1.44.05-r38. It keeps the same TTS service, settings, JNI integration, native
engine and current Polish dictionary as the 64-bit edition.

Release r38 reuses an unchanged effective voice for ordinary consecutive requests,
avoiding repeated voice/dictionary loading. Language/variant changes, data imports,
SSML or embedded controls, failures and cancellation invalidate that reuse.
The normal generator/echo and breath-filter resets are retained, so reuse does
not skip those parts of voice selection. A fixed catalog limit that could hide
bundled voices is removed; allocation and JNI enumeration failures are handled
without publishing a successful empty metadata cache.
Request callbacks/text are released after synthesis, rejected output is handled
before unnecessary work, and an unreadable installed data marker triggers recovery.
The dictionary and all bundled data remain exactly r37; their separate data
version stays r37, so this app-only update does not require re-extracting them.
There is no new background worker and no claim of measured battery savings.

The speech-rate settings offer smooth, standard and Sonic x3 modes. Smooth
mode spans 80-1350 WPM, uses native synthesis up to 300 WPM, and retains the
same native articulation above that point while Sonic gradually compresses
the audio. Fresh installations default to smooth. Existing standard/boost
settings retain their effective speed and mode. An older boosted preference
below the current base-rate minimum migrates to smooth to preserve its
effective speed instead of increasing it. Changing modes preserves
the speed where it fits the selected range. Standard and x3 retain the
previous engine behavior. The independent option to ignore Android's caller
rate multiplier remains available.

Appearance follows the system by default, with explicit light and dark
overrides. The primary system language selects Polish only when it is Polish;
all other primary languages select English. Optional usage hints can be hidden
without removing essential labels, setting values, errors or warnings.

The r37 Polish dictionary corrects consonant voicing in square-bracket and
`²` names, adds short speed-scaled pauses between words in existing compound
character/symbol labels including `u zamknięte`, and fixes malformed existing
symbol phonetics. Composed accent and capital-letter labels also have clear
word boundaries. The chosen symbol vocabulary is preserved. The BOY-style
consonant in numeric 30/40/200, unchanged numeric
300, conventional 50/60/90 reductions, audible `ć` in `sześćset`/600 and the
careful `pierwsz-` pronunciation are retained.
Release r37 changed dictionary data, not application or synthesizer logic.
Themes, language selection and speech-rate modes are unchanged. The existing
nonbreaking-space label remains unreachable at runtime; this update does not
claim that every character is universally spoken. Rare ligatures retain
their existing internal letter-name spelling. Preserved user-imported
dictionaries can still override the bundled dictionary.
Updates retain imported dictionaries according to the existing preservation
setting. Final signed release APKs belong in `installfiles`.

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
Settings or checking voice data does not reset a running synthesis. In r30,
voice data was unchanged since r29 and kept its then-current marker. Signed APKs
are placed in `installfiles` by `build-release.ps1`, without retaining an
identical signed copy in the build directory.

Release r31 updates the Polish dictionary to keep `ci` in forms such as
*druciana*, *bociana*, *starcia* and *tarcia*. The native speech behavior and
settings are otherwise the same as r30. The changed dictionary increments the
voice-data marker so an upgrade installs the corrected data.
The refresh also retains manually imported dictionaries, including overrides
with the same filename as a bundled dictionary, and migrates imports left in
older credential-protected storage.

Release r32 adds a default-on checkbox in the Android settings to keep imported
dictionaries during voice-data updates. Turning it off leaves current imports
in use until the next data update; that update installs bundled dictionaries
and discards retained import copies. The r32 data marker exercises this policy
when updating from r31. Legacy imports are migrated at most once, including
after Direct Boot if credential storage was initially locked.

Release r33 adds an opt-in setting to ignore speech-rate multipliers requested
by Android, TalkBack and other TTS clients. It defaults to off. The saved
mespeak speed and optional Sonic boost continue to work when enabled. The
voice data is unchanged from r32; both editions use the same signed release
process and keep their previous signing lineage.

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
