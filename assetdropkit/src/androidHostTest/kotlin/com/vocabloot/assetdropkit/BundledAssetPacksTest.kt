package com.vocabloot.assetdropkit

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Debug APKs carry the pack as plain app assets (`assets/<packId>/...`), because an APK installed
 * with installDebug has no asset packs. Release builds never use this source (founder, 2026-10-25).
 */
class BundledAssetPacksTest {

    private class FakeAssets(private val files: Map<String, ByteArray>) : AppAssets {
        override fun hasFolder(path: String) = files.keys.any { it.startsWith("$path/") }
        override fun read(path: String) = files[path]?.copyOf()
    }

    private val assets = FakeAssets(mapOf("demo/a.txt" to "hi".encodeToByteArray(), "demo/town/b.bin" to byteArrayOf(1)))

    @Test
    fun aBundledPackIsReadyFromTheStart() = runTest {
        val packs = BundledAssetPacks(assets)
        assertEquals(PackState.Ready, packs.state("demo").first())
        assertEquals(PackState.Ready, packs.fetch("demo"))
    }

    @Test
    fun aPackNotInTheApkIsNotFound() = runTest {
        val packs = BundledAssetPacks(assets)
        assertEquals(PackFailure.NotFound, (packs.fetch("nope") as PackState.Failed).reason)
        assertEquals(PackFailure.NotFound, (packs.state("nope").first() as PackState.Failed).reason)
    }

    @Test
    fun filesAreReadUnderThePackFolder() = runTest {
        val packs = BundledAssetPacks(assets)
        assertContentEquals("hi".encodeToByteArray(), packs.readBytes("demo", "a.txt"))
        assertContentEquals(byteArrayOf(1), packs.readBytes("demo", "town/b.bin"))
        assertNull(packs.readBytes("demo", "missing.txt"))
    }

    @Test
    fun debugBuildsUseTheBundledPacksAndReleaseBuildsUsePlay() {
        val bundled = BundledAssetPacks(assets)
        val play = PlayAssetPacks(object : PlayStore {
            override suspend fun packState(packId: String): PlayPackState? = null
            override fun listen(onUpdate: (PlayPackState) -> Unit): () -> Unit = {}
            override suspend fun fetch(packId: String) = PlayPackState(packId, 0, 0, 0, 0)
            override fun cancel(packId: String) {}
            override fun assetsPath(packId: String): String? = null
            override suspend fun remove(packId: String) {}
        })
        assertSame(bundled, PackSources.choose(debug = true, bundled = { bundled }, play = { play }))
        assertSame(play, PackSources.choose(debug = false, bundled = { bundled }, play = { play }))
    }
}
