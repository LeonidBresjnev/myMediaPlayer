package com.equalizer.mymediaplayer

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import androidx.compose.material3.Surface
import coil.compose.AsyncImage
import coil.request.ImageRequest

@UnstableApi
@Composable
fun PlayerDisplay(
    modifier: Modifier,
    audioModel: AudioModel
) {
    val controller by audioModel.mediaController.observeAsState()
    val context = LocalContext.current
    
    // Reactive metadata state that updates via listener
    var currentMetadata by remember(controller) { 
        mutableStateOf(controller?.mediaMetadata ?: MediaMetadata.EMPTY) 
    }

    // Add listener to update metadata state when track/metadata changes
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                Log.d("PlayerDisplay", "Metadata changed: ${metadata.title} art: ${metadata.artworkUri}")
                currentMetadata = metadata
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d("PlayerDisplay", "Track transition: ${mediaItem?.mediaMetadata?.title}")
                currentMetadata = controller?.mediaMetadata ?: MediaMetadata.EMPTY
            }
        }
        controller?.addListener(listener)
        onDispose {
            controller?.removeListener(listener)
        }
    }

    Surface(
        modifier = modifier,
        color = Color.DarkGray
    ) {
        if (controller != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                // LAYER 1: Artwork via Coil (Observes currentMetadata)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentMetadata.artworkUri)
                        .crossfade(500)
                        .build(),
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                    error = painterResource(id = android.R.drawable.ic_menu_gallery)
                )

                // LAYER 2: Transparent Media3 Controller Overlay
                AndroidView(
                    factory = { ctx ->
                        PlayerControlView(ctx).apply {
                            this.player = controller
                            this.showTimeoutMs = 0 // Keep controls visible
                            this.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            this.show()
                        }
                    },
                    update = { view ->
                        view.player = controller
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Waiting for player...", color = Color.White)
            }
        }
    }
}
