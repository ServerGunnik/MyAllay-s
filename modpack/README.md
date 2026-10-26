# Allymod Performance — modpack optymalizacyjny

Modpack na **Fabric** dla Minecrafta **26.3** (ta sama wersja co Allymod).
Wszystkie mody pobierane są z Modrinth w wersjach `release`.

## Instalacja

1. Zbuduj paczkę (albo użyj gotowej z `dist/`):
   ```bash
   python build_modpack.py
   ```
2. Zaimportuj `dist/Allymod-Performance-26.3.mrpack` w launcherze:
   - **Modrinth App**: *+ Create instance → Import from file*
   - **Prism Launcher**: *Add Instance → Import → wybierz plik*
3. Daj instancji 4–6 GB RAM (więcej nie pomaga, a może zwiększać przycięcia GC).
4. Opcjonalnie wrzuć do `mods/` zbudowany jar Allymod (`./gradlew build` → `build/libs/`).

## Co jest w środku

| Cel | Mody |
|---|---|
| **FPS** | Sodium, ImmediatelyFast, Entity Culling, More Culling, BadOptimizations, Dynamic FPS |
| **CPU / RAM / lagi** | Lithium, FerriteCore, Clumps |
| **Sieć** | Krypton |
| **Jakość grafiki** | Iris (shadery), Continuity (connected textures), LambDynamicLights |
| **Ustawienia** | Sodium Extra, Reese's Sodium Options, Mod Menu, Language Reload |
| **Biblioteki** | Fabric API, Cloth Config, Placeholder API (dociągane automatycznie) |

Opcja `--experimental` dodaje C2ME i ScalableLux (szybsze chunki i światło,
ale to wersje alpha — mogą się crashować).

## Uczciwie o pingu

Ping to czas podróży pakietów do serwera — **żaden mod po stronie klienta go
nie obniży**. Krypton zmniejsza narzut przetwarzania pakietów, a wyższe i
stabilniejsze FPS sprawiają, że gra *odczuwalnie* reaguje szybciej. Na sam
ping realnie wpływa: kabel zamiast Wi‑Fi, serwer bliżej geograficznie,
brak pobierania/streamingu w tle.

## Zalecane ustawienia w grze

- **Render distance** 8–12, **Simulation distance** 6–8
- **Max framerate**: odblokowany lub częstotliwość monitora, VSync wył.
- Sodium → *Performance*: włącz wszystkie opcje „culling”
- Shadery (Iris) tylko jeśli masz zapas FPS — lekkie paczki: Complementary
  Reimagined (profil Low/Medium), BSL, MakeUp Ultra Fast
