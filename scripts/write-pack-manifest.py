#!/usr/bin/env python3
"""Writes <packsDir>/<packId>/assetdropkit.json: the pack's content version and the SHA-256 of
every file, keyed by its path inside the pack. AssetDropKit checks a pack against it once per
version. Run it whenever a pack's files change, and raise --version with every change.

Usage: write-pack-manifest.py <packsDir> <packId> --version <n>
"""
import argparse
import hashlib
import json
import pathlib
import sys

MANIFEST = "assetdropkit.json"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_manifest(packs_dir, pack_id, version):
    pack = pathlib.Path(packs_dir) / pack_id
    if not pack.is_dir():
        sys.exit(f"no pack folder at {pack}")
    files = {}
    for path in sorted(pack.rglob("*")):
        relative = path.relative_to(pack).as_posix()
        hidden = any(part.startswith(".") for part in path.relative_to(pack).parts)
        if path.is_file() and not hidden and relative != MANIFEST:
            files[relative] = sha256(path)
    manifest = {"packId": pack_id, "version": version, "files": files}
    (pack / MANIFEST).write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("packs_dir")
    parser.add_argument("pack_id")
    parser.add_argument("--version", type=int, required=True)
    args = parser.parse_args()
    manifest = write_manifest(args.packs_dir, args.pack_id, args.version)
    print(f"{args.pack_id} v{args.version}: {len(manifest['files'])} files")


if __name__ == "__main__":
    main()
