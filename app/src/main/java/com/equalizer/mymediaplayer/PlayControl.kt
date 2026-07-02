package com.equalizer.mymediaplayer

import android.view.View.VISIBLE
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import androidx.compose.material3.Surface

@UnstableApi
@Composable
fun PlayControl(
    modifier: Modifier,
    audioModel: AudioModel
) {
    val controller by audioModel.mediaController.observeAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .height(100.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        if (controller != null) {
            AndroidView(
                factory = { context ->
                    PlayerControlView(context).apply {
                        this.player = controller
                        this.showTimeoutMs = 0

                        // FORCE the buttons to stay on screen
                        this.setShowNextButton(true)
                        this.setShowPreviousButton(true)
                        this.visibility = VISIBLE
                        this.show()
                        
                        // Optional: hide rewind/ff if you don't use them
                        this.setShowRewindButton(false)
                        this.setShowFastForwardButton(false)

                        // Follow Primary Blue theme
                        this.setBackgroundColor(primaryColor.toArgb())
                    }
                },
                update = { view ->
                    view.player = controller
                    view.setBackgroundColor(primaryColor.toArgb())
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Connecting to Player...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
