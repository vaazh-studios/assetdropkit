# Limits

- **On-Demand Resources is deprecated.** It has no removal date and still works on iOS 17 and 18; iOS 26 and later already use Apple-hosted packs. Watch each Xcode release.
- **Apple-hosted pack versions are not tied to app builds.** Once approved, a version goes live for every installed version of the app, and the system can update it in the background mid-session. Keep contents backward-compatible, ship breaking changes under a new pack ID, and read a pack once per session.
- **Apple allows 100 packs per app** and never reuses an archived pack ID. Total size up to 200 GB.
- **Play on-demand packs** ship with the app bundle and change only with an app update.
- **No Gradle plugin yet.** Packaging is two scripts and a checked-in asset-pack module (see [Packaging](packaging.md)).
- **Android API checks.** The binary-compatibility validator covers the Kotlin/Native API; the Android-only API (`PlayAssetPacks`, `PackSources`, `PlayPrompts`) is not under `apiCheck` yet.
- **On-Demand Resources cannot re-download a changed pack.** `remove` only ends access; the system frees the space later. So when a pack fails the integrity check on iOS 17 and 18, the retry reads the same files and the fetch reports `Failed(Corrupt)`. Apple-hosted packs and Play re-download.
- **Stalls are bounded only if you ask.** `AssetPacks(stallTimeoutMillis = ...)` cancels a download whose state has not changed for that long and returns `Failed(NoNetwork)`; the default (0) waits for the store.
