package com.vocabloot.assetdropkit

import android.content.Context
import kotlinx.io.files.Path

/** Picks the Android source: [LocalPacks] in debug builds, Play otherwise. */
public object PackSources {

    /** Play in release builds; [localRoot] (a copy of `packs/` on the device) only when [debug]. */
    public fun default(context: Context, debug: Boolean, localRoot: Path): PackSource =
        choose(debug = debug, local = { LocalPacks(root = localRoot) }, play = { PlayAssetPacks(context) })

    internal fun choose(debug: Boolean, local: () -> PackSource, play: () -> PackSource): PackSource = if (debug) local() else play()
}
