package com.vocabloot.assetdropkit

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.FileNotFoundException

/** The app's own `assets/`; host tests replace it. */
internal interface AppAssets {
    fun hasFolder(path: String): Boolean
    fun read(path: String): ByteArray?
}

private class ManagerAppAssets(private val assets: AssetManager) : AppAssets {
    override fun hasFolder(path: String) = !assets.list(path).isNullOrEmpty()
    override fun read(path: String): ByteArray? = try {
        assets.open(path).use { it.readBytes() }
    } catch (_: FileNotFoundException) {
        null
    }
}

/**
 * Packs shipped inside the APK as plain assets (`assets/<packId>/...`), for debug builds: an APK
 * installed with `installDebug` has no asset packs. Add the pack's folder to the debug source
 * set's assets; release builds use [PlayAssetPacks].
 */
public class BundledAssetPacks internal constructor(private val assets: AppAssets) : PackSource {

    public constructor(context: Context) : this(ManagerAppAssets(context.assets))

    private fun current(packId: String): PackState =
        if (assets.hasFolder(packId)) PackState.Ready
        else PackState.Failed(PackFailure.NotFound, "no assets/$packId in this APK; add the pack folder to the debug assets")

    override fun state(packId: String): Flow<PackState> = flowOf(current(packId))

    override suspend fun fetch(packId: String): PackState = current(packId)

    override suspend fun readBytes(packId: String, path: String): ByteArray? = assets.read("$packId/$path")

    /** Bundled files cannot be removed; the pack stays Ready. */
    override suspend fun remove(packId: String) {}
}
