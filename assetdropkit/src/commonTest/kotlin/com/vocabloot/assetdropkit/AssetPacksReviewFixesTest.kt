package com.vocabloot.assetdropkit

import com.vocabloot.assetdropkit.test.FakePackSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Findings from the whole-branch review (2026-10-25), each pinned before its fix. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssetPacksReviewFixesTest {

    private val files = mapOf("a.bin" to byteArrayOf(1, 2, 3), "b.bin" to byteArrayOf(4, 5))
    private val manifest = PackManifest.of(packId = "p", version = 1, files = files).toJson().encodeToByteArray()

    /** Runs nothing until told: lets a test cancel a fetch before its shared body has started. */
    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    // Finding 1: a fetch cancelled before its body started left a cancelled flight that every later fetch joined.
    @Test
    fun aFetchCancelledBeforeItStartsDoesNotBreakThePack() = runTest {
        val fake = FakePackSource(stepMillis = 0).apply { pack("p", 1, files); script("p", PackState.NotDownloaded) }
        val manual = ManualDispatcher()
        val packs = AssetPacks(source = fake, dispatcher = manual)
        val first = launch(start = CoroutineStart.UNDISPATCHED) { packs.fetch("p") }
        first.cancelAndJoin()
        val second = async { runCatching { packs.fetch("p") } }
        repeat(20) {
            runCurrent()
            manual.runAll()
        }
        assertEquals(PackState.Ready, second.await().getOrThrow())
    }

    // Finding 2: state() passed the store's Ready through without the integrity check.
    @Test
    fun aRelaunchWithAChangedFileShowsCorruptNotReady() = runTest {
        val fake = FakePackSource().apply {
            files("p", mapOf(PackManifest.PATH to manifest, "a.bin" to byteArrayOf(9, 9, 9), "b.bin" to files.getValue("b.bin")))
            script("p", PackState.Ready)
        }
        val state = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler)).state("p").first()
        assertIs<PackState.Failed>(state)
        assertEquals(PackFailure.Corrupt, state.reason)
    }

    @Test
    fun aRelaunchWithAMatchingPackShowsReady() = runTest {
        val fake = FakePackSource().apply { pack("p", 1, files); script("p", PackState.Ready) }
        assertEquals(PackState.Ready, AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler)).state("p").first())
    }

    @Test
    fun afterACorruptFetchTheStateStaysCorrupt() = runTest {
        val fake = FakePackSource().apply {
            files("p", mapOf(PackManifest.PATH to manifest, "a.bin" to files.getValue("a.bin")))
            script("p", PackState.NotDownloaded)
        }
        val packs = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(PackFailure.Corrupt, (packs.fetch("p") as PackState.Failed).reason)
        assertEquals(PackFailure.Corrupt, (packs.state("p").first() as PackState.Failed).reason)
    }

    // Finding 6: errors in the verify and remove steps escaped fetch as exceptions.
    @Test
    fun aThrowingReadDuringVerificationIsAStateNotAnException() = runTest {
        val fake = FakePackSource().apply { pack("p", 1, files); script("p", PackState.NotDownloaded); throwOnRead("p", IllegalStateException("disk gone")) }
        val result = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler)).fetch("p")
        assertIs<PackState.Failed>(result)
    }

    @Test
    fun aThrowingRemoveDuringTheRetryIsAStateNotAnException() = runTest {
        val fake = FakePackSource().apply {
            files("p", mapOf(PackManifest.PATH to manifest, "a.bin" to files.getValue("a.bin")))
            script("p", PackState.NotDownloaded)
            throwOnRemove("p", IllegalStateException("Play said no"))
        }
        val result = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler)).fetch("p")
        assertIs<PackState.Failed>(result)
    }

    // Finding 7: remove() kept the verification, so a re-download of the same version was never re-hashed.
    @Test
    fun removeForgetsTheVerification() = runTest {
        val fake = FakePackSource().apply { pack("p", 1, files); script("p", PackState.NotDownloaded) }
        val packs = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler))
        packs.fetch("p")
        assertEquals(3, fake.readCount("p"))
        packs.remove("p")
        assertEquals(PackState.Ready, packs.fetch("p"))
        assertEquals(6, fake.readCount("p"), "the manifest and both files again")
    }

    // Finding 5: a download parked in Pending (offline) never settled.
    @Test
    fun aStalledDownloadFailsAsNoNetworkAfterTheStallTimeout() = runTest {
        val fake = FakePackSource().apply { pack("p", 1, files); script("p", PackState.NotDownloaded, PackState.Pending) }
        val packs = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler), stallTimeoutMillis = 30_000)
        val result = packs.fetch("p")
        assertIs<PackState.Failed>(result)
        assertEquals(PackFailure.NoNetwork, result.reason)
        assertTrue(testScheduler.currentTime in 30_000..31_000, "gave up after ${testScheduler.currentTime} ms")
        assertEquals(1, fake.cancelCount("p"), "the stalled download is cancelled")
        assertEquals(PackState.Ready, packs.fetch("p"), "the next fetch retries")
    }

    @Test
    fun aDownloadThatKeepsProgressingIsNotCutOff() = runTest {
        val ticks = (1..20).map { PackState.Downloading(it.toLong(), 20) }.toTypedArray()
        val fake = FakePackSource(stepMillis = 20_000).apply { pack("p", 1, files); script("p", PackState.NotDownloaded, *ticks, PackState.Ready) }
        val packs = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler), stallTimeoutMillis = 30_000)
        assertEquals(PackState.Ready, packs.fetch("p"))
    }
}
