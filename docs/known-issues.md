# Known issues

- **On-Demand Resources cannot re-download a changed pack.** `remove` ends access and the system frees the space later, so the integrity retry on iOS 17 and 18 reads the same files and reports `Failed(Corrupt)`. Apple-hosted packs and Play re-download.
- **Background Assets on iOS 26.4 and later:** forum reports describe downloads failing or packs marked ready with files missing. The integrity check and the single retry cover the missing-file case.
- **Play local testing** cannot simulate network errors, the Wi-Fi wait or pack updates. Use Play's internal app sharing for those.
- **PNG files in On-Demand Resources** may be re-encoded by Xcode's `COMPRESS_PNG_FILES` on device builds, which changes their hash. Turn the setting off for targets with packs, or keep images in formats Xcode does not touch.
- **Play `state()`** can, rarely, emit an older snapshot right after a newer update when the flow starts during a status change. The next update corrects it.
- **iOS error mapping** does not yet follow `NSUnderlyingErrorKey`, so an offline Background Assets failure may surface as `NotFound` or `Unknown`.
