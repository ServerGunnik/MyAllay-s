# MyAllay's

Klientowy mod Fabric, który oznacza graczy jako sojuszników lub wrogów. Działa wyłącznie po stronie gracza: zmienia tylko to, co widzisz (kolor nicku, prefiks, ramka wokół gracza), niczego nie wysyła na serwer.

- Główna wersja: **Minecraft 26.2** (folder `src/`, Java 25)
- Starsze wersje: **1.21.4 – 1.21.11** w folderach `legacy-*` (Java 21)

## Twoje relacje (sojusznik / wróg)

Własna lista graczy, zapisywana w `config/allymod/relations.json`.

- **F6** otwiera menu: lista graczy online z przyciskami `+A` / `+E`, Twoje relacje z przyciskiem `X`, ręczne dodawanie po nicku.
- Nad głową: `[A] Nick` na zielono lub `[E] Nick` na czerwono oraz ramka wokół gracza w tym kolorze.
- Komendy: `/ally add|remove|list <gracz>`, `/enemy add|remove|list <gracz>`, `/allymod reload|info|gui|debug fps`.

Jeśli `relations.json` się uszkodzi, mod nie skasuje relacji: zostawia poprzednią listę, a zepsuty plik kopiuje do `relations.json.broken`.

## Sojusze z bota Discord (configi `snz-sojusz`)

Bot generuje plik JSON z listą graczy sojuszu. Mod pokazuje nad głową i na liście TAB prefiks z państwem i statusem:

- `[Polska • Król] KrolPolski` na **zielono**, gdy państwo jest w sojuszu (`"allay": true`),
- ten sam prefiks na **czerwono**, gdy państwo jest poza sojuszem (`"allay": false`),
- gracze spoza configów bez zmian.

Configów może być dowolnie wiele. Leżą w folderze `config/snz-sojusz/`, a dodaje się je z gry:

**F6 → Sojusze** (albo `/snzsojusz gui`)

| Przycisk | Co robi |
|---|---|
| **Dodaj config** | Otwiera okno wyboru pliku (można zaznaczyć kilka). Pliki można też przeciągnąć na okno gry. |
| **Z linku** | Wklejasz link do configu. Mod pobiera go i sam aktualizuje co 10 minut. |
| **Przeładuj** | Pobiera nowe wersje z linków i wczytuje wszystkie configi od nowa. |
| **Folder** | Otwiera `config/snz-sojusz/` w eksploratorze. |
| **X** przy configu | Usuwa config (i jego link). |

Gdy dodawany plik ma taką samą nazwę jak istniejący config, mod pyta: **Podmień** (nowa wersja tego samego sojuszu) albo **Dodaj jako nowy** (inny sojusz).

Linki do załączników Discorda (`cdn.discordapp.com`) wygasają po około 24 godzinach. Do automatycznej aktualizacji potrzebny jest stały link, np. wystawiony przez bota albo plik na GitHubie.

Komendy: `/snzsojusz` (podsumowanie), `/snzsojusz gui`, `/snzsojusz reload`, `/snzsojusz link <url>`, `/snzsojusz update`.

### Format pliku

```json
[
  { "nick": "KrolPolski", "allay": true,  "status": "Król",     "kingdom": "Polska" },
  { "nick": "Zastepca1",  "allay": true,  "status": "Zastępca", "kingdom": "Polska" },
  { "nick": "StaryGracz", "allay": true,  "status": "Członek",  "kingdom": "Polska" },
  { "nick": "KrolNiemiec","allay": false, "status": "Król",     "kingdom": "Niemcy" }
]
```

- `nick`: 3–16 znaków `a-z A-Z 0-9 _`, wielkość liter nie ma znaczenia.
- `allay`: `true` / `false` (klucz to `allay`, nie `ally`).
- `status`: `Król`, `Zastępca` albo `Członek`. Inna wartość jest traktowana jak `Członek`.
- `kingdom`: nazwa państwa. Kody formatowania `§` są usuwane, a nazwy dłuższe niż 24 znaki skracane.
- Plik musi być zapisany w **UTF-8**.

Błędne pojedyncze wpisy są pomijane z ostrzeżeniem w `logs/latest.log`, reszta się wczytuje. Uszkodzony plik nie kasuje graczy: zostaje jego poprzednia wersja. Gdy gracz jest w kilku configach, wygrywa config pierwszy alfabetycznie.

## Budowanie

```sh
./gradlew build          # mod: build/libs/allymod-<wersja>.jar
./gradlew test           # same testy
./gradlew runClient      # gra testowa (nick ServerGun)
./gradlew runClientTwo   # drugi klient (nick TestBuddy)
```

Każdy push na GitHuba uruchamia build (zakładka **Actions**). Gotowe pliki `.jar` dla wszystkich wersji są do pobrania jako „artifacts” przy danym przebiegu.
