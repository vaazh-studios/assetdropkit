package com.vocabloot.assetdropkit

import com.google.android.play.core.assetpacks.AssetPackException
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.android.play.core.ktx.requestFetch
import com.google.android.play.core.ktx.requestPackStates
import com.google.android.play.core.ktx.requestRemovePack

/** One pack's state as Play reports it. */
internal data class PlayPackState(val name: String, val status: Int, val errorCode: Int, val bytes: Long, val total: Long)

/** The part of Play's [AssetPackManager] that [PlayAssetPacks] uses; host tests replace it. */
internal interface PlayStore {
    /** Play's current state of the pack, or null when Play does not know it. */
    suspend fun packState(packId: String): PlayPackState?
    /** Registers [onUpdate] for every pack's updates; the returned function unregisters it. */
    fun listen(onUpdate: (PlayPackState) -> Unit): () -> Unit
    /** Asks Play to download the pack; a refused request comes back as FAILED with its error code. */
    suspend fun fetch(packId: String): PlayPackState
    fun cancel(packId: String)
    /** The folder holding the pack's `assets`, or null when the pack is not on the device. */
    fun assetsPath(packId: String): String?
    suspend fun remove(packId: String)
}

internal class ManagerPlayStore(private val manager: AssetPackManager) : PlayStore {

    override suspend fun packState(packId: String): PlayPackState? = try {
        manager.requestPackStates(listOf(packId)).packStates()[packId]?.toPlay()
    } catch (e: AssetPackException) {
        PlayPackState(name = packId, status = AssetPackStatus.FAILED, errorCode = e.errorCode, bytes = 0, total = 0)
    }

    override fun listen(onUpdate: (PlayPackState) -> Unit): () -> Unit {
        val listener = AssetPackStateUpdateListener { onUpdate(it.toPlay()) }
        manager.registerListener(listener)
        return { manager.unregisterListener(listener) }
    }

    override suspend fun fetch(packId: String): PlayPackState = try {
        manager.requestFetch(listOf(packId)).packStates()[packId]?.toPlay()
            ?: PlayPackState(name = packId, status = AssetPackStatus.PENDING, errorCode = 0, bytes = 0, total = 0)
    } catch (e: AssetPackException) {
        PlayPackState(name = packId, status = AssetPackStatus.FAILED, errorCode = e.errorCode, bytes = 0, total = 0)
    }

    override fun cancel(packId: String) {
        manager.cancel(listOf(packId))
    }

    override fun assetsPath(packId: String): String? = manager.getPackLocation(packId)?.assetsPath()

    override suspend fun remove(packId: String) {
        manager.requestRemovePack(packId)
    }

    private fun AssetPackState.toPlay() =
        PlayPackState(name = name(), status = status(), errorCode = errorCode(), bytes = bytesDownloaded(), total = totalBytesToDownload())
}
