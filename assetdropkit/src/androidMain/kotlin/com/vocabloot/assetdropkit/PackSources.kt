package com.vocabloot.assetdropkit

import android.content.Context

/** Picks the Android source: packs bundled in the APK in debug builds, Play otherwise. */
public object PackSources {

    /** [BundledAssetPacks] when [debug], [PlayAssetPacks] otherwise; a release build never reads bundled files. */
    public fun default(context: Context, debug: Boolean): PackSource =
        choose(debug = debug, bundled = { BundledAssetPacks(context) }, play = { PlayAssetPacks(context) })

    internal fun choose(debug: Boolean, bundled: () -> PackSource, play: () -> PackSource): PackSource = if (debug) bundled() else play()
}
