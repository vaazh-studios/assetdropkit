# Samples

One screen on both platforms: the `demo` pack's state, a button to fetch it, and its image once
it is Ready. The pack lives in `demo-pack/src/main/assets/demo/` (an image, a text file and
`assetdropkit.json`).

## Android (Play Asset Delivery)

```bash
./gradlew :sample:androidApp:installLocalTesting
```

Installs through bundletool's `--local-testing`, so Play's library serves the on-demand pack from
the device as if from Play. For real network behaviour (offline, Wi-Fi wait) upload the bundle to
Play's internal app sharing.

## iOS (On-Demand Resources, Apple-hosted)

```bash
cd sample/iosApp && xcodegen generate && open iosApp.xcodeproj
```

Runs use On-Demand Resources (the `demo` folder is tagged `demo`). Launch with `-appleHosted`
on iOS 26 to fetch the Apple-hosted pack instead: build `demo.aar` with
`scripts/apple-hosted-pack.sh sample/demo-pack/src/main/assets demo` and serve it with
`xcrun ba-serve`, or test through TestFlight. `AssetDropAdapter.swift` is
`templates/ios/AssetDropAdapter.swift` with the framework renamed to `ComposeApp`.
