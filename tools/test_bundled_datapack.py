"""Regression checks for the distributable 26.2 pack; no Minecraft server is run."""
import hashlib
import json
from pathlib import Path
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT / "datapacks/reference/26.2"
PACK = ROOT / "datapacks/survivalplus-scale-6"
ARCHIVE = ROOT / "downloads/SurvivalPlus-Nether-Scale6-MC26.2.zip"
RESOURCE = "data/minecraft/dimension_type/the_nether.json"


class BundledDatapackTest(unittest.TestCase):
    def test_only_dimension_change_is_scale(self):
        original = json.loads((REFERENCE / "the_nether.json").read_text())
        adjusted = json.loads((PACK / RESOURCE).read_text())
        self.assertEqual(original["coordinate_scale"], 8)
        self.assertEqual(adjusted["coordinate_scale"], 6)
        del original["coordinate_scale"]
        del adjusted["coordinate_scale"]
        self.assertEqual(adjusted, original)

    def test_exact_release_data_format(self):
        version = json.loads((REFERENCE / "version.json").read_text())
        self.assertEqual(version["id"], "26.2")
        self.assertTrue(version["stable"])
        fmt = [version["data_pack_version"], version["data_pack_version_minor"]]
        metadata = json.loads((PACK / "pack.mcmeta").read_text())
        vanilla = json.loads((REFERENCE / "vanilla-pack.mcmeta").read_text())
        self.assertEqual(set(metadata), {"pack"})
        self.assertEqual(metadata["pack"]["min_format"], fmt)
        self.assertEqual(metadata["pack"]["max_format"], fmt)
        for key in ("pack_format", "min_format", "max_format"):
            self.assertEqual(metadata["pack"][key], vanilla["pack"][key])

    def test_zip_has_pack_metadata_at_root_and_no_other_game_resources(self):
        with ZipFile(ARCHIVE) as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(set(archive.namelist()), {"pack.mcmeta", RESOURCE, "scale-provenance.json"})
            for name in archive.namelist():
                self.assertEqual(archive.read(name), (PACK / name).read_bytes())

    def test_checksums_match_pack_and_source(self):
        report = json.loads((PACK / "scale-provenance.json").read_text())
        self.assertEqual(report["source_dimension_sha256"], hashlib.sha256((REFERENCE / "the_nether.json").read_bytes()).hexdigest())
        self.assertEqual(report["output_dimension_sha256"], hashlib.sha256((PACK / RESOURCE).read_bytes()).hexdigest())
        self.assertEqual(report["pack_mcmeta_sha256"], hashlib.sha256((PACK / "pack.mcmeta").read_bytes()).hexdigest())
        expected = ARCHIVE.with_name(ARCHIVE.name + ".sha256").read_text().split()[0]
        self.assertEqual(expected, hashlib.sha256(ARCHIVE.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
