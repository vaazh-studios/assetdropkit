# Stability and versions

## Versioning

Semantic versioning from 1.0. Before 1.0, a minor version may change the API; the changelog says
so in its first line and the binary API dump in `assetdropkit/api` shows the exact diff.

## Requirements

| | Minimum | Built and tested with |
|---|---|---|
| Kotlin | 2.3 | 2.3.20 |
| Android Gradle Plugin | 9.0 (KMP `android` library block) | 9.2.1 |
| Android | minSdk 24, compileSdk 36 | Android 16 emulator |
| iOS | 17 | iOS 26 simulator |
| Xcode / Swift | 26 / 6.0 | 26.6 / 6.3 |
| Coroutines / Serialization / kotlinx-io | 1.10 / 1.8 / 0.9 | 1.10.2 / 1.8.1 / 0.9.1 |
| Play asset-delivery-ktx | 2.3 | 2.3.0 |

## Platform policies

- On-Demand Resources is deprecated by Apple with no removal date; iOS 26 and later already use Apple-hosted packs.
- Apple-hosted pack versions go live for every installed app version once approved. Keep contents backward-compatible and ship breaking changes under a new pack ID; Apple allows 100 packs per app and never reuses an archived ID.

## Migration guides

There have been no breaking releases. From the first one on, each major version gets a guide here.
