package com.equalizer.carservice

import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.MoreExecutors

@OptIn(UnstableApi::class)
class SongListScreen(
    carContext: CarContext,
    private val playControl: PlayControl,
    private val albumId: String,
    private val albumTitle: String
) : Screen(carContext) {

    private var mediaItems: List<MediaItem> = emptyList()
    private var isLoading = true

    init {
        loadSongs()
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

    private fun createCarIcon(metadata: androidx.media3.common.MediaMetadata): CarIcon {
        metadata.artworkUri?.let { uri ->
            val uriString = uri.toString()
            val finalUri = if (uriString.startsWith("content://${com.equalizer.common.MediaThumbnailProvider.AUTHORITY}")) {
                uri
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
        val builder = ListTemplate.Builder()
        builder.setHeader(
            Header.Builder()
                .setTitle(albumTitle)
                .setStartHeaderAction(Action.BACK)
                .build()
        )

        if (isLoading) return builder.setLoading(true).build()

        val listBuilder = ItemList.Builder().setNoItemsMessage("No songs found")

        mediaItems.forEach { item ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(item.mediaMetadata.title ?: "Unknown")
                    .addText(item.mediaMetadata.artist ?: "")
                    .setImage(createCarIcon(item.mediaMetadata), Row.IMAGE_TYPE_SMALL)
                    .setOnClickListener {
                        screenManager.push(SongDetailScreen(carContext, playControl, item))
                    }
                    .build()
            )
        }

        return builder.setSingleList(listBuilder.build()).build()
    }
}
