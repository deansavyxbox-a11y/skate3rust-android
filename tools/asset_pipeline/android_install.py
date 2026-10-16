"""In-process owned-disc asset conversion entry point for Android."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Callable


def _load_converters():
    from . import asset_exports
    from . import install
    return asset_exports, install.convert_map


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fingerprint(path: Path) -> int:
    """The engine's FNV-1a identity binding, separate from SHA-256 integrity."""
    value = 14695981039346656037
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            for byte in chunk:
                value = ((value ^ byte) * 1099511628211) & 0xffffffffffffffff
    return value


def prepare_full_parks(game_root, staging, work, converted, catalog, observer, check_cancelled):
    names = [entry["name"] for entry in catalog if entry["name"] in
             {"MegaPark", "IndustrialSkatePark", "StartPark", "DownTownSkatePark"}]
    if not names:
        return
    from .park_export import export_full_park, ParkCatalog
    destination = staging / "assets/private/park-layouts"
    destination.mkdir(parents=True, exist_ok=True)
    work.mkdir(parents=True, exist_ok=True)
    resources = ParkCatalog(game_root, work / "park-resource-cache")
    maps = {}
    for name in names:
        check_cancelled()
        observer("Preparing full skate park layout: " + name)
        output = destination / (name + ".skate")
        receipt = export_full_park(game_root, name, output, catalog=resources, collections=converted)
        check_cancelled()
        if _sha256(output) != receipt.package_sha256:
            raise RuntimeError("Converted park layout did not match its receipt: " + name)
        maps[name] = {"file": output.name,
                      "base_fingerprint": fingerprint(staging / "maps" / (name + ".skate")),
                      "supplement_fingerprint": fingerprint(output)}
    (destination / "catalog.json").write_text(json.dumps({"version": 1, "maps": maps}, indent=2), encoding="utf-8")


def convert_owned(game_root: Path, staging: Path, work: Path,
                  observer: Callable[[str], None], check_cancelled: Callable[[], None],
                  map_names: list[str] | None = None) -> dict:
    """Convert one extracted Xbox 360 disc into an unpublished install tree.

    All map work is serial and uses the existing pure-Python conversion functions.
    Final runtime validation and publication remain the caller's responsibility.
    """
    game_root, staging, work = map(Path, (game_root, staging, work))
    required = ("default.xex", "data/big/miscload.big", "data/big/miscboot.big",
                "data/big/db.big", "data/content/createacharacter.big")
    for relative in required:
        check_cancelled()
        if not (game_root / relative).is_file():
            raise RuntimeError("This is not a supported Skate 3 disc: missing " + relative)

    archives = sorted((game_root / "data/content").glob("worldDIST_*.big"),
                      key=lambda path: (path.stem != "worldDIST_University", path.name.lower()))
    if map_names is not None:
        requested = set(map_names)
        archives = [path for path in archives if path.stem.removeprefix("worldDIST_") in requested]
    if not archives:
        raise RuntimeError("No supported worldDIST_*.big maps were found on this disc")

    stage_assets = staging / "assets"
    stage_maps = staging / "maps"
    private = stage_assets / "private"
    stage_assets.mkdir(parents=True, exist_ok=True)
    stage_maps.mkdir(parents=True, exist_ok=True)
    work.mkdir(parents=True, exist_ok=True)
    (staging / "settings").mkdir(parents=True, exist_ok=True)
    exports, convert_map = _load_converters()

    check_cancelled()
    observer("Preparing gameplay assets")
    converted = exports.core(game_root, staging, work, observer, None)
    check_cancelled()
    observer("Preparing HUD assets")
    exports.hud(game_root, staging, work, observer, None)
    check_cancelled()
    observer("Preparing skater assets")
    exports.character(game_root, staging, work, observer, None, converted)
    check_cancelled()
    observer("Preparing environment assets")
    exports.environment(game_root, staging, work, observer, None, converted)

    catalog = []
    for index, archive in enumerate(archives, 1):
        check_cancelled()
        observer(f"Converting map {index} of {len(archives)}: {archive.stem.removeprefix('worldDIST_')}")
        # The Android caller validates the result using the renderer-free Rust parser.
        # Passing None prevents the desktop game executable check/subprocess.
        with (work / (archive.stem + "-conversion.log")).open("w", encoding="utf-8") as log:
            entry = convert_map(archive, work, stage_maps, staging, None, log, observer)
        check_cancelled()
        output = stage_maps / (archive.stem.removeprefix("worldDIST_") + ".skate")
        if not output.is_file():
            output = staging / entry.get("path", "")
        if not output.is_file():
            raise RuntimeError("Map converter did not create " + archive.stem)
        entry = dict(entry)
        entry["path"] = "maps/" + output.name
        entry["sha256"] = _sha256(output)
        catalog.append(entry)

    if not any(item["name"] == "University" for item in catalog):
        raise RuntimeError("The required University map could not be prepared")
    prepare_full_parks(game_root, staging, work, converted, catalog, observer, check_cancelled)
    (staging / "maps.json").write_text(json.dumps(catalog, indent=2), encoding="utf-8")
    default_map = next((item for item in catalog if item["name"] == "University"), catalog[0])
    (staging / "settings/default-map.json").write_text(
        json.dumps(default_map["path"]), encoding="utf-8")
    check_cancelled()
    return {"source_sha256": _sha256(game_root / "default.xex"), "maps": catalog}
