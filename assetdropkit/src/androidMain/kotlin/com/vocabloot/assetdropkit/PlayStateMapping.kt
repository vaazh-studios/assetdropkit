package com.vocabloot.assetdropkit

import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus

/** Play's status and error code as a [PackState]; [total] 0 means Play does not know the size. */
internal fun mapPlayStatus(status: Int, errorCode: Int, bytes: Long, total: Long): PackState = when (status) {
    AssetPackStatus.NOT_INSTALLED -> PackState.NotDownloaded
    AssetPackStatus.PENDING -> PackState.Pending
    AssetPackStatus.DOWNLOADING -> PackState.Downloading(bytes = bytes, totalBytes = total.takeIf { it > 0 })
    AssetPackStatus.TRANSFERRING -> PackState.Transferring
    AssetPackStatus.COMPLETED -> PackState.Ready
    AssetPackStatus.WAITING_FOR_WIFI -> PackState.WaitingForWifi
    AssetPackStatus.REQUIRES_USER_CONFIRMATION -> PackState.NeedsPlayConfirmation
    AssetPackStatus.CANCELED -> PackState.Failed(PackFailure.Cancelled, null)
    AssetPackStatus.FAILED -> PackState.Failed(playFailure(errorCode), "Play error $errorCode")
    else -> PackState.Failed(PackFailure.Unknown, "Play status $status")
}

private fun playFailure(errorCode: Int): PackFailure = when (errorCode) {
    AssetPackErrorCode.NETWORK_ERROR -> PackFailure.NoNetwork
    AssetPackErrorCode.INSUFFICIENT_STORAGE -> PackFailure.InsufficientStorage
    AssetPackErrorCode.PACK_UNAVAILABLE, AssetPackErrorCode.DOWNLOAD_NOT_FOUND -> PackFailure.NotFound
    AssetPackErrorCode.APP_UNAVAILABLE, AssetPackErrorCode.APP_NOT_OWNED,
    AssetPackErrorCode.API_NOT_AVAILABLE, AssetPackErrorCode.ACCESS_DENIED,
    AssetPackErrorCode.UNRECOGNIZED_INSTALLATION -> PackFailure.StoreUnavailable
    else -> PackFailure.Unknown
}
