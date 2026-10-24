# Contributing

Thanks for looking. AssetDropKit is small on purpose; the best contributions keep it that way.

**Bugs.** Open an issue with the platform, the store source (Play, Apple-hosted or On-Demand Resources), the `PackState` you got, and the smallest snippet that reproduces it.

**Changes.** Open an issue first for anything that touches the public API or the pack layout. For fixes, a pull request with a test is enough:

```bash
./gradlew :assetdropkit:testAndroidHostTest :assetdropkit:iosSimulatorArm64Test :assetdropkit:apiCheck
```

The Swift package has its own tests: run `xcodebuild test -scheme AssetDropKit` against an iOS simulator.

`apiCheck` fails when the public API changed; run `./gradlew :assetdropkit:apiDump` and commit the diff when the change is intended.

**Style.** Explicit API mode, KDoc on every public declaration, Swift for every Apple call (no Objective-C), no new dependencies without an issue explaining why.

**Store verification.** Store delivery can only be checked with an internal test track or TestFlight build; say in the pull request what you ran.
