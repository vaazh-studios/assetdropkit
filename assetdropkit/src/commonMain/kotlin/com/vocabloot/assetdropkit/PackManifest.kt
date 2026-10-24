package com.vocabloot.assetdropkit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `<packId>/assetdropkit.json`: the pack's content [version] and the lowercase hex SHA-256 of each
 * file, keyed by its path inside the pack. `scripts/write-pack-manifest.py` writes it at build time.
 */
@Serializable
public data class PackManifest(val packId: String, val version: Int, val files: Map<String, String>) {

    /** The manifest as written into the pack. */
    public fun toJson(): String = json.encodeToString(serializer(), this)

    public companion object {
        /** The manifest's path inside every pack. */
        public const val PATH: String = "assetdropkit.json"

        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        /** Reads a manifest; throws on malformed JSON. */
        public fun parse(text: String): PackManifest = json.decodeFromString(serializer(), text)

        /** The manifest for [files] (path inside the pack to contents), for tests and tools. */
        public fun of(packId: String, version: Int, files: Map<String, ByteArray>): PackManifest =
            PackManifest(packId = packId, version = version, files = files.mapValues { Sha256.hex(it.value) })
    }
}
