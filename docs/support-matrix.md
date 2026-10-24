# Support matrix

| | Play Asset Delivery | Apple-hosted Background Assets | On-Demand Resources |
|---|---|---|---|
| Platform | Android, minSdk 24 | iOS 26 and later | iOS 17 and 18 (works on 26 too) |
| Source | `PlayAssetPacks` | `AppleStoreSource` + `AppleAssetPacks` | `AppleStoreSource` + `AppleAssetPacks(onDemandResourcesOnly = true)` on 26 |
| Hosted by | Google Play | App Store | App Store |
| Pack ships with | the app bundle | its own upload and review, independent of builds | the app build |
| Packaging | one asset-pack module per pack, `on-demand` | `scripts/apple-hosted-pack.sh` then the App Store Connect API | a folder reference tagged with the pack ID |
| Extra app setup | `registerPlayPrompts` in `onCreate` | downloader extension, App Group, three Info.plist keys | `ENABLE_ON_DEMAND_RESOURCES = YES` |
| User prompts | Wi-Fi for large packs, Play confirmation for unrecognised installs | none | none |
| Remove | Play deletes the files | the system deletes the files | ends access; the system frees space later |
| Checked with | Play local testing (`bundletool --local-testing`), Pixel 9 Pro XL emulator, 2026-10-24 | unit tests against the Background Assets API | iPhone simulator, iOS 26, 2026-10-24 |

Debug and test sources:

| | `BundledAssetPacks` | `LocalPacks` | `FakePackSource` |
|---|---|---|---|
| For | Android debug APKs (`installDebug`) | tests, any platform | unit tests (`assetdropkit-test`) |
| Reads | `assets/<packId>/` inside the APK | `root/<packId>/` on disk | files you give it |
| Download | none, always Ready | optional delay and failure rate | scripted states |

`PackSources.default(context, debug)` picks `BundledAssetPacks` in debug and Play in release. A release build never reads bundled or local files.

## States by source

| PackState | Play | Apple-hosted | On-Demand Resources |
|---|---|---|---|
| `NotDownloaded` | `NOT_INSTALLED` | not downloaded | `conditionallyBeginAccessingResources` false |
| `Pending` | `PENDING` | began, paused | request started |
| `Downloading` | `DOWNLOADING` (bytes) | downloading (progress) | request progress |
| `Transferring` | `TRANSFERRING` | | |
| `WaitingForWifi` | `WAITING_FOR_WIFI` | | |
| `NeedsPlayConfirmation` | `REQUIRES_USER_CONFIRMATION` | | |
| `Ready` | `COMPLETED`, then the integrity check | finished, then the integrity check | access granted, then the integrity check |
| `Failed` | see [Android setup](setup-android.md#states) | mapped from the system error | mapped from the system error |
