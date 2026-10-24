package com.vocabloot.assetdropkit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.create
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.memset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Kotlin half of the Swift adapter: callbacks resume a fetch once (a second one is
 * ignored), cancelling the coroutine cancels the Swift task, and NSData is copied out at once.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalForeignApi::class, BetaInteropApi::class)
class AppleStoreSourceTest {

    private class FakeStore : ApplePackStore {
        var onFetch: (ApplePackCallbacks) -> Unit = { it.onFinished() }
        var onStatus: (ApplePackCallbacks) -> Unit = { it.onNotDownloaded() }
        var data: NSData? = null
        var cancels = 0
        var removes = 0
        val fetched = CompletableDeferred<ApplePackCallbacks>()

        override fun fetch(packId: String, callbacks: ApplePackCallbacks): PackCancel {
            fetched.complete(callbacks)
            onFetch(callbacks)
            return object : PackCancel {
                override fun cancel() {
                    cancels++
                }
            }
        }

        override fun status(packId: String, callbacks: ApplePackCallbacks) = onStatus(callbacks)
        override fun readBytes(packId: String, path: String): NSData? = data
        override fun remove(packId: String) {
            removes++
        }
    }

    @Test
    fun aFetchEndsReady() = runTest {
        val source = AppleStoreSource(store = FakeStore(), dispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(PackState.Ready, source.fetch("p"))
        assertEquals(PackState.Ready, source.state("p").first())
    }

    @Test
    fun aSecondCallbackIsIgnored() = runTest {
        val store = FakeStore().apply {
            onFetch = {
                it.onFinished()
                it.onFailed(PackFailure.NoNetwork, "late")
                it.onProgress(1, 2)
            }
        }
        val source = AppleStoreSource(store = store, dispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(PackState.Ready, source.fetch("p"))
        assertEquals(PackState.Ready, source.state("p").first())
    }

    @Test
    fun aFailureCarriesItsReason() = runTest {
        val store = FakeStore().apply { onFetch = { it.onFailed(PackFailure.InsufficientStorage, "disk full") } }
        val source = AppleStoreSource(store = store, dispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(PackState.Failed(PackFailure.InsufficientStorage, "disk full"), source.fetch("p"))
    }

    @Test
    fun progressMapsToDownloading() = runTest {
        val store = FakeStore().apply { onFetch = { it.onProgress(3, 10) } }
        val source = AppleStoreSource(store = store, dispatcher = StandardTestDispatcher(testScheduler))
        val job = launch { source.fetch("p") }
        runCurrent()
        assertEquals(PackState.Downloading(3, 10), source.state("p").first())
        store.fetched.await().onProgress(4, 0)
        assertEquals(PackState.Downloading(4, null), source.state("p").first(), "an unknown total is null")
        job.cancelAndJoin()
    }

    @Test
    fun cancellingTheCoroutineCallsPackCancel() = runTest {
        val store = FakeStore().apply { onFetch = { } }
        val source = AppleStoreSource(store = store, dispatcher = StandardTestDispatcher(testScheduler))
        val job = launch { source.fetch("p") }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(1, store.cancels)
        assertEquals(PackState.Failed(PackFailure.Cancelled, null), source.state("p").first())
    }

    @Test
    fun callbacksFromAnotherThreadResumeTheFetch() = runTest {
        val store = FakeStore().apply {
            onFetch = { callbacks ->
                dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.convert(), 0u)) {
                    callbacks.onProgress(5, 10)
                    callbacks.onFinished()
                }
            }
        }
        assertEquals(PackState.Ready, AppleStoreSource(store = store).fetch("p"))
    }

    @Test
    fun theFirstStateIsTheStoresStatus() = runTest {
        val store = FakeStore().apply { onStatus = { it.onFinished() } }
        assertEquals(PackState.Ready, AppleStoreSource(store = store, dispatcher = StandardTestDispatcher(testScheduler)).state("p").first())
        store.onStatus = { it.onPending() }
        assertEquals(PackState.Pending, AppleStoreSource(store = store, dispatcher = StandardTestDispatcher(testScheduler)).state("p").first())
        store.onStatus = { it.onNotDownloaded() }
        assertEquals(PackState.NotDownloaded, AppleStoreSource(store = store, dispatcher = StandardTestDispatcher(testScheduler)).state("p").first())
    }

    @Test
    fun bytesAreCopiedOutOfNSData() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val data = bytes.usePinned { NSMutableData.create(bytes = it.addressOf(0), length = bytes.size.convert()) }
        val store = FakeStore().apply { this.data = data }
        val read = AppleStoreSource(store = store, dispatcher = StandardTestDispatcher(testScheduler)).readBytes("p", "a.bin")
        memset(data.mutableBytes, 0, data.length)
        assertContentEquals(bytes, read)
    }

    @Test
    fun aMissingFileReadsNull() = runTest {
        assertNull(AppleStoreSource(store = FakeStore(), dispatcher = StandardTestDispatcher(testScheduler)).readBytes("p", "a.bin"))
    }

    @Test
    fun removeAsksTheStoreAndForgetsTheState() = runTest {
        val store = FakeStore()
        val source = AppleStoreSource(store = store, dispatcher = StandardTestDispatcher(testScheduler))
        source.fetch("p")
        source.remove("p")
        assertEquals(1, store.removes)
        assertTrue(source.state("p").first() == PackState.NotDownloaded)
    }
}
