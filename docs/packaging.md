# Packaging packs

Every pack is a folder `packs/<packId>/` in your app repo. Every file path starts with the pack
ID, because Apple merges all packs into one namespace: `packs/postdelivery/town/sedan.glb` is
read as `readBytes("postdelivery", "town/sedan.glb")` on every platform.

## 1. The integrity file

Each pack carries `<packId>/assetdropkit.json`: a content version and the SHA-256 of every file.
AssetDropKit checks a pack against it once per version; a mismatch removes the pack, fetches it
once more, then reports `Failed(Corrupt)`.

```bash
python3 scripts/write-pack-manifest.py packs postdelivery --version 1
```

Run it after every change to the pack and raise `--version` each time. Hidden files are skipped.

## 2. Android: an asset-pack module

One module per pack. Its assets folder is the pack's single home: point Xcode and the scripts
at the same folder rather than keeping a second copy.

```
packs/postdelivery-play/
  build.gradle.kts
  src/main/assets/postdelivery/          <- the pack: assetdropkit.json, town/sedan.glb, ...
```

```kotlin
// packs/postdelivery-play/build.gradle.kts
plugins { id("com.android.asset-pack") }

assetPack {
    packName.set("postdelivery")
    dynamicDelivery { deliveryType.set("on-demand") }
}
```

List the module in the app:

```kotlin
android { assetPacks += listOf(":packs:postdelivery-play") }
```

With that layout the scripts take the assets folder as `<packsDir>`:
`write-pack-manifest.py packs/postdelivery-play/src/main/assets postdelivery --version 1`.

Debug builds can read a copy of the folder on the device with `LocalPacks`
(`PackSources.default(context, debug = true, localRoot)`). Release builds always go through Play.

## 3. iOS 17 and 18: On-Demand Resources

1. Drag the `postdelivery` pack folder into the Xcode project as a **folder reference** (blue folder), added to the app target.
2. In the File inspector, set its **On Demand Resource Tags** to `postdelivery` (the pack ID).
3. Build Settings: `ENABLE_ON_DEMAND_RESOURCES = YES`.

Xcode hosts the tags itself during Xcode runs; the App Store hosts them in release.

## 4. iOS 26 and later: Apple-hosted Background Assets

1. **Downloader extension.** File > New > Target > Background Download, type **Apple-Hosted, Managed**, deployment target iOS 26. The generated extension is enough:

    ```swift
    import BackgroundAssets
    import ExtensionFoundation
    import StoreKit

    @main
    struct DownloaderExtension: StoreDownloaderExtension {
        func shouldDownload(_ assetPack: AssetPack) -> Bool { true }
    }
    ```

2. **App Group.** Add the same App Group to the app and the extension.
3. **Info.plist** of the app and the extension:

    | Key | Value |
    | --- | --- |
    | `BAAppGroupID` | the App Group ID |
    | `BAHasManagedAssetPacks` | `YES` |
    | `BAUsesAppleHosting` | `YES` |

4. **Archive the pack** (Xcode 26):

    ```bash
    scripts/apple-hosted-pack.sh packs postdelivery   # writes packs/postdelivery.aar
    ```

5. **Upload** `postdelivery.aar` through the App Store Connect API (background asset uploads) in your release lane. Each pack version goes through review; once approved it goes live for every installed version of the app.

## Versioning rules for Apple-hosted packs

A pack version is not tied to an app build, and the system can update it in the background.
Keep a pack's contents backward-compatible; ship a breaking change under a new pack ID (Apple
allows 100 packs per app and never reuses an archived ID). Read a pack once per session and keep
using what you loaded.
