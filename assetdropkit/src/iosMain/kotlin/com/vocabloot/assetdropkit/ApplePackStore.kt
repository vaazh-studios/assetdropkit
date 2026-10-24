package com.vocabloot.assetdropkit

import platform.Foundation.NSData

/**
 * What the app's Swift adapter (`templates/ios/AssetDropAdapter.swift`) reports for one call.
 * Call one of [onFinished], [onFailed], [onPending] or [onNotDownloaded] to end it; anything
 * after the first ending call is ignored. Plain methods rather than lambdas, so progress ticks
 * box no numbers and nothing trips Swift 6's Sendable checks.
 */
public interface ApplePackCallbacks {
    /** Download progress; [total] is 0 when the system does not say. */
    public fun onProgress(bytes: Long, total: Long)
    public fun onFinished()
    public fun onFailed(reason: PackFailure, detail: String?)
    /** Status only: queued or paused by the system. */
    public fun onPending()
    /** Status only: not on the device. */
    public fun onNotDownloaded()
}

/** Cancels the Swift task behind one fetch. */
public interface PackCancel {
    public fun cancel()
}

/**
 * The app's Swift adapter over the `AssetDropKit` Swift package (Apple-hosted packs on iOS 26+,
 * On-Demand Resources on iOS 17 and 18). The app framework re-exports this library
 * (`export("com.vocabloot:assetdropkit")`) so Swift sees these names.
 */
public interface ApplePackStore {
    /** Starts or resumes the download, reporting through [callbacks] until it ends. */
    public fun fetch(packId: String, callbacks: ApplePackCallbacks): PackCancel
    /** Reports the pack's state on the device once: finished (ready), pending, not downloaded or failed. */
    public fun status(packId: String, callbacks: ApplePackCallbacks)
    /** A file of a ready pack, path inside the pack; null when missing. */
    public fun readBytes(packId: String, path: String): NSData?
    /** Lets the system free the pack; call `onFinished` once it is gone, so a following fetch cannot overtake it. */
    public fun remove(packId: String, callbacks: ApplePackCallbacks)
}
