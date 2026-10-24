package com.vocabloot.assetdropkit

import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayAssetPacksTest {

    private class FakePlay : PlayStore {
        var current: PlayPackState? = PlayPackState(name = "p", status = AssetPackStatus.NOT_INSTALLED, errorCode = 0, bytes = 0, total = 0)
        var fetchAnswer = PlayPackState(name = "p", status = AssetPackStatus.PENDING, errorCode = 0, bytes = 0, total = 0)
        var location: String? = null
        val listeners = mutableListOf<(PlayPackState) -> Unit>()
        val cancelled = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val fetchStarted = CompletableDeferred<Unit>()

        fun emit(status: Int, errorCode: Int = 0, bytes: Long = 0, total: Long = 0) {
            val state = PlayPackState(name = "p", status = status, errorCode = errorCode, bytes = bytes, total = total)
            current = state
            listeners.toList().forEach { it(state) }
        }

        override suspend fun packState(packId: String) = current
        override fun listen(onUpdate: (PlayPackState) -> Unit): () -> Unit {
            listeners += onUpdate
            return { listeners -= onUpdate }
        }
        override suspend fun fetch(packId: String): PlayPackState {
            fetchStarted.complete(Unit)
            return fetchAnswer
        }
        override fun cancel(packId: String) {
            cancelled += packId
        }
        override fun assetsPath(packId: String) = location
        override suspend fun remove(packId: String) {
            removed += packId
        }
    }

    @Test
    fun firstStateAfterRelaunchIsTheStoresState() = runTest {
        val play = FakePlay().apply { current = PlayPackState(name = "p", status = AssetPackStatus.DOWNLOADING, errorCode = 0, bytes = 3, total = 10) }
        assertEquals(PackState.Downloading(3, 10), PlayAssetPacks(play).state("p").first())
    }

    @Test
    fun aPackPlayDoesNotKnowIsNotFound() = runTest {
        val play = FakePlay().apply { current = null }
        assertEquals(PackFailure.NotFound, (PlayAssetPacks(play).state("p").first() as PackState.Failed).reason)
    }

    @Test
    fun fetchFollowsPlaysUpdatesUntilSettled() = runTest {
        val play = FakePlay()
        val result = async { PlayAssetPacks(play).fetch("p") }
        play.fetchStarted.await()
        runCurrent()
        play.emit(AssetPackStatus.DOWNLOADING, bytes = 5, total = 10)
        play.emit(AssetPackStatus.TRANSFERRING)
        play.emit(AssetPackStatus.COMPLETED)
        assertEquals(PackState.Ready, result.await())
        assertTrue(play.listeners.isEmpty(), "the listener is unregistered")
    }

    @Test
    fun aFailedFetchRequestReturnsAtOnce() = runTest {
        val play = FakePlay().apply { fetchAnswer = PlayPackState(name = "p", status = AssetPackStatus.FAILED, errorCode = AssetPackErrorCode.NETWORK_ERROR, bytes = 0, total = 0) }
        assertEquals(PackFailure.NoNetwork, (PlayAssetPacks(play).fetch("p") as PackState.Failed).reason)
    }

    @Test
    fun updatesForOtherPacksAreIgnored() = runTest {
        val play = FakePlay()
        val result = async { PlayAssetPacks(play).fetch("p") }
        play.fetchStarted.await()
        runCurrent()
        play.listeners.toList().forEach { it(PlayPackState(name = "q", status = AssetPackStatus.COMPLETED, errorCode = 0, bytes = 0, total = 0)) }
        runCurrent()
        assertTrue(result.isActive)
        play.emit(AssetPackStatus.COMPLETED)
        assertEquals(PackState.Ready, result.await())
    }

    @Test
    fun cancellingAFetchCancelsTheDownload() = runTest {
        val play = FakePlay()
        val job = launch { PlayAssetPacks(play).fetch("p") }
        play.fetchStarted.await()
        runCurrent()
        job.cancelAndJoin()
        assertEquals(listOf("p"), play.cancelled)
        assertTrue(play.listeners.isEmpty())
    }

    @Test
    fun readBytesUsesThePackLocation() = runTest {
        val assets = Files.createTempDirectory("play-assets").toFile()
        File(assets, "p/town").mkdirs()
        File(assets, "p/town/a.txt").writeText("hi")
        val play = FakePlay().apply { location = assets.path }
        val packs = PlayAssetPacks(play)
        assertContentEquals("hi".encodeToByteArray(), packs.readBytes("p", "town/a.txt"))
        assertNull(packs.readBytes("p", "town/missing.txt"))
        assets.deleteRecursively()
    }

    @Test
    fun readBytesIsNullWithoutAPackLocation() = runTest {
        assertNull(PlayAssetPacks(FakePlay()).readBytes("p", "town/a.txt"))
    }

    @Test
    fun removeAsksPlay() = runTest {
        val play = FakePlay()
        PlayAssetPacks(play).remove("p")
        assertEquals(listOf("p"), play.removed)
    }

    @Test
    fun aReleaseSourceNeverReadsLocalFiles() {
        val local = LocalPacks(root = Path("packs"))
        val play = PlayAssetPacks(FakePlay())
        assertSame(play, PackSources.choose(debug = false, local = { local }, play = { play }))
        assertSame(local, PackSources.choose(debug = true, local = { local }, play = { play }))
    }
}
