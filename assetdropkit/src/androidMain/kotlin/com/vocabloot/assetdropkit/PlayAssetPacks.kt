package com.vocabloot.assetdropkit

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Google Play Asset Delivery: on-demand asset packs hosted by Play. Each pack is an asset-pack
 * module whose files sit under `assets/<packId>/`. A sideloaded build gets NeedsPlayConfirmation,
 * never local files.
 */
public class PlayAssetPacks internal constructor(private val play: PlayStore) : PackSource {

    public constructor(
        context: Context,
        manager: AssetPackManager = AssetPackManagerFactory.getInstance(context),
    ) : this(ManagerPlayStore(manager))

    /** Packs removed in this process: Play sends no update for a removal, so state collectors hear it here. */
    private val removed = MutableSharedFlow<String>(extraBufferCapacity = 16)

    override fun state(packId: String): Flow<PackState> = callbackFlow {
        val unregister = play.listen { if (it.name == packId) trySend(it.toPackState()) }
        launch { removed.collect { if (it == packId) send(PackState.NotDownloaded) } }
        send(play.packState(packId)?.toPackState() ?: PackState.Failed(PackFailure.NotFound, "Play does not know pack $packId"))
        awaitClose { unregister() }
    }

    override suspend fun fetch(packId: String): PackState {
        val updates = Channel<PackState>(Channel.UNLIMITED)
        val unregister = play.listen { if (it.name == packId) updates.trySend(it.toPackState()) }
        try {
            var state = play.fetch(packId).toPackState()
            while (!state.isSettled) state = updates.receive()
            return state
        } catch (e: CancellationException) {
            play.cancel(packId)
            throw e
        } finally {
            unregister()
        }
    }

    override suspend fun readBytes(packId: String, path: String): ByteArray? {
        val assets = play.assetsPath(packId) ?: return null
        return File(assets, "$packId/$path").takeIf { it.isFile }?.readBytes()
    }

    override suspend fun remove(packId: String) {
        play.remove(packId)
        removed.emit(packId)
    }

    private fun PlayPackState.toPackState() = mapPlayStatus(status = status, errorCode = errorCode, bytes = bytes, total = total)
}
