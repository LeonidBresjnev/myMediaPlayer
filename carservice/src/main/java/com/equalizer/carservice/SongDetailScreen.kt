package com.equalizer.carservice

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

@UnstableApi
class SongDetailScreen(
    carContext: CarContext,
    private val playControl: PlayControl,
    private val mediaItem: MediaItem
) : Screen(carContext) {

    private fun createCarIcon(uriString: String?): CarIcon {
        if (uriString != null) {
            val finalUri = if (uriString.startsWith("content://${com.equalizer.common.MediaThumbnailProvider.AUTHORITY}")) {
                android.net.Uri.parse(uriString)
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
        
        val paneBuilder = Pane.Builder()
        
        // Detailed Information
        val infoRow = Row.Builder()
            .setTitle(metadata.title ?: "Unknown Title")
            .addText(metadata.artist ?: "Unknown Artist")
            .setImage(createCarIcon(metadata.artworkUri?.toString()), Row.IMAGE_TYPE_SMALL)
            .build()
        
        paneBuilder.addRow(infoRow)
        
        // Technical Info if available
        metadata.totalDiscCount?.let { 
            paneBuilder.addRow(Row.Builder().setTitle("Sample Rate").addText("${it} Hz").build())
        }
        metadata.releaseMonth?.let {
            paneBuilder.addRow(Row.Builder().setTitle("Channels").addText("${it}").build())
        }

        // Primary Play Action
        val playAction = Action.Builder()
            .setTitle("PLAY")
            .setBackgroundColor(CarColor.BLUE)
            .setOnClickListener {
                playControl.playMedia(mediaItem)
                screenManager.push(ModernPlaybackScreen(carContext, playControl))
            }
            .build()

        paneBuilder.addAction(playAction)

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(
                Header.Builder()
                    .setTitle("Song Details")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .build()
    }
}
