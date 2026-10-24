package com.vocabloot.assetdropkit.test

import com.vocabloot.assetdropkit.PackFailure
import com.vocabloot.assetdropkit.PackManifest
import com.vocabloot.assetdropkit.PackSource
import com.vocabloot.assetdropkit.PackState
import com.vocabloot.assetdropkit.isSettled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A scriptable store for tests. [script] sets the pack's current state to the first value (what
 * the store reports after a relaunch) and plays the rest, one every [stepMillis], on the next
 * fetch; a script that ends unsettled hangs that fetch until it is cancelled (then
 * Failed(Cancelled)). With no script left, a fetch makes the pack Ready. Files are readable only
 * while the pack is Ready.
 */
public class FakePackSource(private val stepMillis: Long = 10) : PackSource {

    private val states = mutableMapOf<String, MutableStateFlow<PackState>>()
    private val scripts = mutableMapOf<String, ArrayDeque<PackState>>()
    private val contents = mutableMapOf<String, Map<String, ByteArray>>()
    private val throwing = mutableMapOf<String, Exception>()
    private val fetches = mutableMapOf<String, Int>()
    private val reads = mutableMapOf<String, Int>()
    private val removes = mutableMapOf<String, Int>()
    private val cancels = mutableMapOf<String, Int>()

    private fun flow(packId: String) = states.getOrPut(packId) { MutableStateFlow(PackState.NotDownloaded) }

    /** Sets the current state to [states]' first value; the next fetch plays the rest. */
    public fun script(packId: String, vararg states: PackState) {
        require(states.isNotEmpty()) { "a script needs at least the current state" }
        flow(packId).value = states.first()
        scripts[packId] = ArrayDeque(states.drop(1))
    }

    /** The pack's files, path inside the pack to contents, exactly as given (no manifest added). */
    public fun files(packId: String, files: Map<String, ByteArray>) {
        contents[packId] = files
    }

    /** The pack's files plus a matching `assetdropkit.json`, so it passes the integrity check. */
    public fun pack(packId: String, version: Int, files: Map<String, ByteArray>) {
        val manifest = PackManifest.of(packId = packId, version = version, files = files).toJson().encodeToByteArray()
        files(packId, files + (PackManifest.PATH to manifest))
    }

    /** Makes the next fetches of [packId] throw [error], like a store SDK crashing. */
    public fun throwOnFetch(packId: String, error: Exception) {
        throwing[packId] = error
    }

    public fun fetchCount(packId: String): Int = fetches[packId] ?: 0
    public fun readCount(packId: String): Int = reads[packId] ?: 0
    public fun removeCount(packId: String): Int = removes[packId] ?: 0
    public fun cancelCount(packId: String): Int = cancels[packId] ?: 0

    override fun state(packId: String): Flow<PackState> = flow(packId).asStateFlow()

    override suspend fun fetch(packId: String): PackState {
        fetches[packId] = fetchCount(packId) + 1
        throwing[packId]?.let { throw it }
        val state = flow(packId)
        val script = scripts.getOrPut(packId) { ArrayDeque() }
        try {
            if (script.isEmpty()) {
                if (state.value != PackState.Ready) {
                    delay(stepMillis)
                    state.value = PackState.Ready
                }
                return state.value
            }
            while (script.isNotEmpty()) {
                delay(stepMillis)
                state.value = script.removeFirst()
            }
            if (state.value.isSettled) return state.value
            awaitCancellation()
        } catch (e: CancellationException) {
            cancels[packId] = cancelCount(packId) + 1
            state.value = PackState.Failed(PackFailure.Cancelled, null)
            throw e
        }
    }

    override suspend fun readBytes(packId: String, path: String): ByteArray? {
        if (flow(packId).value != PackState.Ready) return null
        reads[packId] = readCount(packId) + 1
        return contents[packId]?.get(path)?.copyOf()
    }

    override suspend fun remove(packId: String) {
        removes[packId] = removeCount(packId) + 1
        flow(packId).value = PackState.NotDownloaded
    }
}
