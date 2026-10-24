# Module AssetDropKit

Asset packs downloaded on first use from the stores: Google Play Asset Delivery on Android,
Apple-hosted Background Assets on iOS 26+ and On-Demand Resources on iOS 17 and 18.

# Package com.vocabloot.assetdropkit

`AssetPacks` is the entry point: it shares one fetch per pack, verifies each pack against its
`assetdropkit.json` manifest and reads files through a `PackSource` (`PlayAssetPacks` on Android,
`AppleStoreSource` on iOS, `LocalPacks` in debug builds). Start with the README quickstart.
