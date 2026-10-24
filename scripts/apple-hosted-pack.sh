#!/bin/sh
# Builds the Apple-hosted archive for one pack: writes Manifest.json (on-demand, the whole
# <packId>/ folder, iOS) and runs `xcrun ba-package` from <packsDir>, so every file keeps its
# <packId>/ prefix. Needs Xcode 26. Upload <packId>.aar with the App Store Connect API.
#
# Usage: scripts/apple-hosted-pack.sh <packsDir> <packId>
set -eu

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <packsDir> <packId>" >&2
  exit 64
fi
packs_dir=$1
pack_id=$2

cd "$packs_dir"
if [ ! -d "$pack_id" ]; then
  echo "no pack folder at $packs_dir/$pack_id" >&2
  exit 66
fi
if [ ! -f "$pack_id/assetdropkit.json" ]; then
  echo "$pack_id has no assetdropkit.json; run scripts/write-pack-manifest.py first" >&2
  exit 65
fi

cat > Manifest.json <<JSON
{
  "assetPackID": "$pack_id",
  "downloadPolicy": { "onDemand": {} },
  "fileSelectors": [ { "directory": "$pack_id" } ],
  "platforms": [ "iOS" ]
}
JSON

xcrun ba-package package Manifest.json --output-path "$pack_id.aar"
rm Manifest.json
echo "wrote $packs_dir/$pack_id.aar"
