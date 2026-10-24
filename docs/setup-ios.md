# iOS

All Apple calls live in the `AssetDropKit` Swift package. It picks Apple-hosted Background
Assets on iOS 26 and later and On-Demand Resources on iOS 17 and 18. No Objective-C, and Kotlin
never calls BackgroundAssets through cinterop.

## 1. Export the library from your Kotlin framework

```kotlin
iosTarget.binaries.framework {
    export("com.vocabloot:assetdropkit:0.1.0")
}
```

Swift then sees `ApplePackStore`, `ApplePackCallbacks`, `PackCancel` and `PackFailure`
(`.noNetwork`, `.insufficientStorage`, ...).

## 2. Add the adapter

Copy [`templates/ios/AssetDropAdapter.swift`](https://github.com/vaazh-studios/assetdropkit/blob/main/templates/ios/AssetDropAdapter.swift)
into your app target and replace `import Shared` with your framework's module name. A Swift
package cannot import your Kotlin framework, so this short file lives in the app.

## 3. Hand it to Kotlin

```swift
let store = AssetDropAdapter()                                   // App Store and TestFlight
let store = AssetDropAdapter(packs: AppleAssetPacks(onDemandResourcesOnly: true)) // Xcode runs on iOS 26
```

```kotlin
// iosMain
fun makePacks(store: ApplePackStore) = AssetPacks(source = AppleStoreSource(store = store))
```

`AppleStoreSource` resumes each call once (a late second callback is ignored), cancels the Swift
task when your coroutine is cancelled, and copies file bytes out of `NSData` straight away.

## 4. Package the packs

See [Packaging](packaging.md): the ODR folder reference and tag, the downloader extension, the App
Group and the three Info.plist keys.

## On-Demand Resources details

One `NSBundleResourceRequest` per fetched pack is held for the process's life at urgent loading
priority, with the preservation priority raised so the system keeps the pack. `remove` ends
access and lowers it; the system frees the space later.
