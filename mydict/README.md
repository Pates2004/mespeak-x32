# Optional Polish dictionary: previous numbers and regular stress

[Polska instrukcja](README.pl.md)

`pl.dict` is a **complete** alternative dictionary, not an overlay. It retains
the current pronunciation fixes, with these approved differences:

- Numeric 30/40/200 and their compounds retain the earlier `c;` sound instead
  of the default `ts;` Polish soft affricate. 20, 300, the audible ć in 600 and
  written number words are unchanged.
- The reviewed noun group ending in `-ika/-yka` uses regular penultimate stress:
  mu-ZY-ka, fi-ZY-ka, tech-NI-ka, me-cha-NI-ka, plus mak-SI-mum and mi-NI-mum.
  The default keeps careful MU-zyka, FI-zyka, TECH-nika, me-CHA-nika,
  MAK-simum and MI-nimum. These are style choices, not a claim that all
  colloquial alternatives are wrong. Monika has mo-NI-ka in both versions.
- Kolaż uses accepted KO-laż; the default retains the accepted rarer ko-LAŻ.

Only the reviewed noun stress rules and three explicit word preferences change
style. Verb stress rules, speed algorithms and ordinary sentence spacing remain
unchanged. Neither dictionary recognizes meaning from context: shared written
forms receive the selected style in either meaning.

Installers use the default. The native runtime filename is **`pl_dict`**,
without an extension. On Windows, close applications using the engine, retain
your existing dictionary outside `espeak-data`, and copy `pl.dict` into the
installed `espeak-data` under the name `pl_dict`. Administrator permission may
be required. Restore the default with the same release's `espeak-data/pl_dict`.
An installer upgrade may restore the default; copy the matching alternative
again if desired. Do not replace phoneme tables or use another eSpeak NG
release's dictionary.

On Android, select `pl.dict` in dictionary import; the app stores it as
`pl_dict`. Restore the default by importing `engine/espeak-data/pl_dict` from
the **exact same version/tag** of this repository. This replaces only that
dictionary, preserving other imports/settings; clearing app data is unnecessary.
Android may preserve an imported dictionary across app updates. To receive
later fixes, import `mydict/pl.dict` from the **new matching release/tag** after
updating the app.

`dictsource/pl_list` and `dictsource/pl_rules` are the full Polish sources;
there is no `pl_roots`, `pl_listx` or `pl_extra` dependency. The host compiler
and phoneme tables must match this classic core/data. The little-endian binary
is shared by this family's Windows x86/x64 and Android architectures. Rebuild
with a compatible **Windows host CLI**, not an arbitrary eSpeak NG executable.

From PowerShell inside `mydict`:

```powershell
.\rebuild.ps1 -EspeakExe 'C:\path\to\matching\espeak.exe' -InstallDefault
.\rebuild.ps1 -EspeakExe 'C:\path\to\matching\espeak.exe' -CheckOnly
.\rebuild.ps1 -VerifyOnly
```

The first command compiles both variants, updating this folder's `pl.dict` and
the checkout's default. Without `-InstallDefault`, only `pl.dict` is updated.
`-CheckOnly` rebuilds and compares both without changing product dictionaries.
`-DataPath` selects the **parent of espeak-data** for compilation input;
otherwise the checkout root (Windows) or `engine` (Android) is used.
Installing the default always targets this checkout, even with external input.
The script checks the phoneme/voice hashes in `phoneme-data.json`, retains
evidence in central Workspace, and does not modify host installation data.

`-VerifyOnly` needs no compiler. Packaging uses it to verify both binaries,
sources and the approved mapping against `dictionary-manifest.json`. Source
and `voices/pl` text hashes normalize UTF-8/LF, tolerating Git CRLF conversion;
binary table hashes and runtime files stay unchanged. Git marks `pl.dict`
binary. Update sources and manifest together after a reviewed rebuild.
Dictionary sources must be valid UTF-8 without a BOM. The classic compiler
can interpret a leading BOM as an extra dictionary word; verification rejects
it explicitly. LF and CRLF line endings are both supported.

Windows keeps canonical default sources in root `dictsource`. The script
checks three numeric entries, three word stress preferences and the 133 noun
rules in `careful-stress-rules.txt` (70 for i, 63 for y). The alternative
neutralizes only their `=` stress marker, preserving vowel phonemes, inflection
contexts and name exceptions. Android reconstructs the same default sources
from this complete alternative and the explicit mapping. Both are compiled
normally with `--compile=pl`, without binary patches or table installation.
Future unrelated corrections must reach both versions before rebuilding.
The script rejects an unreviewed rule count or unmatched source pair.

References: [WSJP: thirty](https://wsjp.pl/haslo/podglad/37651/trzydziesci),
[WSJP: three hundred](https://wsjp.pl/haslo/podglad/37654/trzysta),
[WSJP: music](https://wsjp.pl/haslo/podglad/2840/muzyka/5055446/w-szkole),
[WSJP: minimum](https://wsjp.pl/haslo/podglad/34364/minimum),
[UŚ: Monika](https://www.poradniajezykowa.pl/baza_archiwum.php?TEMAT=Wymowa),
[UW: careful/colloquial stress](https://poradniajezykowa.uw.edu.pl/porady/jeszcze-raz-o-akcentowaniu-wyrazow-zakonczonych-na-ika-yka/),
[Awramiuk: kolaż variants, p. 207](https://repozytorium.uwb.edu.pl/jspui/bitstream/11320/2573/1/Polszczyzna_Mazowsza_i_Podlasia_tom_XVIII.pdf).
