package com.vocabloot.assetdropkit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The app's entry point: one shared fetch per pack, an integrity check against the pack's
 * `assetdropkit.json`, and reads on [dispatcher], never the caller's thread.
 *
 * [stallTimeoutMillis] (0 = off) bounds a download whose state stops changing, as an offline
 * download parked in Pending does: it is cancelled and the fetch returns `Failed(NoNetwork)`.
 */
public class AssetPacks(
    private val source: PackSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    markers: VerifiedMarkers = InMemoryVerifiedMarkers(),
    private val stallTimeoutMillis: Long = 0,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val integrity = PackIntegrity(source, markers)
    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, Flight>()

    private class Flight {
        lateinit var result: Deferred<PackState>
        var waiters = 0
    }

    /**
     * The pack's state; the first value is read from the store, never assumed. A store's Ready
     * becomes Ready here only once the pack matches its manifest (otherwise `Failed(Corrupt)`), so
     * a UI keyed on Ready never reads an unverified pack.
     */
    public fun state(packId: String): Flow<PackState> = source.state(packId)
        .distinctUntilChanged()
        .map { state -> if (state == PackState.Ready) withContext(dispatcher) { verified(packId) } else state }
        .distinctUntilChanged()

    /**
     * Downloads the pack if needed and verifies it. Suspends until Ready, Failed, WaitingForWifi
     * or NeedsPlayConfirmation, and returns a state rather than throwing. Callers fetching the
     * same pack at once share one store request; the download is cancelled when every caller has
     * been cancelled.
     */
    public suspend fun fetch(packId: String): PackState {
        val flight = lock.withLock {
            inFlight[packId]?.takeIf { !it.result.isCancelled }
                ?: Flight().also { created ->
                    created.result = scope.async(start = CoroutineStart.LAZY) {
                        try {
                            fetchAndVerify(packId)
                        } finally {
                            withContext(NonCancellable) { lock.withLock { if (inFlight[packId] === created) inFlight.remove(packId) } }
                        }
                    }
                    inFlight[packId] = created
                }
        }.also { it.waiters++ }
        flight.result.start()
        try {
            return flight.result.await()
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                lock.withLock {
                    flight.waiters--
                    if (flight.waiters == 0) {
                        if (inFlight[packId] === flight) inFlight.remove(packId)
                        flight.result.cancel()
                    }
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

    /** Frees the pack's space and forgets its verification; the next [fetch] downloads and checks it again. */
    public suspend fun remove(packId: String): Unit = withContext(dispatcher) {
        integrity.forget(packId)
        source.remove(packId)
    }

    private suspend fun verified(packId: String): PackState = safely {
        integrity.problem(packId)?.let { PackState.Failed(PackFailure.Corrupt, it) } ?: PackState.Ready
    }

    private suspend fun fetchAndVerify(packId: String): PackState = safely {
        val first = storeFetch(packId)
        if (first != PackState.Ready) return@safely first
        if (integrity.problem(packId) == null) return@safely PackState.Ready
        integrity.forget(packId)
        source.remove(packId)
        val second = storeFetch(packId)
        if (second != PackState.Ready) second
        else integrity.problem(packId)?.let { PackState.Failed(PackFailure.Corrupt, it) } ?: PackState.Ready
    }

    private suspend fun storeFetch(packId: String): PackState =
        if (stallTimeoutMillis <= 0) source.fetch(packId) else fetchWatchingForStalls(packId)

    /** The store fetch, cancelled when the pack's state has not changed for [stallTimeoutMillis]. */
    private suspend fun fetchWatchingForStalls(packId: String): PackState = coroutineScope {
        var stalled = false
        val result = async { source.fetch(packId) }
        val watchdog = launch {
            source.state(packId).collectLatest {
                delay(stallTimeoutMillis)
                stalled = true
                result.cancel()
            }
        }
        try {
            result.await()
        } catch (e: CancellationException) {
            if (!stalled) throw e
            PackState.Failed(PackFailure.NoNetwork, "no progress for $stallTimeoutMillis ms")
        } finally {
            watchdog.cancel()
        }
    }

    /** Anything a source or the file system throws becomes `Failed(Unknown)`; cancellation passes through. */
    private suspend fun safely(block: suspend () -> PackState): PackState = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PackState.Failed(PackFailure.Unknown, e.message ?: e::class.simpleName)
    }

    private fun requireInsidePack(path: String) {
        require(path.isNotEmpty() && !path.startsWith("/") && '\\' !in path) { "not a path inside a pack: '$path'" }
        require(path.split('/').none { it == ".." || it == "." || it.isEmpty() }) { "not a path inside a pack: '$path'" }
    }
}
