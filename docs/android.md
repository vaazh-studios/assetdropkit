# Android

## Source

```kotlin
val packs = AssetPacks(source = PlayAssetPacks(context))
```

`PackSources.default(context, debug = BuildConfig.DEBUG)` picks `BundledAssetPacks` in debug
builds and Play otherwise. An APK installed with `installDebug` has no asset packs, so debug
builds carry the pack as plain assets:

```kotlin
// app/build.gradle.kts
android.sourceSets["debug"].assets.srcDir("../packs/postdelivery-play/src/main/assets")
```

A release build never reads bundled files: a sideloaded release gets `NeedsPlayConfirmation`
(Play can fix it) or `Failed(StoreUnavailable)`, never a silent fallback.

## Play's prompts

Two states need the user:

- `WaitingForWifi`: a large pack on mobile data.
- `NeedsPlayConfirmation`: Play does not recognise this install (a sideload). Accepting reinstalls the app from Play.

Register the prompts in `onCreate`, before the activity starts, and show them when `fetch` returns
either state; fetch again after the user answers.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prompts = packs.registerPlayPrompts(this)
        // later: if (state is WaitingForWifi || state is NeedsPlayConfirmation) prompts.show()
    }
}
```

## States

| Play status | PackState |
| --- | --- |
| `NOT_INSTALLED` | `NotDownloaded` |
| `PENDING` | `Pending` |
| `DOWNLOADING` | `Downloading(bytes, totalBytes)` |
| `TRANSFERRING` | `Transferring` |
| `COMPLETED` | `Ready` |
| `WAITING_FOR_WIFI` | `WaitingForWifi` |
| `REQUIRES_USER_CONFIRMATION` | `NeedsPlayConfirmation` |
| `CANCELED` | `Failed(Cancelled)` |
| `FAILED` with `NETWORK_ERROR` | `Failed(NoNetwork)` |
| `FAILED` with `INSUFFICIENT_STORAGE` | `Failed(InsufficientStorage)` |
| `FAILED` with `PACK_UNAVAILABLE`, `DOWNLOAD_NOT_FOUND` | `Failed(NotFound)` |
| `FAILED` with `APP_UNAVAILABLE`, `APP_NOT_OWNED`, `API_NOT_AVAILABLE`, `ACCESS_DENIED`, `UNRECOGNIZED_INSTALLATION` | `Failed(StoreUnavailable)` |
| anything else | `Failed(Unknown)` |
