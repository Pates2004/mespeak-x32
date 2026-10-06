# mespeak — Android 32-bit

[English documentation](README.md)

mespeak 1.44.05-r34 to silnik zamiany tekstu na mowę dla Androida. Integracja
usługi, ustawień i JNI bazuje na eSpeak NG, a natywny syntezator oraz dane głosów
pochodzą z rozwijanego klasycznego eSpeak 1.44.05, z naszym polskim słownikiem.

Ta edycja obsługuje ABI `armeabi-v7a` i `x86` i używa identyfikatora `com.pates2004.mespeak.x32`.
Druga edycja jest dostępna w [mespeak](https://github.com/Pates2004/mespeak).
Obie mają te same funkcje i dane, a na liście aplikacji nazywają się mespeak.

## Instalacja i ustawienia

Podpisany plik release znajduje się w `installfiles`. Pierwsze otwarcie
instaluje dołączone dane głosów przed wyświetleniem list, z wybranymi wszystkimi
językami. Można przejść z aplikacji do systemowych ustawień zamiany tekstu na
mowę; przy niedostępnej stronie producenta dostępne są bezpieczne przejścia
do ustawień dostępności lub ustawień ogólnych. Podgląd Powiedz/Stop używa
mespeak bez przestawiania domyślnego silnika Androida.

Domyślnie zaznaczona opcja pokazuje ikonę w launcherze. Ukrycie ikony nie
wyłącza syntezatora i nie usuwa go z ustawień TTS. Modulacja reguluje zmienność
wysokości mowy; nie jest tym samym co podstawowa wysokość głosu.

Interfejs jest polski, gdy główny język systemowy jest polski; dla wszystkich
innych jest angielski, również gdy polski występuje jako drugi język na liście.
Motyw domyślnie podąża za systemem, ale można wymusić jasny albo ciemny.
Opcjonalne podpowiedzi można wyłączyć bez utraty niezbędnych etykiet, wartości,
komunikatów błędów i ostrzeżeń. Zmiana motywu zachowuje tekst do odsłuchu.

Katalog zawiera 104 warianty z eSpeak NG 1.52.0; plik `fast` dostosowano do
składni klasycznego silnika. Katalog głosów i natywny silnik inicjalizują się
raz na proces, więc samo otwarcie ustawień nie resetuje trwającej syntezy.

## Prędkość

Dostępne są trzy tryby:

- Płynny — domyślny w nowych ustawieniach, 80–1350 słów na minutę. Do 300 SNM
  pracuje silnik natywny; powyżej zachowuje tę samą artykulację, a Sonic
  stopniowo skraca dźwięk.
- Standardowy — dotychczasowy zakres bazowy 80–450 SNM i dotychczasowe zachowanie.
- Sonic x3 — dotychczasowe trzykrotne podbicie prędkości bazowej.

Dotychczasowy zapisany tryb i efektywna prędkość są zachowywane. Wyjątek:
stare podbite ustawienie o bazie poniżej 80 przechodzi na tryb płynny, aby
zachować rzeczywistą prędkość, zamiast wymuszać skok do 240 SNM. Ręczna zmiana
trybu zachowuje prędkość, o ile mieści się ona w zakresie nowego trybu.
Podsumowanie i suwak pokazują tę samą efektywną wartość.

Osobna opcja ignorowania prędkości żądanej przez Androida/TalkBack/inne klienty
pozostaje domyślnie wyłączona i działa we wszystkich trybach.

## Słownik i aktualizacje

r34 zachowuje spółgłoskę w rodzinie `pierwsz-` oraz pełniejsze zbitki w
`sześćset`, `pięćdziesiąt`, `sześćdziesiąt`, `dziewięćdziesiąt` i pochodnych.
Zapis cyfrowy zgadza się ze słownym; naturalne upodobnienia polskie pozostają.
Pełniejsza wymowa jest wybraną preferencją, a nie twierdzeniem, że przyjęte
w języku uproszczenia są niepoprawne.

Domyślnie zachowywane są ręcznie importowane słowniki, również te zastępujące
plik dołączony do aplikacji. Wyłączenie tej opcji działa przy następnej
aktualizacji danych; nie usuwa importów natychmiast. Dawne importy są
przenoszone bez porzucania danych użytkownika, również po odblokowaniu telefonu.

Historia: r29 — rodzina bezinteres-; r30 — stabilniejsza ikona/ostatnie aplikacje,
ustawienia TTS, modulacja i podgląd; r31 — poprawki ci oraz zachowanie importów;
r32 — przełącznik zachowania importów; r33 — ignorowanie prędkości klienta.
r34 obejmuje bieżące zmiany słownika, wyglądu i trybów prędkości.

## Kompilacja i podpis

Wymagane są JDK 17 oraz wersje SDK, NDK i CMake z `build.gradle`.
Kompilacja deweloperska: `gradlew.bat assembleDebug`.
Podpisany release: `powershell -ExecutionPolicy Bypass -File build-release.ps1`.
Skrypt pobiera klucze z sąsiedniego katalogu `signing`, którego nie wolno
publikować w repozytorium. Finalny podpisany APK przenosi do `installfiles`,
bez identycznej drugiej kopii w katalogu kompilacji.

Wydania od r28 są optymalizowanymi kompilacjami release, bez diagnostycznego
logowania Java/JNI. Linia podpisów umożliwia aktualizację z dawnego certyfikatu
r27 o nazwie Android Debug do trwałego certyfikatu Pates2004 na nowszych
Androidach. Nazwa historycznego certyfikatu nie oznacza, że aktualny APK jest
kompilacją debug. SHA256 trwałego certyfikatu:
`2928C21E152E9FD245A5F423F0A11BD8FD658C09282684481C82EDF599E9E055`.

## Licencje

Źródła i dane eSpeak: GPLv3, patrz `LICENSE-eSpeak.txt`. Pliki integracji
androidowej zachowują oryginalne informacje o prawach autorskich i Apache 2.0.
