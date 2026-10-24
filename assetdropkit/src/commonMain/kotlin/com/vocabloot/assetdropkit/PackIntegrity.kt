package com.vocabloot.assetdropkit

/** Checks a ready pack against its manifest; returns null when it matches, or what was wrong. */
internal class PackIntegrity(private val source: PackSource, private val markers: VerifiedMarkers) {

    suspend fun problem(packId: String): String? {
        val text = source.readBytes(packId, PackManifest.PATH)?.decodeToString() ?: return "no ${PackManifest.PATH}"
        val manifest = runCatching { PackManifest.parse(text) }.getOrElse { return "unreadable ${PackManifest.PATH}: ${it.message}" }
        if (manifest.packId != packId) return "${PackManifest.PATH} is for pack ${manifest.packId}"
        if (markers.isVerified(packId, manifest.version)) return null
        manifest.files.forEach { (path, expected) ->
            val bytes = source.readBytes(packId, path) ?: return "missing $path"
            if (Sha256.hex(bytes) != expected.lowercase()) return "changed $path"
        }
        markers.markVerified(packId, manifest.version)
        return null
    }
}
