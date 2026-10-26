package com.vocabloot.assetdropkit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * The store is asked on the packs' dispatcher, never the collector's thread: on Android the first
 * state is a Play query (a binder call, or a disk read with Play's local-testing service), and a
 * UI collects state on the main thread.
 */
class AssetPacksDispatcherTest {

    private class RecordingSource : PackSource {
        var stateReadOn: CoroutineContext.Element? = null

        override fun state(packId: String): Flow<PackState> = flow {
            stateReadOn = currentCoroutineContext()[ContinuationInterceptor]
            emit(PackState.NotDownloaded)
        }

        override suspend fun fetch(packId: String): PackState = PackState.NotDownloaded
        override suspend fun readBytes(packId: String, path: String): ByteArray? = null
        override suspend fun remove(packId: String) {}
    }

    @Test
    fun theStoresStateIsReadOnThePacksDispatcher() = runTest {
        val packsDispatcher = StandardTestDispatcher(testScheduler)
        val source = RecordingSource()
        AssetPacks(source = source, dispatcher = packsDispatcher).state("p").first()
        assertSame(packsDispatcher, source.stateReadOn)
    }
}
