# Android

## Source

```kotlin
val packs = AssetPacks(source = PlayAssetPacks(context))
```

`PackSources.default(context, debug = BuildConfig.DEBUG, localRoot)` picks `LocalPacks` (files on
the device, with an optional simulated delay and failure rate) in debug builds and Play
otherwise. A release build never reads local files: a sideloaded release gets
`NeedsPlayConfirmation`, not a silent fallback.

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
| `REQUIRES_USER_CONFIRMATION`, or `FAILED` with `UNRECOGNIZED_INSTALLATION` | `NeedsPlayConfirmation` |
| `CANCELED` | `Failed(Cancelled)` |
| `FAILED` with `NETWORK_ERROR` | `Failed(NoNetwork)` |
| `FAILED` with `INSUFFICIENT_STORAGE` | `Failed(InsufficientStorage)` |
| `FAILED` with `PACK_UNAVAILABLE`, `DOWNLOAD_NOT_FOUND` | `Failed(NotFound)` |
| `FAILED` with `APP_UNAVAILABLE`, `APP_NOT_OWNED`, `API_NOT_AVAILABLE`, `ACCESS_DENIED` | `Failed(StoreUnavailable)` |
| anything else | `Failed(Unknown)` |
