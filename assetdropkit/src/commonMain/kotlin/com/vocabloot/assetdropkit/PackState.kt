package com.vocabloot.assetdropkit

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/** Where a pack stands, as its store reports it. */
public sealed interface PackState {
    /** Not on the device. [AssetPacks.fetch] downloads it. */
    public data object NotDownloaded : PackState

    /** Queued, or paused by the system. */
    public data object Pending : PackState

    /** Downloading; [totalBytes] is null when the store does not say. */
    public data class Downloading(val bytes: Long, val totalBytes: Long?) : PackState

    /** Play only: unpacking after the download. */
    public data object Transferring : PackState

    /** Play only: a large pack waits for Wi-Fi until the user agrees to mobile data (`PlayPrompts.show`). */
    public data object WaitingForWifi : PackState

    /** Play only: Play does not recognise this install (a sideload); `PlayPrompts.show` asks the user. */
    public data object NeedsPlayConfirmation : PackState

    /** On the device and verified; [AssetPacks.readBytes] reads it. */
    public data object Ready : PackState

    /** The fetch stopped. [detail] is the store's own message, for logs. */
    public data class Failed(val reason: PackFailure, val detail: String?) : PackState
}

/** Why a fetch failed, the same on every store. Swift sees camel-case names (`.noNetwork`). */
@OptIn(ExperimentalObjCName::class)
public enum class PackFailure {
    @ObjCName("noNetwork") NoNetwork,
    @ObjCName("insufficientStorage") InsufficientStorage,
    @ObjCName("notFound") NotFound,
    @ObjCName("storeUnavailable") StoreUnavailable,
    @ObjCName("cancelled") Cancelled,
    @ObjCName("corrupt") Corrupt,
    @ObjCName("unknown") Unknown,
}

/** True for the states [PackSource.fetch] returns on: nothing more happens without the user or a new fetch. */
public val PackState.isSettled: Boolean
    get() = this is PackState.Ready || this is PackState.Failed || this is PackState.WaitingForWifi || this is PackState.NeedsPlayConfirmation
