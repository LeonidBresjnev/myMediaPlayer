package com.equalizer.mymediaplayer

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@UnstableApi
@Composable
fun PlayerDisplay(
    modifier: Modifier,
    audioModel: AudioModel
) {
    val controller by audioModel.mediaController.observeAsState()
    val nextItem by audioModel.nextMediaItem.observeAsState()
    val playbackContext by audioModel.currentPlaybackContext.observeAsState()
    val playlists by audioModel.playlists.observeAsState(emptyList())

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

    val contextName = remember(playbackContext, playlists) {
        when {
            playbackContext == null -> "None"
            playbackContext == "music_library_root" -> "Music Library"
            playbackContext == "icecast_root" -> "Radio"
            playbackContext?.startsWith("playlist_") == true -> {
                playlists.find { it.mediaId == playbackContext }?.mediaMetadata?.title?.toString() ?: "Playlist"
            }
            else -> File(playbackContext!!).name
        }
    }

    val marqueeText = remember(currentMetadata, nextItem, contextName) {
        val artist = currentMetadata.artist?.toString() ?: ""
        val title = currentMetadata.title?.toString() ?: ""
        val station = currentMetadata.albumTitle?.toString() ?: ""
        
        val isRadio = contextName == "Radio"
        
        val nowPlaying = if (isRadio) {
            // Simplified Radio Logic: Always show what we have
            val info = listOfNotNull(
                artist.takeIf { it.isNotBlank() && it != station },
                title.takeIf { it.isNotBlank() && it != station }
            ).joinToString(" - ")
            
            if (info.isNotBlank()) {
                "STATION: $station  |  NOW PLAYING: $info"
            } else {
                "STATION: $station  |  NOW PLAYING: Live Stream"
            }
        } else {
            val titlePart = title.ifEmpty { "Unknown" }
            val artistPart = artist.ifEmpty { "Unknown Artist" }
            "NOW PLAYING: $titlePart - $artistPart"
        }
        
        val fromText = "  |  FROM: $contextName"
        val nextText = if (!isRadio) {
            nextItem?.let { "  |  UP NEXT: ${it.mediaMetadata.title ?: "Unknown"} - ${it.mediaMetadata.artist ?: "Unknown Artist"}" } ?: ""
        } else ""
        
        "$nowPlaying$fromText$nextText"
    }

    Surface(
        modifier = modifier,
        color = Color.White // Set white background for artwork
    ) {
        if (controller != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                // LAYER 1: Artwork via Coil (Observes currentMetadata)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentMetadata.artworkUri)
                        .crossfade(500)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .build(),
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize().padding(32.dp), // More padding for logos
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )

                // LAYER 2: Rolling Text Marquee
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = marqueeText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(
                                color = Color.Black,
                                blurRadius = 4f
                            )
                        ),
                        maxLines = 1
                    )
                }

                // LAYER 3: Transparent Media3 Controller Overlay
                AndroidView(
                    factory = { ctx ->
                        PlayerControlView(ctx).apply {
                            this.player = controller
                            this.showTimeoutMs = 0 // Keep controls visible
                            this.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            this.accessibilityPaneTitle = "current song"

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
