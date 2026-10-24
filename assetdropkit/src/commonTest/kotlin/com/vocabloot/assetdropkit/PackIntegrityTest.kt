package com.vocabloot.assetdropkit

import com.vocabloot.assetdropkit.test.FakePackSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A store can report a pack ready with a file missing or changed (the iOS 26.4 reports). Each
 * pack carries `assetdropkit.json`; a mismatch removes the pack, fetches once more, then fails
 * as Corrupt. A matching pack version is hashed once, not on every fetch.
 */
class PackIntegrityTest {

    private val files = mapOf("a.bin" to byteArrayOf(1, 2, 3), "b.bin" to byteArrayOf(4, 5))
    private val manifest = PackManifest.of(packId = "p", version = 1, files = files).toJson().encodeToByteArray()

    @Test
    fun aMissingFileRemovesRefetchesOnceThenReportsCorrupt() = runTest {
        val fake = FakePackSource().apply {
            files("p", mapOf("assetdropkit.json" to manifest, "a.bin" to files.getValue("a.bin")))
            script("p", PackState.NotDownloaded)
        }
        val result = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler)).fetch("p")
        assertIs<PackState.Failed>(result)
        assertEquals(PackFailure.Corrupt, result.reason)
        assertEquals(2, fake.fetchCount("p"))
        assertEquals(1, fake.removeCount("p"))
    }

    @Test
    fun aChangedFileIsCorruptToo() = runTest {
        val fake = FakePackSource().apply {
            files("p", mapOf("assetdropkit.json" to manifest, "a.bin" to byteArrayOf(9, 9, 9), "b.bin" to files.getValue("b.bin")))
            script("p", PackState.Ready)
        }
        val result = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler)).fetch("p")
        assertEquals(PackFailure.Corrupt, (result as PackState.Failed).reason)
    }

    @Test
    fun aPackWithoutAManifestIsCorrupt() = runTest {
        val fake = FakePackSource().apply { files("p", files); script("p", PackState.Ready) }
        val result = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler)).fetch("p")
        assertEquals(PackFailure.Corrupt, (result as PackState.Failed).reason)
    }

    @Test
    fun aManifestForAnotherPackIsCorrupt() = runTest {
        val other = PackManifest.of(packId = "q", version = 1, files = files).toJson().encodeToByteArray()
        val fake = FakePackSource().apply { files("p", files + ("assetdropkit.json" to other)); script("p", PackState.Ready) }
        val result = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler)).fetch("p")
        assertEquals(PackFailure.Corrupt, (result as PackState.Failed).reason)
    }

    @Test
    fun aMatchingPackIsVerifiedOncePerVersion() = runTest {
        val fake = FakePackSource().apply { pack("p", 1, files); script("p", PackState.NotDownloaded) }
        val packs = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler))
        assertEquals(PackState.Ready, packs.fetch("p"))
        assertEquals(3, fake.readCount("p"), "the manifest and both files")
        assertEquals(PackState.Ready, packs.fetch("p"))
        assertEquals(4, fake.readCount("p"), "only the manifest the second time")
    }

    @Test
    fun aNewVersionIsVerifiedAgain() = runTest {
        val markers = InMemoryVerifiedMarkers()
        val fake = FakePackSource().apply { pack("p", 1, files); script("p", PackState.NotDownloaded) }
        val packs = AssetPacks(source = fake, dispatcher = StandardTestDispatcher(testScheduler), markers = markers)
        packs.fetch("p")
        fake.pack("p", 2, files)
        assertEquals(PackState.Ready, packs.fetch("p"))
        assertEquals(6, fake.readCount("p"))
        assertEquals(true, markers.isVerified("p", 2))
    }
}
