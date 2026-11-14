# Native cancellation fixtures / Natywne testy anulowania

These two small executables are built explicitly, separately from the product.
They accept no input and exit after a fixed number of checks. A successful run
prints one JSON line and exits0. Use a10s execution deadline. They do not import
dictionaries, change preferences, open audio output, or exercise SSML.

- `cancellation_delivery_test`: compiles the real JNI binding and pinned Sonic
  with a deterministic fake JVM/core producer. Checks a stop before native
  entry, stop during direct/Sonic output, stop from a word callback, successful
  next request, and stopping Sonic's remaining drain/flush. This is a boundary
  unit test; it does not establish natural-language waveform equivalence.
- `wavegen_stop_test`: compiles the real wave generator; observes ownership of
  queued voice copies, queue wraparound, resume/echo reset and repeated stop.

Configure this directory with the normal Android NDK CMake toolchain,
`ANDROID_ABI=x86_64` (or another supported ABI), `ANDROID_PLATFORM=21`,
`ANDROID_STL=c++_static`, and `SONIC_SOURCE_DIR` pointing at the product's
existing Sonic checkout, revision `fbf75c3d6d846bad3bb3d456cbc5d07d9fd8c104`.
Build outside the source tree, then run each executable on an owned emulator
with the matching ABI. Ordinary real-core text, canceled-request recovery and
framework behavior require the separate Android instrumentation tests.

---

Te dwa małe programy buduje się jawnie, osobno od produktu. Nie przyjmują danych
wejściowych; wykonują stałą liczbę kontroli. Sukces to jeden wiersz JSON i kod
wyjścia0. Uruchamiaj z limitem10s. Nie importują słowników, nie zmieniają ustawień,
nie odtwarzają dźwięku i nie sprawdzają SSML.

- `cancellation_delivery_test`: prawdziwe JNI i przypięty Sonic oraz kontrolowane
  atrapy JVM/producenta PCM. Sprawdza anulowanie przed wejściem do silnika,
  w trakcie przekazywania zwykłego/Sonic audio i callbacku wyrazu, następne
  żądanie oraz przerwanie opróżniania/kończenia Sonic. To test granicy JNI,
  nie dowód identyczności wymowy prawdziwego tekstu.
- `wavegen_stop_test`: prawdziwy generator; sprawdza zwalnianie oczekujących kopii
  głosu, zawinięcie kolejki, reset wznowienia/echa i ponowne zatrzymanie.

Skonfiguruj ten katalog standardowym toolchainem CMake z Android NDK, z opcjami
`ANDROID_ABI=x86_64` (lub innym obsługiwanym ABI), `ANDROID_PLATFORM=21`,
`ANDROID_STL=c++_static` i `SONIC_SOURCE_DIR` wskazującym istniejące źródła Sonic
produktu w rewizji podanej wyżej. Buduj poza źródłami, uruchamiaj na własnym
emulatorze z odpowiednim ABI. Zwykły tekst, stan po przerwaniu i współpracę
z frameworkiem sprawdzają osobne androidowe testy instrumentacyjne.
