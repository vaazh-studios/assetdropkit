"""Tests for write-pack-manifest.py: the integrity file the Kotlin library checks."""
import importlib.util
import json
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parent.parent / "write-pack-manifest.py"
spec = importlib.util.spec_from_file_location("write_pack_manifest", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class WritePackManifestTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.packs = pathlib.Path(self.tmp.name)
        pack = self.packs / "demo"
        (pack / "town").mkdir(parents=True)
        (pack / "a.txt").write_bytes(b"abc")
        (pack / "town" / "b.bin").write_bytes(b"")
        (pack / ".DS_Store").write_bytes(b"junk")

    def tearDown(self):
        self.tmp.cleanup()

    def manifest(self):
        return json.loads((self.packs / "demo" / "assetdropkit.json").read_text())

    def test_hashes_every_file_by_its_path_inside_the_pack(self):
        module.write_manifest(self.packs, "demo", version=3)
        manifest = self.manifest()
        self.assertEqual(manifest["packId"], "demo")
        self.assertEqual(manifest["version"], 3)
        self.assertEqual(manifest["files"]["a.txt"], "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        self.assertEqual(manifest["files"]["town/b.bin"], "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")

    def test_the_manifest_and_hidden_files_are_left_out(self):
        module.write_manifest(self.packs, "demo", version=1)
        module.write_manifest(self.packs, "demo", version=2)
        self.assertEqual(sorted(self.manifest()["files"]), ["a.txt", "town/b.bin"])

    def test_only_the_three_fields_the_library_reads(self):
        module.write_manifest(self.packs, "demo", version=1)
        self.assertEqual(sorted(self.manifest()), ["files", "packId", "version"])

    def test_a_missing_pack_is_an_error(self):
        with self.assertRaises(SystemExit):
            module.write_manifest(self.packs, "nope", version=1)


if __name__ == "__main__":
    unittest.main()
