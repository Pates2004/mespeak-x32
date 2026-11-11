# Opcjonalny słownik: wcześniejsze liczby i regularny akcent

[English instructions](README.md)

`pl.dict` to **kompletny** wariant słownika, a nie nakładka. Zachowuje bieżące
poprawki wymowy, z następującymi uzgodnionymi różnicami:

- Liczbowe 30/40/200 i ich złożenia zachowują wcześniejsze `c;` zamiast
  domyślnego miękkiego `ts;` (ć). 20, 300, słyszalne ć w 600 i liczby zapisane
  słowami pozostają bez zmian.
- Omawiana grupa rzeczowników na `-ika/-yka` ma regularny akcent:
  mu-ZY-ka, fi-ZY-ka, tech-NI-ka, me-cha-NI-ka, a także mak-SI-mum i mi-NI-mum.
  Domyślnie pozostają staranne MU-zyka, FI-zyka, TECH-nika, me-CHA-nika,
  MAK-simum i MI-nimum. Jest to wybór stylu, a nie uznanie wszystkich wariantów
  potocznych za błędne. Monika ma mo-NI-ka w obu wersjach.
- Kolaż otrzymuje dopuszczalne KO-laż; domyślnie pozostaje dopuszczalne,
  rzadsze ko-LAŻ.

Zmiana stylu obejmuje wskazane reguły rzeczowników i trzy preferencje
słów. Reguły akcentowania czasowników, algorytmy szybkości i zwykłe odstępy
zdań pozostają bez zmian. Żaden słownik nie rozpoznaje znaczenia z kontekstu:
wspólny zapis otrzyma wybrany styl niezależnie od znaczenia.

Instalatory używają wersji domyślnej. Rzeczywista nazwa pliku silnika to
**`pl_dict`**, bez rozszerzenia. Na Windows zamknij aplikacje korzystające
z syntezatora, zachowaj dotychczasowy słownik poza `espeak-data` i skopiuj
`pl.dict` do zainstalowanego `espeak-data` pod nazwą `pl_dict`. Mogą być
potrzebne uprawnienia administratora. Domyślny słownik przywrócisz przez
`espeak-data/pl_dict` z tego samego wydania. Aktualizacja instalatorem może
przywrócić wersję domyślną; podmień ponownie pasujący wariant opcjonalny,
jeśli go chcesz. Nie wymieniaj tablic fonemów i nie używaj słownika z obcego
wydania eSpeak NG.

Na Androidzie wybierz `pl.dict` w imporcie; aplikacja zapisze go jako
`pl_dict`. Wersję domyślną przywrócisz, importując `engine/espeak-data/pl_dict`
dokładnie z **tej samej wersji/tagu** repozytorium. Zastąpi to tylko ten słownik,
zachowując pozostałe importy i ustawienia; nie trzeba czyścić danych aplikacji.
Android może zachować import podczas aktualizacji. Aby otrzymać kolejne
poprawki, po aktualizacji aplikacji zaimportuj `mydict/pl.dict` z **nowego,
pasującego wydania/tagu**.

`dictsource/pl_list` i `dictsource/pl_rules` to pełne polskie źródła;
nie ma zależności `pl_roots`, `pl_listx` ani `pl_extra`. Kompilator i tablice
fonemów muszą odpowiadać klasycznemu rdzeniowi/danym tej wersji. Ten sam plik
little-endian działa w Windows x86/x64 i architekturach Androida tej rodziny.
Do odbudowy użyj zgodnego **programu konsolowego Windows**, a nie dowolnego
eSpeak NG.

W PowerShell, w folderze `mydict`:

```powershell
.\rebuild.ps1 -EspeakExe 'C:\sciezka\do\zgodnego\espeak.exe' -InstallDefault
.\rebuild.ps1 -EspeakExe 'C:\sciezka\do\zgodnego\espeak.exe' -CheckOnly
.\rebuild.ps1 -VerifyOnly
```

Pierwsze polecenie kompiluje oba warianty, aktualizując `pl.dict` i wersję
domyślną repozytorium. Bez `-InstallDefault` aktualizowany jest tylko
`pl.dict`. `-CheckOnly` odbudowuje i porównuje oba pliki bez zmiany słowników
produktu. `-DataPath` wskazuje **katalog nadrzędny espeak-data** jako dane
wejściowe; domyślnie jest to główny katalog Windows albo `engine` Androida.
Domyślny słownik jest zapisywany zawsze w tym repozytorium, również przy
zewnętrznym źródle danych. Skrypt sprawdza hashe tablic/głosu z
`phoneme-data.json` i pozostawia dowody w centralnym Workspace; nie zmienia
danych syntezatora zainstalowanego w systemie.

`-VerifyOnly` nie wymaga kompilatora. Pakowanie sprawdza nim oba pliki,
źródła i zatwierdzoną mapę względem `dictionary-manifest.json`. Hashe tekstów
źródeł i `voices/pl` są normalizowane do UTF-8/LF, więc konwersja CRLF przez
Git nie przeszkadza; hashe tablic binarnych i pliki danych nie są zmieniane.
Git traktuje `pl.dict` jako binarny. Po zatwierdzonej odbudowie aktualizuj
źródła i manifest razem.
Źródła muszą być poprawnym UTF-8 bez BOM. Klasyczny kompilator może uznać
początkowy BOM za dodatkowe słowo; weryfikator jawnie go odrzuca. Obsługiwane
są zarówno końce wierszy LF, jak i CRLF.

Główne źródła wersji domyślnej Windows pozostają w `dictsource`. Skrypt
sprawdza trzy wpisy liczbowe, trzy preferencje akcentu i 133 reguły
rzeczowników z `careful-stress-rules.txt` (70 dla i, 63 dla y). Alternatywa
neutralizuje jedynie znacznik `=`, zachowując fonemy samogłosek, warunki odmiany
i wyjątki imion. Na Androidzie skrypt odtwarza takie same źródła domyślne
z pełnego wariantu alternatywnego i jawnej mapy. Oba są kompilowane zwykłym
`--compile=pl`, bez edycji binarek i instalowania tablic. Kolejne niezwiązane
poprawki trzeba uwzględniać w obu wersjach. Skrypt odrzuci nieuzgodnioną
liczbę reguł lub niepasującą parę źródeł.

Źródła: [WSJP: trzydzieści](https://wsjp.pl/haslo/podglad/37651/trzydziesci),
[WSJP: trzysta](https://wsjp.pl/haslo/podglad/37654/trzysta),
[WSJP: muzyka](https://wsjp.pl/haslo/podglad/2840/muzyka/5055446/w-szkole),
[WSJP: minimum](https://wsjp.pl/haslo/podglad/34364/minimum),
[UŚ: Monika](https://www.poradniajezykowa.pl/baza_archiwum.php?TEMAT=Wymowa),
[UW: akcent staranny i potoczny](https://poradniajezykowa.uw.edu.pl/porady/jeszcze-raz-o-akcentowaniu-wyrazow-zakonczonych-na-ika-yka/),
[Awramiuk: warianty kolażu, s. 207](https://repozytorium.uwb.edu.pl/jspui/bitstream/11320/2573/1/Polszczyzna_Mazowsza_i_Podlasia_tom_XVIII.pdf).
