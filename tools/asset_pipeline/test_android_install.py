import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from tools.asset_pipeline import android_install


class AndroidInstallTests(unittest.TestCase):
    def test_converts_maps_serially_and_propagates_cancellation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            game = root / "disc"
            (game / "data/big").mkdir(parents=True)
            (game / "data/content").mkdir(parents=True)
            (game / "data/content/createacharacter.big").write_bytes(b"fixture")
            for path in ("default.xex", "data/big/miscload.big", "data/big/miscboot.big", "data/big/db.big"):
                (game / path).write_bytes(b"fixture")
            for name in ("worldDIST_University.big", "worldDIST_StartPark.big"):
                (game / "data/content" / name).write_bytes(b"map")

            events = []
            exports = Mock()
            exports.core.return_value = {"fixture": True}
            convert_map = Mock(side_effect=lambda archive, work, maps, stage, exe, log, report:
                               (events.append(archive.name),
                                (maps / (archive.stem + ".skate")).write_bytes(b"map-output"),
                                {"name": archive.stem.removeprefix("worldDIST_"),
                                 "path": "maps/" + archive.stem.removeprefix("worldDIST_") + ".skate",
                                 "sha256": "a" * 64})[2])
            calls = []
            def check_cancelled():
                calls.append("check")
                if len(calls) == 11:
                    raise InterruptedError("cancelled")

            with patch.object(android_install, "_load_converters", return_value=(exports, convert_map)), \
                 patch("subprocess.Popen", side_effect=AssertionError("subprocess must not run")), \
                 patch("urllib.request.urlopen", side_effect=AssertionError("network must not run")):
                with self.assertRaises(InterruptedError):
                    android_install.convert_owned(game, root / "stage", root / "work", lambda *_: None,
                                                  check_cancelled)
            self.assertEqual(events, ["worldDIST_University.big"])
            self.assertEqual(exports.core.call_count, 1)

    def test_builds_map_catalog_and_default_without_game_executable(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            game = root / "disc"
            (game / "data/big").mkdir(parents=True)
            (game / "data/content").mkdir(parents=True)
            (game / "data/content/createacharacter.big").write_bytes(b"fixture")
            for path in ("default.xex", "data/big/miscload.big", "data/big/miscboot.big", "data/big/db.big"):
                (game / path).write_bytes(b"fixture")
            (game / "data/content/worldDIST_University.big").write_bytes(b"map")
            exports = Mock()
            exports.core.return_value = {"fixture": True}

            def map_converter(archive, work, maps, stage, game_exe, log, report):
                self.assertIsNone(game_exe)
                output = maps / "University.skate"
                output.write_bytes(b"playable-map")
                return {"name": "University", "path": "maps/University.skate", "sha256": "b" * 64}

            with patch.object(android_install, "_load_converters", return_value=(exports, map_converter)):
                stage = root / "stage"
                android_install.convert_owned(game, stage, root / "work", lambda *_: None, lambda: None)
            self.assertEqual((stage / "settings/default-map.json").read_text(), '"maps/University.skate"')
            self.assertIn('"University"', (stage / "maps.json").read_text())
            self.assertEqual(exports.core.call_count, 1)


    def test_exports_full_parks_and_binds_them_to_converted_base_maps(self):
        import hashlib
        import json
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);stage=root/"stage";work=root/"work"
            (stage/"maps").mkdir(parents=True)
            base=stage/"maps/MegaPark.skate";base.write_bytes(b"base")
            converted={"collections": []}
            def export(game,district,destination,**kwargs):
                self.assertIs(kwargs["collections"],converted)
                destination.write_bytes(b"supplement")
                return SimpleNamespace(package_sha256=hashlib.sha256(b"supplement").hexdigest())
            with patch("tools.asset_pipeline.park_export.export_full_park",side_effect=export) as exporter, \
                 patch("tools.asset_pipeline.park_export.ParkCatalog"):
                android_install.prepare_full_parks(root/"disc",stage,work,converted,[{"name":"MegaPark"}],lambda *_:None,lambda:None)
            exporter.assert_called_once()
            catalog=json.loads((stage/"assets/private/park-layouts/catalog.json").read_text())
            entry=catalog["maps"]["MegaPark"]
            self.assertEqual(entry["file"],"MegaPark.skate")
            self.assertEqual(entry["base_fingerprint"],android_install.fingerprint(base))
            self.assertEqual(entry["supplement_fingerprint"],android_install.fingerprint(stage/"assets/private/park-layouts/MegaPark.skate"))
            self.assertEqual(base.read_bytes(),b"base")

    def test_full_park_conversion_failure_is_not_silently_published(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            with patch("tools.asset_pipeline.park_export.export_full_park",side_effect=ValueError("missing full park")), \
                 patch("tools.asset_pipeline.park_export.ParkCatalog"):
                with self.assertRaisesRegex(ValueError,"missing full park"):
                    android_install.prepare_full_parks(root/"disc",root/"stage",root/"work",{},[{"name":"MegaPark"}],lambda *_:None,lambda:None)


if __name__ == "__main__":
    unittest.main()
