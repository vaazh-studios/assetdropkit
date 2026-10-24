package com.vocabloot.assetdropkit

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Debug builds and tests read packs straight from `packs/` on disk, with a simulated download. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalPacksTest {

    private val root = Path(SystemTemporaryDirectory, "assetdropkit-${Random.nextLong().toULong()}")

    init {
        SystemFileSystem.createDirectories(Path(root, "p", "town"))
        SystemFileSystem.sink(Path(root, "p", "town", "a.txt")).buffered().use { it.write("hello".encodeToByteArray()) }
    }

    @AfterTest
    fun cleanUp() {
        SystemFileSystem.delete(Path(root, "p", "town", "a.txt"))
        SystemFileSystem.delete(Path(root, "p", "town"))
        SystemFileSystem.delete(Path(root, "p"))
        SystemFileSystem.delete(root)
    }

    @Test
    fun readsAFileInsideItsPack() = runTest {
        val local = LocalPacks(root = root)
        assertEquals(PackState.NotDownloaded, local.state("p").value())
        assertNull(local.readBytes("p", "town/a.txt"), "nothing before the fetch, as in a store")
        assertEquals(PackState.Ready, local.fetch("p"))
        assertContentEquals("hello".encodeToByteArray(), local.readBytes("p", "town/a.txt"))
        assertNull(local.readBytes("p", "town/missing.txt"))
    }

    @Test
    fun aDelayShowsDownloadingBeforeReady() = runTest {
        val local = LocalPacks(root = root, delayMillis = 500)
        val seen = mutableListOf<PackState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { local.state("p").toList(seen) }
        assertEquals(PackState.Ready, local.fetch("p"))
        val downloading = seen.indexOfFirst { it is PackState.Downloading }
        assertTrue(downloading >= 0 && downloading < seen.indexOf(PackState.Ready), "states: $seen")
        assertEquals(500, testScheduler.currentTime)
    }

    @Test
    fun failEveryOneFailsEveryFetchAsNoNetwork() = runTest {
        val local = LocalPacks(root = root, failEvery = 1)
        repeat(2) {
            val result = local.fetch("p")
            assertIs<PackState.Failed>(result)
            assertEquals(PackFailure.NoNetwork, result.reason)
        }
    }

    @Test
    fun failEveryTwoFailsEverySecondDownload() = runTest {
        val local = LocalPacks(root = root, failEvery = 2)
        assertEquals(PackState.Ready, local.fetch("p"))
        assertEquals(PackState.Ready, local.fetch("p"), "a ready pack is not downloaded again")
        local.remove("p")
        assertIs<PackState.Failed>(local.fetch("p"))
        assertEquals(PackState.Ready, local.fetch("p"))
    }

    @Test
    fun aMissingPackIsNotFound() = runTest {
        val result = LocalPacks(root = root).fetch("nope")
        assertEquals(PackFailure.NotFound, (result as PackState.Failed).reason)
    }

    @Test
    fun removeMakesThePackNotDownloadedButKeepsTheSourceFiles() = runTest {
        val local = LocalPacks(root = root)
        local.fetch("p")
        local.remove("p")
        assertEquals(PackState.NotDownloaded, local.state("p").value())
        assertTrue(SystemFileSystem.exists(Path(root, "p", "town", "a.txt")))
    }

    private suspend fun Flow<PackState>.value() = first()
}
