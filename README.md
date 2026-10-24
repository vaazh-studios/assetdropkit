# AssetDropKit

Built for [Vocabloot](https://vocabloot.com), the photo vocabulary app for iOS and Android. Part of the [Vocabloot open-source libraries](https://vocabloot.com/oss).

Kotlin Multiplatform asset packs, downloaded on first use from the app stores themselves: **Google Play Asset Delivery** on Android, **Apple-hosted Background Assets** on iOS 26 and later, **On-Demand Resources** on iOS 17 and 18. The stores host the bytes, so there is no server and no hosting bill.

![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-000000?logo=apple&logoColor=white)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

- [Why](#why) · [Quickstart](#quickstart) · [Requirements](#requirements) · [Docs](https://vaazh-studios.github.io/assetdropkit/)

## Why

Games and content packs make an app big, and most users never open most of them. Both stores will deliver extra content after install, each with its own API, states and packaging. AssetDropKit gives Kotlin one API over all three, with one pack layout and one integrity check.

## Quickstart

```kotlin
val packs = AssetPacks(source = source) // PlayAssetPacks on Android, AppleStoreSource on iOS

when (val state = packs.fetch("postdelivery")) {
    PackState.Ready -> packs.readBytes("postdelivery", "town/sedan.glb")
    is PackState.Failed -> showOfflineFallback(state.reason)
    else -> askTheUser() // WaitingForWifi or NeedsPlayConfirmation, Android only
}
```

- One store request when two callers fetch the same pack; cancelling the last caller cancels the download.
- Each pack is checked against its `assetdropkit.json` (SHA-256 per file) once per version.
- After a relaunch the first state comes from the store, never assumed.
- All Apple calls are Swift (`AssetDropKit` package); Kotlin talks to them through a short adapter in your app.

| Platform | Delivery |
| --- | --- |
| Android, minSdk 24 | Play Asset Delivery, on-demand packs |
| iOS 26 and later | Apple-hosted Background Assets |
| iOS 17 and 18 | On-Demand Resources |

Samples for both platforms live in [`sample/`](sample/README.md).

## Requirements

- Android minSdk 24.
- iOS 17.0. Apple-hosted packs need iOS 26 and a downloader extension; iOS 17 and 18 use On-Demand Resources.
- Kotlin 2.3.20, Xcode 26.

## License

Apache 2.0. See [LICENSE](LICENSE).
