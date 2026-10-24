package com.vocabloot.assetdropkit

import com.vocabloot.assetdropkit.test.FakePackSource
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetPacksTest {

    private val files = mapOf("town/a.glb" to byteArrayOf(1, 2, 3), "town/b.png" to byteArrayOf(4, 5))

    private fun TestScope.packsOver(fake: FakePackSource) = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler))

    private fun fakeWithPack(vararg script: PackState) = FakePackSource().apply {
        pack(packId = "p", version = 1, files = files)
        script("p", *script)
    }

    @Test
    fun aFetchWalksTheStoreStatesToReady() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded, PackState.Pending, PackState.Downloading(4, 10), PackState.Ready)
        assertEquals(PackState.Ready, packsOver(fake).fetch("p"))
        assertEquals(1, fake.fetchCount("p"))
    }

    @Test
    fun concurrentFetchesOfOnePackMakeOneStoreRequest() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded, PackState.Pending, PackState.Downloading(4, 10), PackState.Ready)
        val packs = packsOver(fake)
        val intro = async { packs.fetch("p") }
        val run = async { packs.fetch("p") }
        assertEquals(PackState.Ready, intro.await())
        assertEquals(PackState.Ready, run.await())
        assertEquals(1, fake.fetchCount("p"))
    }

    @Test
    fun cancellingFetchReportsCancelled() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded, PackState.Pending, PackState.Downloading(1, 10))
        val packs = packsOver(fake)
        val job = launch { packs.fetch("p") }
        advanceTimeBy(100)
        job.cancelAndJoin()
        advanceUntilIdle()
        assertEquals(1, fake.cancelCount("p"), "the store download is cancelled")
        assertEquals(PackState.Failed(PackFailure.Cancelled, null), packs.state("p").first())
    }

    @Test
    fun oneCallerCancellingLeavesTheOtherDownloading() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded, PackState.Pending, PackState.Downloading(1, 10), PackState.Downloading(5, 10), PackState.Ready)
        val packs = packsOver(fake)
        val intro = launch { packs.fetch("p") }
        val run = async { packs.fetch("p") }
        advanceTimeBy(15)
        intro.cancelAndJoin()
        assertEquals(PackState.Ready, run.await())
        assertEquals(0, fake.cancelCount("p"))
    }

    @Test
    fun firstStateComesFromTheSource() = runTest {
        // Relaunched mid-download: the store already reports progress.
        val fake = fakeWithPack(PackState.Downloading(5, 10))
        assertEquals(PackState.Downloading(5, 10), packsOver(fake).state("p").first())
    }

    @Test
    fun noNetworkFailsPromptly() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded, PackState.Failed(PackFailure.NoNetwork, "offline"))
        val result = packsOver(fake).fetch("p")
        assertEquals(PackState.Failed(PackFailure.NoNetwork, "offline"), result)
        assertTrue(testScheduler.currentTime < 1_000, "no retry loop: ${testScheduler.currentTime} ms")
    }

    @Test
    fun theNextFetchAfterAFailureRetries() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded, PackState.Failed(PackFailure.NoNetwork, null))
        val packs = packsOver(fake)
        packs.fetch("p")
        assertEquals(PackState.Ready, packs.fetch("p"))
        assertEquals(2, fake.fetchCount("p"))
    }

    @Test
    fun waitingForWifiReturnsWithoutHanging() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded, PackState.Pending, PackState.WaitingForWifi)
        assertEquals(PackState.WaitingForWifi, packsOver(fake).fetch("p"))
    }

    @Test
    fun needsPlayConfirmationReturnsWithoutHanging() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded, PackState.NeedsPlayConfirmation)
        assertEquals(PackState.NeedsPlayConfirmation, packsOver(fake).fetch("p"))
    }

    @Test
    fun readBytesReturnsAFileOfAReadyPack() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded)
        val packs = packsOver(fake)
        assertNull(packs.readBytes("p", "town/a.glb"), "nothing to read before the pack is fetched")
        packs.fetch("p")
        assertContentEquals(byteArrayOf(1, 2, 3), packs.readBytes("p", "town/a.glb"))
        assertNull(packs.readBytes("p", "town/missing.glb"))
    }

    @Test
    fun aPathOutsideThePackIsRefused() = runTest {
        val packs = packsOver(fakeWithPack(PackState.Ready))
        listOf("../q/a.glb", "/etc/hosts", "town/../../x", "", "town\\a.glb").forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) { packs.readBytes("p", path) }
        }
    }

    @Test
    fun aThrowingSourceFailsAsUnknown() = runTest {
        val fake = fakeWithPack(PackState.NotDownloaded).apply { throwOnFetch("p", IllegalStateException("store crashed")) }
        assertEquals(PackState.Failed(PackFailure.Unknown, "store crashed"), packsOver(fake).fetch("p"))
    }
}
