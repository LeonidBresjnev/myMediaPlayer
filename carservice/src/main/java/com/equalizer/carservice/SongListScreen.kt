package com.equalizer.carservice

import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Template
import androidx.car.app.model.CarIconSpan
import android.text.SpannableString
import android.text.Spanned
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.MoreExecutors

@OptIn(UnstableApi::class, ExperimentalCarApi::class)
class SongListScreen(
    carContext: CarContext,
    private val playControl: PlayControl,
    private val albumId: String,
    private val albumTitle: String
) : Screen(carContext) {

    private var mediaItems: List<MediaItem> = emptyList()
    private var isLoading = true

    private val invalidateListener = { invalidate() }

    init {
        loadSongs()
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                playControl.addInvalidateListener(invalidateListener)
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                playControl.removeInvalidateListener(invalidateListener)
            }
        })
    }

    private fun loadSongs() {
        if (playControl.mediaControllerFuture.isDone) {
            val controller = playControl.controller
            val childrenFuture = controller.getChildren(albumId, 0, Int.MAX_VALUE, null)
            childrenFuture.addListener({
                try {
                    val result = childrenFuture.get()
                    if (result.value != null) {
                        mediaItems = result.value!!
                        isLoading = false
                        invalidate()
                    }
                } catch (e: Exception) {
                    isLoading = false
                    e.message?.let {
                        Log.d("Car: SongList Screen", it)
                    }
                    invalidate()
                }
            }, MoreExecutors.directExecutor())
        } else {
            playControl.mediaControllerFuture.addListener({
                loadSongs()
            }, MoreExecutors.directExecutor())
        }
    }

    private fun createCarIcon(metadata: MediaMetadata): CarIcon {
        metadata.artworkUri?.let { uri ->
            val uriString = uri.toString()
            val finalUri = if (uriString.startsWith("content://${com.equalizer.common.MediaThumbnailProvider.getAuthority(carContext)}")) {
                uri
            } else {
                com.equalizer.common.MediaThumbnailProvider.getArtworkUri(carContext, uriString)
            }
            return CarIcon.Builder(IconCompat.createWithContentUri(finalUri)).build()
        }
        
        // Use Media3 built-in icon for songs
        return CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_artist)).build()
    }

    override fun onGetTemplate(): Template {
        val isCurrentlyPlaying = playControl.isPlaying == PlayControl.Status.PLAYING
        val isStopped = playControl.isPlaying == PlayControl.Status.STOPPED

        val playAllAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(
                carContext,
                if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )).build())
            .setOnClickListener {
                if (playControl.mediaControllerFuture.isDone) {
                    val controller = playControl.controller
                    if (isCurrentlyPlaying) {
                        controller.pause()
                    } else {
                        if (mediaItems.isNotEmpty()) {
                            controller.setMediaItems(mediaItems, 0, 0L)
                            controller.prepare()
                            controller.play()
                        } else {
                            controller.play()
                        }
                    }
                    invalidate()
                }
            }
            .build()

        val nowPlayingAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(
                carContext,
                androidx.media3.session.R.drawable.media3_notification_small_icon
            )).build())
            .setOnClickListener {
                if (playControl.mediaControllerFuture.isDone) {
                    val currentItem = playControl.controller.currentMediaItem
                    if (currentItem != null) {
                        screenManager.push(SongDetailScreen(carContext, playControl, currentItem))
                    }
                }
            }
            .setEnabled(!isStopped)
            .build()

        val builder = SectionedItemTemplate.Builder()
        
        builder.setHeader(
            Header.Builder()
                .setTitle(albumTitle)
                .setStartHeaderAction(Action.BACK)
                .addEndHeaderAction(playAllAction)
                .addEndHeaderAction(nowPlayingAction)
                .build()
        )

        if (isLoading) return builder.setLoading(true).build()

        val rowSectionBuilder = RowSection.Builder().setTitle("Songs")
        var hasItems = false

        mediaItems.forEach { item ->
            hasItems = true
            val isFavourite = playControl.favourites.contains(item.mediaId)
            val isThisItemActive = if (playControl.mediaControllerFuture.isDone) {
                playControl.controller.currentMediaItem?.mediaId == item.mediaId && 
                playControl.isPlaying != PlayControl.Status.STOPPED
            } else false

            val isThisPlaying = isThisItemActive && playControl.isPlaying == PlayControl.Status.PLAYING

            val title = item.mediaMetadata.title ?: "Unknown"
            val displayTitle: CharSequence = if (isThisItemActive) {
                SpannableString("  $title").apply {
                    val iconRes = if (isThisPlaying) 
                        androidx.media3.session.R.drawable.media3_icon_circular_play 
                    else 
                        androidx.media3.session.R.drawable.media3_icon_pause
                    
                    val playIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build()
                    setSpan(CarIconSpan.create(playIcon, CarIconSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                }
            } else {
                title
            }

            val artist = item.mediaMetadata.artist ?: ""
            val displayArtist: CharSequence = if (isThisItemActive) {
                val status = if (isThisPlaying) "Now Playing" else "Paused"
                "$artist • $status"
            } else {
                artist
            }

            val favAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext,
                    if (isFavourite) com.equalizer.common.R.drawable.ic_favourite else com.equalizer.common.R.drawable.ic_favourite_border
                ))
                    .setTint(if (isFavourite) CarColor.RED else CarColor.DEFAULT)
                    .build())
                .setOnClickListener {
                    val extras = Bundle().apply {
                        putString("SONG_ID", item.mediaId)
                    }
                    val customCommand = SessionCommand("toggleFavourite", Bundle())
                    if (playControl.mediaControllerFuture.isDone) {
                        playControl.controller.sendCustomCommand(customCommand, extras)
                    }
                }
                .build()

            val playAction = Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext,
                    if (isThisPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )).build())
                .setOnClickListener {
                    if (playControl.mediaControllerFuture.isDone) {
                        val controller = playControl.controller
                        if (isThisPlaying) {
                            controller.pause()
                        } else {
                            if (controller.currentMediaItem?.mediaId == item.mediaId) {
                                controller.play()
                            } else {
                                controller.setMediaItem(item)
                                controller.prepare()
                                controller.play()
                            }
                        }
                        invalidate()
                    }
                }
                .build()

            val rowBuilder = Row.Builder()
                .setTitle(displayTitle)
                .addText(displayArtist)

            rowSectionBuilder.addItem(
                rowBuilder
                    .setImage(createCarIcon(item.mediaMetadata), Row.IMAGE_TYPE_SMALL)
                    .addAction(favAction)
                    .addAction(playAction)
                    .setOnClickListener {
                        playControl.playMedia(item)
                        screenManager.push(SongDetailScreen(carContext, playControl, item))
                    }
                    .build()
            )
        }

        if (!hasItems) {
            rowSectionBuilder.addItem(Row.Builder().setTitle("No songs found").setEnabled(false).build())
        }

        builder.addSection(rowSectionBuilder.build())

        return builder.build()
    }
}
