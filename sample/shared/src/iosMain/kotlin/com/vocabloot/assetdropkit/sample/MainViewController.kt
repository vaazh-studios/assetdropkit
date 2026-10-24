package com.vocabloot.assetdropkit.sample

import androidx.compose.ui.window.ComposeUIViewController
import com.vocabloot.assetdropkit.ApplePackStore
import com.vocabloot.assetdropkit.AppleStoreSource
import com.vocabloot.assetdropkit.AssetPacks
import platform.UIKit.UIViewController

/** [store] is the app's Swift AssetDropAdapter. */
fun MainViewController(store: ApplePackStore): UIViewController {
    val packs = AssetPacks(source = AppleStoreSource(store = store))
    return ComposeUIViewController { App(packs = packs) }
}
