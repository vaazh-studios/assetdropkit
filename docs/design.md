# Design (maintainers)

## What it is

A Kotlin Multiplatform library that downloads content packs on first use from the app stores
themselves: Google Play Asset Delivery on Android, Apple-hosted Background Assets on iOS 26 and
later, On-Demand Resources on iOS 17 and 18. The stores host the bytes, so an app ships small
and pays nothing to host its game art.

## Goals and non-goals

- One Kotlin API (`AssetPacks`, `PackState`, `PackSource`) over three store systems.
- Zero hosting cost: no source fetches from a server you run.
- Every Apple call in Swift; no Objective-C, and Kotlin never calls BackgroundAssets through cinterop.
- Not a CDN client, not a patcher, not a Gradle plugin (packaging is two scripts and a checked-in module in v1).

## Layers

```
app (Kotlin)         AssetPacks ── shared fetches, integrity check, stall timeout, reads on a dispatcher
                         │
                     PackSource
        ┌────────────────┼───────────────────┐
  PlayAssetPacks   AppleStoreSource    BundledAssetPacks / LocalPacks / FakePackSource
   (androidMain)     (iosMain)
                         │  ApplePackStore (plain Kotlin interfaces, exported to Swift)
app (Swift)       AssetDropAdapter.swift (template, ~90 lines)
                         │
Swift package     AppleAssetPacks ── AppleHostedSource (iOS 26+) / OnDemandResourcesSource (17, 18)
```

A Swift package cannot import the app's Kotlin framework, so the glue lives in the app. The
adapter interfaces are plain methods, not lambdas: no boxed numbers per progress tick and
nothing that trips Swift 6's Sendable checks. `AppleStoreSource` resumes each call once and
copies `NSData` out at once.

## Packs

Every file lives under `<packId>/`, because Apple merges all packs into one namespace; paths
read the same on every platform. Each pack carries `<packId>/assetdropkit.json` with a content
version and the SHA-256 of every file.

## Fetch

- One in-flight fetch per pack, shared by every caller; the download is cancelled when the last caller is cancelled.
- After the store says Ready, the pack is checked against its manifest once per version (`VerifiedMarkers`). A mismatch removes the pack, fetches once more, then reports `Failed(Corrupt)`.
- `state()` maps a store's Ready through the same check, so a UI keyed on Ready never reads an unverified pack.
- `stallTimeoutMillis` cancels a download whose state stops changing and returns `Failed(NoNetwork)`.
- `fetch` returns a state; exceptions from a source or the file system become `Failed(Unknown)`.

## Versions

Play and On-Demand Resources packs ship with the app build. An Apple-hosted pack version is
independent of builds: once approved it reaches every installed version and can update in the
background. Contents stay backward-compatible; a breaking change ships under a new pack ID.
