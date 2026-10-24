package com.vocabloot.assetdropkit

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Apple's stores through the app's Swift adapter: Apple-hosted packs on iOS 26 and later,
 * On-Demand Resources on iOS 17 and 18. Callbacks may arrive on any thread; each call resumes
 * once, and file bytes are copied out of NSData on [dispatcher] straight away.
 */
public class AppleStoreSource(
    private val store: ApplePackStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PackSource {

    private val lock = Mutex()
    private val states = mutableMapOf<String, MutableStateFlow<PackState?>>()
    private val fetching = mutableSetOf<String>()

    private suspend fun flowOf(packId: String) = lock.withLock { states.getOrPut(packId) { MutableStateFlow(null) } }

    /** The first value in a process comes from the store; after that, from this source's own fetches. */
    override fun state(packId: String): Flow<PackState> = flow {
        val state = flowOf(packId)
        if (state.value == null && lock.withLock { packId !in fetching }) {
            val current = call { store.status(packId, it) }
            state.compareAndSet(null, current)
        }
        emitAll(state.filterNotNull())
    }

    override suspend fun fetch(packId: String): PackState {
        val state = flowOf(packId)
        lock.withLock { fetching += packId }
        state.update { if (it == PackState.Ready) it else PackState.Pending }
        try {
            // A tick racing the result loses: progress never replaces a settled state.
            val result = call(onProgress = { tick -> state.update { if (it?.isSettled == true) it else tick } }) { store.fetch(packId, it) }
            state.value = result
            return result
        } catch (e: CancellationException) {
            state.value = PackState.Failed(PackFailure.Cancelled, null)
            throw e
        } finally {
            lock.withLock { fetching -= packId }
        }
    }

    override suspend fun readBytes(packId: String, path: String): ByteArray? =
        withContext(dispatcher) { store.readBytes(packId, path)?.copyToByteArray() }

    override suspend fun remove(packId: String) {
        call { store.remove(packId, it) }
        flowOf(packId).value = PackState.NotDownloaded
    }

    /** Runs one adapter call and suspends until its first ending callback; cancelling calls [PackCancel.cancel]. */
    private suspend fun call(onProgress: (PackState) -> Unit = {}, start: (ApplePackCallbacks) -> Any?): PackState {
        val done = CompletableDeferred<PackState>()
        val callbacks = object : ApplePackCallbacks {
            override fun onProgress(bytes: Long, total: Long) {
                if (done.isActive) onProgress(PackState.Downloading(bytes = bytes, totalBytes = total.takeIf { it > 0 }))
            }
            override fun onFinished() {
                done.complete(PackState.Ready)
            }
            override fun onFailed(reason: PackFailure, detail: String?) {
                done.complete(PackState.Failed(reason, detail))
            }
            override fun onPending() {
                done.complete(PackState.Pending)
            }
            override fun onNotDownloaded() {
                done.complete(PackState.NotDownloaded)
            }
        }
        val cancel = start(callbacks) as? PackCancel
        try {
            return done.await()
        } catch (e: CancellationException) {
            cancel?.cancel()
            throw e
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.copyToByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
