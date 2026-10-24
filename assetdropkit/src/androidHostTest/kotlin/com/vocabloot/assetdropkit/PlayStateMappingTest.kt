package com.vocabloot.assetdropkit

import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every Play status and failure code lands on one PackState, the same names as on iOS. */
class PlayStateMappingTest {

    private fun status(status: Int, bytes: Long = 0, total: Long = 0) = mapPlayStatus(status = status, errorCode = AssetPackErrorCode.NO_ERROR, bytes = bytes, total = total)
    private fun failure(errorCode: Int) = (mapPlayStatus(status = AssetPackStatus.FAILED, errorCode = errorCode, bytes = 0, total = 0) as PackState.Failed).reason

    @Test
    fun statuses() {
        assertEquals(PackState.Pending, status(AssetPackStatus.PENDING))
        assertEquals(PackState.Downloading(40, 100), status(AssetPackStatus.DOWNLOADING, bytes = 40, total = 100))
        assertEquals(PackState.Transferring, status(AssetPackStatus.TRANSFERRING))
        assertEquals(PackState.Ready, status(AssetPackStatus.COMPLETED))
        assertEquals(PackState.WaitingForWifi, status(AssetPackStatus.WAITING_FOR_WIFI))
        assertEquals(PackState.NeedsPlayConfirmation, status(AssetPackStatus.REQUIRES_USER_CONFIRMATION))
        assertEquals(PackState.NotDownloaded, status(AssetPackStatus.NOT_INSTALLED))
        assertEquals(PackState.Failed(PackFailure.Cancelled, null), status(AssetPackStatus.CANCELED))
        assertEquals(PackFailure.Unknown, (status(AssetPackStatus.UNKNOWN) as PackState.Failed).reason)
    }

    @Test
    fun anUnknownTotalIsNull() {
        assertEquals(PackState.Downloading(40, null), status(AssetPackStatus.DOWNLOADING, bytes = 40, total = 0))
    }

    @Test
    fun failureCodes() {
        assertEquals(PackFailure.NoNetwork, failure(AssetPackErrorCode.NETWORK_ERROR))
        assertEquals(PackFailure.InsufficientStorage, failure(AssetPackErrorCode.INSUFFICIENT_STORAGE))
        assertEquals(PackFailure.NotFound, failure(AssetPackErrorCode.PACK_UNAVAILABLE))
        assertEquals(PackFailure.NotFound, failure(AssetPackErrorCode.DOWNLOAD_NOT_FOUND))
        assertEquals(PackFailure.StoreUnavailable, failure(AssetPackErrorCode.APP_UNAVAILABLE))
        assertEquals(PackFailure.StoreUnavailable, failure(AssetPackErrorCode.APP_NOT_OWNED))
        assertEquals(PackFailure.StoreUnavailable, failure(AssetPackErrorCode.API_NOT_AVAILABLE))
        assertEquals(PackFailure.StoreUnavailable, failure(AssetPackErrorCode.ACCESS_DENIED))
        assertEquals(PackFailure.Unknown, failure(AssetPackErrorCode.INTERNAL_ERROR))
        assertEquals(PackFailure.Unknown, failure(AssetPackErrorCode.INVALID_REQUEST))
    }

    /** A sideload fails with UNRECOGNIZED_INSTALLATION; Play's confirmation dialog is the way out, never local files. */
    @Test
    fun anUnrecognisedInstallNeedsPlayConfirmation() {
        assertEquals(
            PackState.NeedsPlayConfirmation,
            mapPlayStatus(status = AssetPackStatus.FAILED, errorCode = AssetPackErrorCode.UNRECOGNIZED_INSTALLATION, bytes = 0, total = 0),
        )
    }
}
