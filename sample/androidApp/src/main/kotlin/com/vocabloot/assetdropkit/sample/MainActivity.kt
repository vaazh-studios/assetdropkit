package com.vocabloot.assetdropkit.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vocabloot.assetdropkit.AssetPacks
import com.vocabloot.assetdropkit.PlayAssetPacks
import com.vocabloot.assetdropkit.registerPlayPrompts

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packs = AssetPacks(source = PlayAssetPacks(applicationContext))
        val prompts = packs.registerPlayPrompts(this)
        setContent { App(packs = packs, onNeedsUser = { prompts.show() }) }
    }
}
