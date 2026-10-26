"""Buduje modpack optymalizacyjny (.mrpack) dla Minecrafta na Fabricu.

Skrypt pyta API Modrinth o najnowsze wersje modow zgodne z wersja gry,
dociaga wymagane zaleznosci i zapisuje gotowy plik .mrpack, ktory mozna
zaimportowac w Modrinth App, Prism Launcher, ATLauncher itp.

Uzycie:
    python build_modpack.py                  # wersja gry z ../gradle.properties
    python build_modpack.py --mc 26.3 --stable-only
"""

import argparse
import json
import pathlib
import urllib.parse
import urllib.request
import zipfile

API = "https://api.modrinth.com/v2"
USER_AGENT = "ServerGunnik/allymod-modpack (github.com/ServerGunnik)"
HERE = pathlib.Path(__file__).resolve().parent

# slug -> krotki opis (do README / logu)
MODS = {
    # --- FPS / renderowanie ---
    "sodium": "nowy silnik renderowania, glowne zrodlo FPS",
    "immediatelyfast": "szybsze rysowanie HUD, tekstu, map, encji",
    "entityculling": "nie renderuje encji i block entities zaslonietych scianami",
    "moreculling": "dodatkowe culling lisci, blokow i ramek",
    "badoptimizations": "mikrooptymalizacje swiatla, nieba, lightmapy",
    "dynamic-fps": "obniza FPS gdy gra jest w tle / zminimalizowana",
    # --- CPU / tick / RAM ---
    "lithium": "optymalizacja fizyki, AI mobow, ticku swiata",
    "ferrite-core": "mniejsze zuzycie RAM",
    "clumps": "laczy kule XP w jedna encje (mniej lagow)",
    # --- siec / ping ---
    "krypton": "zoptymalizowany stos sieciowy (mniej narzutu na pakiety)",
    # --- jakosc grafiki ---
    "iris": "shadery (wsparcie OptiFine shaderpackow) na Sodium",
    "continuity": "laczone tekstury (connected textures) jak w OptiFine",
    "lambdynamiclights": "dynamiczne swiatlo z pochodni w reku",
    # --- ustawienia / wygoda ---
    "sodium-extra": "dodatkowe opcje grafiki (mgla, czasteczki, animacje)",
    "reeses-sodium-options": "wygodniejsze menu ustawien Sodium",
    "modmenu": "lista modow i dostep do ich konfiguracji",
    "language-reload": "szybsza zmiana jezyka",
    "fabric-api": "biblioteka wymagana przez wiekszosc modow",
}

# Mody alpha - szybsze generowanie chunkow / swiatlo, ale mniej stabilne.
EXPERIMENTAL = {
    "c2me-fabric": "wielowatkowe generowanie i ladowanie chunkow",
    "scalablelux": "szybszy silnik swiatla",
}


def api_get(path, **params):
    query = urllib.parse.urlencode({k: json.dumps(v) for k, v in params.items()})
    url = f"{API}{path}" + (f"?{query}" if query else "")
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def pick_version(project, mc, stable_only):
    versions = api_get(f"/project/{project}/version", loaders=["fabric"], game_versions=[mc])
    if stable_only:
        versions = [v for v in versions if v["version_type"] == "release"]
    # API zwraca od najnowszej; preferuj release, potem beta, potem alpha
    order = {"release": 0, "beta": 1, "alpha": 2}
    versions.sort(key=lambda v: order[v["version_type"]])
    return versions[0] if versions else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mc", help="wersja Minecrafta (domyslnie z gradle.properties)")
    parser.add_argument("--stable-only", action="store_true", help="tylko wersje release")
    parser.add_argument("--experimental", action="store_true", help="dodaj C2ME i ScalableLux")
    parser.add_argument("--out", default=str(HERE / "dist"))
    args = parser.parse_args()

    props = dict(
        line.split("=", 1)
        for line in (HERE.parent / "gradle.properties").read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    mc = args.mc or props["minecraft_version"]
    loader = props["loader_version"]

    wanted = dict(MODS)
    if args.experimental:
        wanted.update(EXPERIMENTAL)

    files, seen, missing = [], set(), []
    queue = list(wanted)
    while queue:
        slug = queue.pop(0)
        version = pick_version(slug, mc, args.stable_only)
        if version is None:
            missing.append(slug)
            continue
        if version["project_id"] in seen:
            continue
        seen.add(version["project_id"])

        primary = next((f for f in version["files"] if f["primary"]), version["files"][0])
        files.append({
            "path": f"mods/{primary['filename']}",
            "hashes": {"sha1": primary["hashes"]["sha1"], "sha512": primary["hashes"]["sha512"]},
            "env": {"client": "required", "server": "unsupported"},
            "downloads": [primary["url"]],
            "fileSize": primary["size"],
        })
        print(f"  [{version['version_type']:7}] {primary['filename']}")

        for dep in version["dependencies"]:
            if dep["dependency_type"] == "required" and dep["project_id"] not in seen:
                queue.append(dep["project_id"])

    if missing:
        print(f"\nBrak wersji dla MC {mc}: {', '.join(missing)}")

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": f"{props['version']}+{mc}",
        "name": f"Allymod Performance ({mc})",
        "summary": "Optymalizacja FPS, RAM i sieci + lepsza grafika (Sodium, Iris, Lithium, Krypton...)",
        "files": sorted(files, key=lambda f: f["path"]),
        "dependencies": {"minecraft": mc, "fabric-loader": loader},
    }

    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (HERE / "modrinth.index.json").write_text(json.dumps(index, indent=2) + "\n")
    pack = out / f"Allymod-Performance-{mc}.mrpack"
    with zipfile.ZipFile(pack, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("modrinth.index.json", json.dumps(index, indent=2))
        for f in (HERE / "overrides").rglob("*"):
            if f.is_file():
                zf.write(f, f.relative_to(HERE).as_posix())
    print(f"\nZapisano {pack} ({len(files)} modow)")


if __name__ == "__main__":
    main()
