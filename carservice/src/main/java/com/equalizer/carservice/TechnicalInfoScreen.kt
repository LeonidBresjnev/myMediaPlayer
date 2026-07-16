package com.equalizer.carservice

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

@UnstableApi
class TechnicalInfoScreen(
    carContext: CarContext,
    private val mediaItem: MediaItem
) : Screen(carContext) {

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
        val paneBuilder = Pane.Builder()

        // Basic Info
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(metadata.title ?: "Unknown Title")
                .addText(metadata.artist ?: "Unknown Artist")
                .build()
        )

        // Technical Details
        // totalDiscCount is mapped to sampleRate in MyMediaService
        val sampleRate = metadata.totalDiscCount
        if (sampleRate != null && sampleRate > 0) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Sample Rate")
                    .addText("$sampleRate Hz")
                    .build()
            )
        }

        // releaseMonth is mapped to numChannels in MyMediaService
        val channels = metadata.releaseMonth
        if (channels != null && channels > 0) {
            val channelText = when (channels) {
                1 -> "Mono"
                2 -> "Stereo"
                else -> "$channels Channels"
            }
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Audio Channels")
                    .addText(channelText)
                    .build()
            )
        }

        paneBuilder.setImage(createCarIcon(metadata.artworkUri?.toString()))

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Technical Details")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
