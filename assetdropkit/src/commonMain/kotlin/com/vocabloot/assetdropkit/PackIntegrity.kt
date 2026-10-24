package com.vocabloot.assetdropkit

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Checks a ready pack against its manifest; returns null when it matches, or what was wrong. */
internal class PackIntegrity(private val source: PackSource, private val markers: VerifiedMarkers) {

    /** Fetches and state collectors verify concurrently; markers are touched one call at a time. */
    private val markersLock = Mutex()

    suspend fun problem(packId: String): String? {
        val text = source.readBytes(packId, PackManifest.PATH)?.decodeToString() ?: return "no ${PackManifest.PATH}"
        val manifest = runCatching { PackManifest.parse(text) }.getOrElse { return "unreadable ${PackManifest.PATH}: ${it.message}" }
        if (manifest.packId != packId) return "${PackManifest.PATH} is for pack ${manifest.packId}"
        if (markersLock.withLock { markers.isVerified(packId, manifest.version) }) return null
        manifest.files.forEach { (path, expected) ->
            val bytes = source.readBytes(packId, path) ?: return "missing $path"
            if (Sha256.hex(bytes) != expected.lowercase()) return "changed $path"
        }
        markersLock.withLock { markers.markVerified(packId, manifest.version) }
        return null
    }

    suspend fun forget(packId: String) {
        markersLock.withLock { markers.clear(packId) }
    }
}
