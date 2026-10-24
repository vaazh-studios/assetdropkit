# FAQ

**Why not Firebase Storage or a CDN?** Egress. Every first play downloads the pack again on a
new device, and you pay per gigabyte. The stores already host and deliver your app for free;
AssetDropKit uses their delivery for the extra content too.

**Does it work without Google Play?** No. A release build reads packs only from Play. A
sideloaded build gets `NeedsPlayConfirmation` (accepting reinstalls the app from Play) or
`Failed(StoreUnavailable)`. Debug builds read packs bundled in the APK.

**Why is there Swift code in my app?** A Swift package cannot import your Kotlin framework,
and the Apple APIs are Swift. The adapter template is the short piece in between; copy it and
change one import.

**Can I use only Android, or only iOS?** Yes. The Kotlin core and each source are independent;
skip the Swift package if you only ship Android.

**Do I have to use all three Apple and Google systems?** No. On iOS you can stay on On-Demand
Resources everywhere with `AppleAssetPacks(onDemandResourcesOnly = true)`.

**How do I show progress?** Collect `packs.state(packId)`: `Downloading(bytes, totalBytes)`
gives a fraction when the store knows the total.

**What happens offline?** The store reports `Failed(NoNetwork)`, or the download sits in
`Pending`. Pass `stallTimeoutMillis` to turn a stall into `Failed(NoNetwork)`, show a fallback,
and fetch again later.

**Why the integrity check?** Stores occasionally report a pack ready with files missing or
changed. A pack that fails the check is removed, fetched once more, then reported as
`Failed(Corrupt)`, so you never load a half pack.

**How big can a pack be?** As big as each store allows; see Google's and Apple's documentation.
AssetDropKit reads a file into memory at once, so keep single files to tens of megabytes.
