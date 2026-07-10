package com.equalizer.carservice

import android.os.Bundle
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand

@UnstableApi
class SongDetailScreen(
    carContext: CarContext,
    private val playControl: PlayControl,
    private val mediaItem: MediaItem
) : Screen(carContext) {

    private val invalidateListener = { invalidate() }

    init {
        // Listen for playback changes to refresh the Play/Pause button state
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                playControl.addInvalidateListener(invalidateListener)
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                playControl.removeInvalidateListener(invalidateListener)
            }
        })
    }


    private fun createCarIcon(uriString: String?): CarIcon {
        if (uriString != null) {
            val finalUri = if (uriString.startsWith("content://${com.equalizer.common.MediaThumbnailProvider.getAuthority(carContext)}")) {
                uriString.toUri()
            } else {
                com.equalizer.common.MediaThumbnailProvider.getArtworkUri(carContext, uriString)
            }
            return CarIcon.Builder(IconCompat.createWithContentUri(finalUri)).build()
        }
        return CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_artist)).build()
    }

    override fun onGetTemplate(): Template {
        val metadata = mediaItem.mediaMetadata
        val isPlaying = playControl.isPlaying == PlayControl.Status.PLAYING
        
        val isFavourite = playControl.favourites.contains(mediaItem.mediaId)
        android.util.Log.d("SongDetailScreen", "onGetTemplate: title=${metadata.title} id=${mediaItem.mediaId} isFav=$isFavourite")

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
        val favAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext,
                if (isFavourite) com.equalizer.common.R.drawable.ic_favourite else com.equalizer.common.R.drawable.ic_favourite_border
            ))
                .setTint(if (isFavourite) CarColor.RED else CarColor.DEFAULT)
                .build())
            .setOnClickListener {
                val extras = Bundle().apply {
                    putString("SONG_ID", mediaItem.mediaId)
                }
                val customCommand = SessionCommand("toggleFavourite", Bundle())
                if (playControl.mediaControllerFuture.isDone) {
                    playControl.controller.sendCustomCommand(customCommand, extras)
                }
            }
            .build()

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

        paneBuilder.addAction(favAction)
        paneBuilder.addAction(playPauseAction)

        val builder = PaneTemplate.Builder(paneBuilder.build())
        
        if (carContext.carAppApiLevel >= 5) {
            builder.setHeader(
                Header.Builder()
                    .setTitle("Playback Control")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        } else {
            try {
                builder.setTitle("Playback Control")
                builder.setHeaderAction(Action.BACK)
            } catch (_: Exception) {}
        }

        return builder.build()
    }
}
