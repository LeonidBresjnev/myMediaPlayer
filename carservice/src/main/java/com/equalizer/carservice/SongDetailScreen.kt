package com.equalizer.carservice

import android.os.Bundle
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Template
import androidx.annotation.OptIn
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.media.model.MediaPlaybackTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand

@OptIn(ExperimentalCarApi::class)
@UnstableApi
class SongDetailScreen(
    carContext: CarContext,
    private val playControl: PlayControl,
    private val mediaItem: MediaItem
) : Screen(carContext) {

    private val invalidateListener = { invalidate() }

    init {
        playControl.addInvalidateListener(invalidateListener)
        playControl.registerToken()
    }

    override fun onGetTemplate(): Template {
        // Use current media item if it's available, otherwise fallback to the one passed in
        val effectiveItem = playControl.currentMediaItem ?: mediaItem
        val isFavourite = playControl.favourites.contains(effectiveItem.mediaId)
        
        val favAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext,
                if (isFavourite) com.equalizer.common.R.drawable.ic_favourite else com.equalizer.common.R.drawable.ic_favourite_border
            ))
                .setTint(if (isFavourite) CarColor.RED else CarColor.DEFAULT)
                .build())
            .setOnClickListener {
                val extras = Bundle().apply {
                    putString("SONG_ID", effectiveItem.mediaId)
                }
                val customCommand = SessionCommand("toggleFavourite", Bundle())
                if (playControl.mediaControllerFuture.isDone) {
                    playControl.controller.sendCustomCommand(customCommand, extras)
                }
            }
            .build()

        val infoAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_info_details)).build())
            .setOnClickListener {
                screenManager.push(TechnicalInfoScreen(carContext, effectiveItem))
            }
            .build()

        return MediaPlaybackTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Now Playing")
                    .setStartHeaderAction(Action.BACK)
                    .addEndHeaderAction(favAction)
                    .addEndHeaderAction(infoAction)
                    .build()
            )
            .build()
    }
}
