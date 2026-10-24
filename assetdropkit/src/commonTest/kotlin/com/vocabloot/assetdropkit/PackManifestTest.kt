package com.vocabloot.assetdropkit

import kotlin.test.Test
import kotlin.test.assertEquals

/** The library reads exactly what scripts/write-pack-manifest.py writes (sorted keys, two-space indent). */
class PackManifestTest {

    private val scriptOutput = """
        {
          "files": {
            "hello.txt": "bf9ef686e7cda15a812b3e767f99db67b24cbb453a2992a1427ccc772aa7e46a"
          },
          "packId": "demo",
          "version": 1
        }
    """.trimIndent()

    @Test
    fun parsesTheScriptsOutput() {
        val manifest = PackManifest.parse(scriptOutput)
        assertEquals(PackManifest(packId = "demo", version = 1, files = mapOf("hello.txt" to "bf9ef686e7cda15a812b3e767f99db67b24cbb453a2992a1427ccc772aa7e46a")), manifest)
        assertEquals(manifest.files.getValue("hello.txt"), Sha256.hex("hello from the demo pack\n".encodeToByteArray()))
    }

    @Test
    fun aManifestRoundTrips() {
        val manifest = PackManifest.of(packId = "p", version = 4, files = mapOf("a/b.bin" to byteArrayOf(1)))
        assertEquals(manifest, PackManifest.parse(manifest.toJson()))
    }
}
