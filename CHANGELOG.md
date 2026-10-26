# Changelog

## 0.1.1 (2026-10-26)

- Fixed: `AssetPacks.state()` asked the store for a pack's first state on the collector's thread. On Android that is a Play binder call (a disk read with Play's local-testing service), often on the main thread; the whole state flow now runs on the `AssetPacks` dispatcher.

## 0.1.0 (2026-10-25)

First release.

- `AssetPacks`: one shared, cancellable fetch per pack; an integrity check against each pack's `assetdropkit.json` (SHA-256 per file, once per version through `VerifiedMarkers`); a store's Ready becomes Ready only after the check; an optional `stallTimeoutMillis`; reads on an injected dispatcher.
- `PackState` and `PackFailure`, the same on every store.
- Android: `PlayAssetPacks` (Play Asset Delivery on-demand packs), `registerPlayPrompts` for Play's Wi-Fi and confirmation dialogs, `BundledAssetPacks` for debug APKs, `PackSources.default(context, debug)`.
- iOS: `AppleStoreSource` and the `ApplePackStore` adapter interfaces; the `AssetDropKit` Swift package (`AppleAssetPacks`) with Apple-hosted Background Assets on iOS 26 and later and On-Demand Resources on iOS 17 and 18; `templates/ios/AssetDropAdapter.swift`.
- `LocalPacks` for tests on any platform.
- Packaging: `scripts/write-pack-manifest.py` and `scripts/apple-hosted-pack.sh`.
- `com.vocabloot:assetdropkit-test`: `FakePackSource`.
