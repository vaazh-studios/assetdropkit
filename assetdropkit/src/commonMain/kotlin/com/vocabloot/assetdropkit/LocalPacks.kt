package com.vocabloot.assetdropkit

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Packs read from `root/<packId>/` on disk, for debug builds and tests. A fetch waits
 * [delayMillis] (showing Downloading) and every [failEvery]-th fetch fails as NoNetwork
 * (0 never fails). Like a store, nothing is readable before a fetch; [remove] forgets the fetch
 * and never deletes the files. Release builds never use it.
 */
public class LocalPacks(
    private val root: Path,
    private val delayMillis: Long = 0,
    private val failEvery: Int = 0,
) : PackSource {

    private val states = mutableMapOf<String, MutableStateFlow<PackState>>()
    private var fetches = 0

    private fun flow(packId: String) = states.getOrPut(packId) { MutableStateFlow(PackState.NotDownloaded) }

    override fun state(packId: String): Flow<PackState> = flow(packId).asStateFlow()

    override suspend fun fetch(packId: String): PackState {
        val state = flow(packId)
        if (state.value == PackState.Ready) return PackState.Ready
        fetches++
        if (delayMillis > 0) {
            state.value = PackState.Downloading(bytes = 0, totalBytes = null)
            delay(delayMillis)
        }
        state.value = when {
            failEvery > 0 && fetches % failEvery == 0 -> PackState.Failed(PackFailure.NoNetwork, "simulated by LocalPacks(failEvery = $failEvery)")
            !SystemFileSystem.exists(Path(root, packId)) -> PackState.Failed(PackFailure.NotFound, "no ${Path(root, packId)}")
            else -> PackState.Ready
        }
        return state.value
    }

    override suspend fun readBytes(packId: String, path: String): ByteArray? {
        if (flow(packId).value != PackState.Ready) return null
        val file = Path(root, packId, *path.split('/').toTypedArray())
        if (SystemFileSystem.metadataOrNull(file)?.isRegularFile != true) return null
        return SystemFileSystem.source(file).buffered().use { it.readByteArray() }
    }

    override suspend fun remove(packId: String) {
        flow(packId).value = PackState.NotDownloaded
    }
}
