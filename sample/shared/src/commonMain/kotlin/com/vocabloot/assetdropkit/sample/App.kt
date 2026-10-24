package com.vocabloot.assetdropkit.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.vocabloot.assetdropkit.AssetPacks
import com.vocabloot.assetdropkit.PackState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap

const val DEMO_PACK = "demo"

/** One screen: the demo pack's state, a button to fetch it, and its image once Ready. */
@Composable
fun App(packs: AssetPacks, onNeedsUser: () -> Unit = {}) {
    val state by remember { packs.state(DEMO_PACK) }.collectAsState(initial = null)
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var greeting by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) {
        if (state == PackState.Ready) {
            image = packs.readBytes(DEMO_PACK, "image.png")?.decodeToImageBitmap()
            greeting = packs.readBytes(DEMO_PACK, "hello.txt")?.decodeToString()?.trim()
        } else {
            image = null
            greeting = null
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("AssetDropKit sample", style = MaterialTheme.typography.headlineSmall)
            Text("Pack \"$DEMO_PACK\": ${state?.label() ?: "asking the store"}")
            (state as? PackState.Downloading)?.let { downloading ->
                val total = downloading.totalBytes
                if (total != null && total > 0) {
                    LinearProgressIndicator(progress = { downloading.bytes.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Button(onClick = {
                scope.launch {
                    val result = packs.fetch(DEMO_PACK)
                    if (result == PackState.WaitingForWifi || result == PackState.NeedsPlayConfirmation) onNeedsUser()
                }
            }) { Text("Get the demo pack") }
            OutlinedButton(onClick = { scope.launch { packs.remove(DEMO_PACK) } }) { Text("Remove it") }
            greeting?.let { Text(it) }
            image?.let { Image(bitmap = it, contentDescription = "The demo pack's image", modifier = Modifier.size(240.dp)) }
        }
    }
}

private fun PackState.label(): String = when (this) {
    PackState.NotDownloaded -> "not downloaded"
    PackState.Pending -> "queued"
    is PackState.Downloading -> "downloading ${bytes / 1024} KB" + (totalBytes?.let { " of ${it / 1024} KB" } ?: "")
    PackState.Transferring -> "unpacking"
    PackState.WaitingForWifi -> "waiting for Wi-Fi"
    PackState.NeedsPlayConfirmation -> "needs Play's confirmation"
    PackState.Ready -> "ready"
    is PackState.Failed -> "failed: $reason" + (detail?.let { " ($it)" } ?: "")
}
