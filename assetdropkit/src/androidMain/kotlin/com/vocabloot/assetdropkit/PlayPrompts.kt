package com.vocabloot.assetdropkit

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory

/**
 * Play's two dialogs: mobile data for a large pack (WaitingForWifi) and Play's confirmation for
 * an install it does not recognise (NeedsPlayConfirmation). Fetch again after the user answers.
 */
public class PlayPrompts internal constructor(
    private val manager: AssetPackManager,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>,
) {
    /** Shows Play's dialog for every pack waiting on the user (either state); false when there is none. */
    public fun show(): Boolean = manager.showConfirmationDialog(launcher)
}

/** Registers Play's dialogs; call from the activity's `onCreate`, before it starts. */
@Suppress("UnusedReceiverParameter")
public fun AssetPacks.registerPlayPrompts(activity: ComponentActivity): PlayPrompts = PlayPrompts(
    manager = AssetPackManagerFactory.getInstance(activity),
    launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {},
)
