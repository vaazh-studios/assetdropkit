package com.vocabloot.assetdropkit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The app's entry point: one shared fetch per pack, an integrity check against the pack's
 * `assetdropkit.json`, and reads on [dispatcher], never the caller's thread.
 */
public class AssetPacks(
    private val source: PackSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    markers: VerifiedMarkers = InMemoryVerifiedMarkers(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val integrity = PackIntegrity(source, markers)
    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, Flight>()

    private class Flight(val result: Deferred<PackState>) {
        var waiters = 0
    }

    /** The pack's state; the first value is read from the store, never assumed. */
    public fun state(packId: String): Flow<PackState> = source.state(packId).distinctUntilChanged()

    /**
     * Downloads the pack if needed and verifies it. Suspends until Ready, Failed, WaitingForWifi
     * or NeedsPlayConfirmation. Callers fetching the same pack at once share one store request;
     * the download is cancelled when every caller has been cancelled.
     */
    public suspend fun fetch(packId: String): PackState {
        val flight = lock.withLock {
            inFlight.getOrPut(packId) { Flight(scope.async { fetchAndVerify(packId) }) }.also { it.waiters++ }
        }
        try {
            return flight.result.await()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                lock.withLock {
                    flight.waiters--
                    if (flight.waiters == 0 && flight.result.isActive) flight.result.cancel()
                }
            }
            throw e
        }
    }

    /** A file of a ready pack ([path] is inside the pack, as `town/sedan.glb`), or null. */
    public suspend fun readBytes(packId: String, path: String): ByteArray? {
        requireInsidePack(path)
        return withContext(dispatcher) { source.readBytes(packId, path) }
    }

    /** Frees the pack's space; the next [fetch] downloads it again. */
    public suspend fun remove(packId: String): Unit = withContext(dispatcher) { source.remove(packId) }

    private suspend fun fetchAndVerify(packId: String): PackState = try {
        val first = storeFetch(packId)
        when {
            first != PackState.Ready -> first
            integrity.problem(packId) == null -> PackState.Ready
            else -> {
                source.remove(packId)
                val second = storeFetch(packId)
                if (second != PackState.Ready) second
                else integrity.problem(packId)?.let { PackState.Failed(PackFailure.Corrupt, it) } ?: PackState.Ready
            }
        }
    } finally {
        withContext(NonCancellable) { lock.withLock { inFlight.remove(packId) } }
    }

    private suspend fun storeFetch(packId: String): PackState = try {
        source.fetch(packId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PackState.Failed(PackFailure.Unknown, e.message)
    }

    private fun requireInsidePack(path: String) {
        require(path.isNotEmpty() && !path.startsWith("/") && '\\' !in path) { "not a path inside a pack: '$path'" }
        require(path.split('/').none { it == ".." || it == "." || it.isEmpty() }) { "not a path inside a pack: '$path'" }
    }
}
