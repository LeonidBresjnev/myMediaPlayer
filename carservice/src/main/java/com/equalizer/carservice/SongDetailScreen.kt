package com.equalizer.carservice

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
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

@UnstableApi
class SongDetailScreen(
    carContext: CarContext,
    private val playControl: PlayControl,
    private val mediaItem: MediaItem
) : Screen(carContext) {

    init {
        // Listen for playback changes to refresh the Play/Pause button state
        playControl.setInvalidate0 {
            invalidate()
        }
    }


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
        return CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_artist)).build()
    }

    override fun onGetTemplate(): Template {
        val metadata = mediaItem.mediaMetadata
        val isPlaying = playControl.isPlaying == PlayControl.Status.PLAYING
        
        val paneBuilder = Pane.Builder()
        
        // Detailed Information
        val infoRow = Row.Builder()
            .setTitle(metadata.title ?: "Unknown Title")
            .addText(metadata.artist ?: "Unknown Artist")
           /* .setImage(createCarIcon(metadata.artworkUri?.toString()), Row.IMAGE_TYPE_LARGE)*/
            .build()
        
        paneBuilder
            .setImage(createCarIcon(metadata.artworkUri?.toString()))
            .addRow(infoRow)
        
        // Technical Info if available
        metadata.totalDiscCount?.let { 
            paneBuilder.addRow(Row.Builder().setTitle("Sample Rate").addText("$it Hz").build())
        }
        metadata.releaseMonth?.let {
            paneBuilder.addRow(Row.Builder().setTitle("Channels").addText("$it").build())
        }

        // Integrated Toggle Action
        val playPauseAction = if (isPlaying) {
            Action.Builder()
                .setIcon(CarIcon
                    .Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_pause))
                    .build())
                .setOnClickListener {
                    if (playControl.mediaControllerFuture.isDone) {
                        playControl.controller.pause()
                    }
                }
                .build()
        } else {
            Action.Builder()
                .setIcon(CarIcon
                    .Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_circular_play))
                    .build())
                .setOnClickListener {
                    playControl.playMedia(mediaItem)
                }
                .build()
        }

        paneBuilder.addAction(playPauseAction)

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Playback Control")
                    .setStartHeaderAction(Action.BACK)
                   /* .addEndHeaderAction(eqAction)*/
                    .build()
            )


            .build()
    }
}
