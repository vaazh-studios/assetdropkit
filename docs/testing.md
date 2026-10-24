# Testing

## Unit tests

`com.vocabloot:assetdropkit-test` has `FakePackSource`: script the states a store reports, give it
files (`pack(...)` adds a matching `assetdropkit.json`), and count fetches, reads and cancels.

```kotlin
val fake = FakePackSource().apply {
    pack(packId = "p", version = 1, files = mapOf("a.bin" to byteArrayOf(1, 2, 3)))
    script("p", PackState.NotDownloaded, PackState.Downloading(4, 10), PackState.Ready)
}
val packs = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler))
```

A script's first state is what the store reports right away (after a relaunch, say); the rest
play on the next fetch. A script that ends unsettled hangs that fetch until it is cancelled.

## Android

- **Play local testing:** `bundletool build-apks --local-testing` then `install-apks`; the sample wraps both in `./gradlew :sample:androidApp:installLocalTesting`. Fast, but it cannot simulate network errors or the Wi-Fi wait.
- **Internal app sharing:** upload the bundle in Play Console and install from the link. This is Google's recommended route and exercises real downloads.

## iOS

- **On-Demand Resources:** Xcode hosts the tags during Xcode runs. For installs outside Xcode, set `EMBED_ASSET_PACKS_IN_PRODUCT_BUNDLE = YES` in Debug.
- **Apple-hosted:** serve the archive with `xcrun ba-serve` and point the device at it in Settings > Developer > Background Assets, then use TestFlight. There are no sandbox accounts, and external testers need each pack version reviewed.
