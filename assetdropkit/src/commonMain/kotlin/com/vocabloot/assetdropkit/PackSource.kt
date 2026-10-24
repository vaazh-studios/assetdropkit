package com.vocabloot.assetdropkit

import kotlinx.coroutines.flow.Flow

/**
 * One store's delivery: Play, Apple's, or local files. Paths are inside the pack, without the
 * `<packId>/` prefix; each source adds it. Apps use [AssetPacks], which adds shared fetches and the
 * integrity check on top of a source.
 */
public interface PackSource {
    /** The pack's state; the first value is read from the store, never assumed. */
    public fun state(packId: String): Flow<PackState>

    /** Starts or resumes the download and suspends until the state [isSettled]. Cancelling cancels the download. */
    public suspend fun fetch(packId: String): PackState

    /** A file of a ready pack, or null when the pack is not ready or has no such file. */
    public suspend fun readBytes(packId: String, path: String): ByteArray?

    /** Frees the pack's space; the next [fetch] downloads it again. */
    public suspend fun remove(packId: String)
}
