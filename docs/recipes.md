# Recipes

## Progress and an offline fallback in Compose

```kotlin
@Composable
fun GameIntro(packs: AssetPacks) {
    val state by remember { packs.state("postdelivery") }.collectAsState(initial = null)
    LaunchedEffect(Unit) { packs.fetch("postdelivery") }
    when (val s = state) {
        is PackState.Downloading -> ProgressLine(s.bytes, s.totalBytes)
        is PackState.Failed -> if (s.reason == PackFailure.NoNetwork) PlayWithBuiltInArt()
        PackState.Ready -> PlayWithPackArt()
        else -> Unit
    }
}
```

Construct `AssetPacks(source, stallTimeoutMillis = 20_000)` so an offline first play falls back
instead of waiting.

## Hash once per install, not once per launch

Back `VerifiedMarkers` with persistent settings (multiplatform-settings shown):

```kotlin
class SettingsMarkers(private val settings: Settings) : VerifiedMarkers {
    override fun isVerified(packId: String, version: Int) = settings.getInt("adk.$packId", -1) == version
    override fun markVerified(packId: String, version: Int) = settings.putInt("adk.$packId", version)
    override fun clear(packId: String) = settings.remove("adk.$packId")
}
val packs = AssetPacks(source = source, markers = SettingsMarkers(settings))
```

## Reading a model and a texture

```kotlin
val glb = packs.readBytes("postdelivery", "town/sedan.glb") ?: return fallback()
val png = packs.readBytes("postdelivery", "town/colormap.png")?.decodeToImageBitmap()
```

`readBytes` runs on the dispatcher you gave `AssetPacks`; decode on a background dispatcher too.

## One pack per game

Give each game its own pack ID (`postdelivery`, `wordmaze`), so a player downloads only the
games they open, and remove a game's pack when they have not played it in a while.

## Unit tests

```kotlin
val fake = FakePackSource().apply {
    pack(packId = "postdelivery", version = 1, files = mapOf("town/sedan.glb" to glbBytes))
    script("postdelivery", PackState.NotDownloaded, PackState.Downloading(1, 2), PackState.Ready)
}
```

See [Testing](testing.md) for scripting failures and cancellations.
