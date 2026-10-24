package com.vocabloot.assetdropkit

/**
 * Remembers which pack versions passed the integrity check, so a version is hashed once. Back it
 * with persistent settings to hash once per install instead of once per launch. [AssetPacks]
 * calls it under its own lock, so an implementation need not be thread-safe.
 */
public interface VerifiedMarkers {
    public fun isVerified(packId: String, version: Int): Boolean
    public fun markVerified(packId: String, version: Int)

    /** Forgets every verified version of [packId]; called when the pack is removed. */
    public fun clear(packId: String) {}
}

/** [VerifiedMarkers] for one process: each launch verifies each pack once. */
public class InMemoryVerifiedMarkers : VerifiedMarkers {
    private val verified = mutableSetOf<Pair<String, Int>>()
    override fun isVerified(packId: String, version: Int): Boolean = (packId to version) in verified
    override fun markVerified(packId: String, version: Int) {
        verified += packId to version
    }
    override fun clear(packId: String) {
        verified.removeAll { it.first == packId }
    }
}
