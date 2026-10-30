"""Functional tests for the generator. Fixture format numbers are NOT version pins."""

import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from create_scale_datapack import DIMENSION_RESOURCE, InputError, generate, main


class DatapackGeneratorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.dimension = self.root / "effective_dimension.json"
        self.metadata = self.root / "pack.mcmeta"
        self.output = self.root / "generated"
        self.original = {
            "coordinate_scale": 8.0,
            "ultrawarm": True,
            "has_ceiling": True,
            "future_custom_field": {"biome": "custom:sulfur", "nested": [1, False, "Enxofre"]},
        }
        self.write_dimension(self.original)
        # Synthetic format number; not an assertion about Minecraft 26.2.
        self.metadata.write_text('{"pack":{"pack_format":1,"description":"fixture"}}\n', encoding="utf-8")

    def write_dimension(self, value):
        self.dimension.write_text(json.dumps(value), encoding="utf-8")

    def run_generate(self, **kwargs):
        return generate(self.dimension, self.metadata, self.output, **kwargs)

    def test_changes_only_scale_preserves_unknown_properties_and_originals(self):
        source_before = self.dimension.read_bytes()
        metadata_before = self.metadata.read_bytes()
        report = self.run_generate()
        output_dimension = self.output / DIMENSION_RESOURCE
        expected = self.original.copy()
        expected["coordinate_scale"] = 6.0
        self.assertEqual(json.loads(output_dimension.read_text()), expected)
        self.assertEqual(self.dimension.read_bytes(), source_before)
        self.assertEqual((self.output / "pack.mcmeta").read_bytes(), metadata_before)
        self.assertEqual(self.metadata.read_bytes(), metadata_before)
        self.assertEqual(report["source_dimension_sha256"], hashlib.sha256(source_before).hexdigest())
        self.assertEqual(report["output_dimension_sha256"], hashlib.sha256(output_dimension.read_bytes()).hexdigest())
        self.assertEqual(report["pack_mcmeta_sha256"], hashlib.sha256(metadata_before).hexdigest())
        self.assertEqual(report, json.loads((self.output / "scale-provenance.json").read_text()))
        self.assertEqual({str(p.relative_to(self.output)) for p in self.output.rglob("*") if p.is_file()},
                         {DIMENSION_RESOURCE, "pack.mcmeta", "scale-provenance.json"})

    def test_refuses_existing_output_even_empty(self):
        self.output.mkdir()
        with self.assertRaises(InputError):
            self.run_generate()
        self.assertEqual(list(self.output.iterdir()), [])

    def test_refuses_overwrite_after_first_generation(self):
        self.run_generate()
        before = (self.output / DIMENSION_RESOURCE).read_bytes()
        with self.assertRaises(InputError):
            self.run_generate(scale=7)
        self.assertEqual((self.output / DIMENSION_RESOURCE).read_bytes(), before)

    def test_supports_modern_metadata_without_guessing_version(self):
        self.metadata.write_text(json.dumps({"pack": {"min_format": [1, 0], "max_format": [1, 3],
                                                      "description": {"text": "fixture"}}}))
        self.run_generate()
        self.assertEqual((self.output / "pack.mcmeta").read_bytes(), self.metadata.read_bytes())

    def test_rejects_invalid_target_scales(self):
        for scale in [0, -1, float("nan"), float("inf"), True, "6"]:
            with self.subTest(scale=scale), self.assertRaises(InputError):
                self.run_generate(scale=scale)
            self.assertFalse(self.output.exists())

    def test_rejects_invalid_source_scales(self):
        for value in [None, False, "8", -1, 0, float("inf")]:
            self.write_dimension({"coordinate_scale": value})
            with self.subTest(value=value), self.assertRaises(InputError):
                self.run_generate()
            self.assertFalse(self.output.exists())

    def test_rejects_nonfinite_unknown_field(self):
        self.dimension.write_text('{"coordinate_scale":8,"unknown":[1e1000]}')
        with self.assertRaises(InputError):
            self.run_generate()

    def test_requires_explicit_source_scale(self):
        self.write_dimension({"custom": True})
        with self.assertRaises(InputError):
            self.run_generate()

    def test_rejects_duplicate_json_keys(self):
        self.dimension.write_text('{"coordinate_scale":8,"coordinate_scale":9}')
        with self.assertRaises(InputError):
            self.run_generate()

    def test_rejects_non_object_dimension(self):
        self.write_dimension([{"coordinate_scale": 8}])
        with self.assertRaises(InputError):
            self.run_generate()

    def test_rejects_malformed_metadata(self):
        examples = [
            {}, {"pack": {}}, {"pack": {"pack_format": True, "description": "x"}},
            {"pack": {"pack_format": -1, "description": "x"}},
            {"pack": {"pack_format": [1, 0], "description": "x"}},
            {"pack": {"min_format": [2, 0], "max_format": [1, 0], "description": "x"}},
            {"pack": {"min_format": 1, "description": "x"}},
            {"pack": {"pack_format": 1, "description": None}},
        ]
        for value in examples:
            self.metadata.write_text(json.dumps(value))
            with self.subTest(metadata=value), self.assertRaises(InputError):
                self.run_generate()
            self.assertFalse(self.output.exists())

    def test_rejects_metadata_that_can_change_other_content(self):
        for section in ["features", "filter", "overlays"]:
            self.metadata.write_text(json.dumps({"pack": {"pack_format": 1, "description": "x"}, section: {}}))
            with self.subTest(section=section), self.assertRaises(InputError):
                self.run_generate()

    def test_refuses_symlink_input(self):
        linked = self.root / "linked.json"
        linked.symlink_to(self.dimension)
        with self.assertRaises(InputError):
            generate(linked, self.metadata, self.output)

    def test_refuses_broken_symlink_output(self):
        self.output.symlink_to(self.root / "missing")
        with self.assertRaises(InputError):
            self.run_generate()

    def test_refuses_symlink_parent_output(self):
        parent = self.root / "link"
        parent.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(InputError):
            generate(self.dimension, self.metadata, parent / "generated")

    def test_requires_existing_parent(self):
        with self.assertRaises(InputError):
            generate(self.dimension, self.metadata, self.root / "missing" / "generated")
        self.assertFalse((self.root / "missing").exists())

    def test_custom_scale(self):
        report = self.run_generate(scale=4)
        self.assertEqual(json.loads((self.output / DIMENSION_RESOURCE).read_text())["coordinate_scale"], 4.0)
        self.assertEqual(report["target_coordinate_scale"], 4.0)

    def test_cli_failure_preserves_output_absence(self):
        result = main(["--dimension-type", str(self.dimension), "--pack-mcmeta", str(self.metadata),
                       "--output", str(self.output), "--scale", "nan"])
        self.assertEqual(result, 1)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
