package com.equalizer.carservice
/*
import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.util.UnstableApi
import androidx.core.net.toUri

@OptIn(UnstableApi::class)
class ModernPlaybackScreen(
    carContext: CarContext,
    private val playControl: PlayControl
) : Screen(carContext) {

    private fun createCarIcon(uriString: String?): CarIcon {
        if (uriString != null) {
            val finalUri = if (uriString.startsWith("content://${com.equalizer.common.MediaThumbnailProvider.AUTHORITY}")) {
                uriString.toUri()
            } else {
                com.equalizer.common.MediaThumbnailProvider.CONTENT_URI.buildUpon()
                    .appendQueryParameter("path", uriString)
                    .build()
            }
            return CarIcon.Builder(IconCompat.createWithContentUri(finalUri)).build()
        }
        return CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.ui.R.drawable.media3_icon_music)).build()
    }

    override fun onGetTemplate(): Template {
        playControl.setInvalidate0 {
            invalidate()
        }

        val controller = if (playControl.mediaControllerFuture.isDone) playControl.controller else null
        val currentItem = controller?.currentMediaItem
        val metadata = currentItem?.mediaMetadata

        val paneBuilder = Pane.Builder()
        
        val songRow = Row.Builder()
            .setTitle(metadata?.title ?: "Not Playing")
            .addText(metadata?.artist ?: "Select a song to begin")
            .setImage(createCarIcon(metadata?.artworkUri?.toString()), Row.IMAGE_TYPE_SMALL)
            .build()
        
        paneBuilder.addRow(songRow)

        val isPlaying = playControl.isPlaying == PlayControl.Status.PLAYING

       val playPauseAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext,
                if (isPlaying) R.drawable.stopplay else R.drawable.play_solid
            )).build())
            .setOnClickListener {
                if (isPlaying) {
                    controller?.pause()
                } else {
                    controller?.play()
                }
            }
            .build()

        paneBuilder.addAction(playPauseAction)

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Now Playing")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
*/