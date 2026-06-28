package com.equalizer.carservice

import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.MoreExecutors
import java.util.Locale

@OptIn(UnstableApi::class)
class MainTabScreen(
    carContext: CarContext,
    private val playControl: PlayControl
) : Screen(carContext) {

    private var activeTabId = "library"
    private var mediaItems: List<MediaItem> = emptyList()
    private var isLoading = true
    
    // Task step tracking
    private var taskStepCount = 0

    init {
        loadAlbums()
        
        // Listen for real-time frequency changes from phone/engine
        playControl.setVolPerFreqSetter0 {
            Log.d("MainTabScreen", "Frequency update received: $it")
            invalidate()
        }
    }

    private fun loadAlbums() {
        if (playControl.mediaControllerFuture.isDone) {
            val controller = playControl.controller
            val childrenFuture = controller.getChildren("root", 0, Int.MAX_VALUE, null)
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
                        Log.d("Car -Main Tab Screen", it)
                    }
                    invalidate()
                }
            }, MoreExecutors.directExecutor())
        } else {
            playControl.mediaControllerFuture.addListener({
                loadAlbums()
            }, MoreExecutors.directExecutor())
        }
    }

    private fun createCarIcon(metadata: androidx.media3.common.MediaMetadata, isBrowsable: Boolean): CarIcon {
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
        
        // VISUAL REFINEMENT: Use distinct icons for folders vs songs
        return if (isBrowsable) {
            CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.lever_vert)).build()
        } else {
            CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_artist)).build()
        }
    }

    override fun onGetTemplate(): Template {
        playControl.setInvalidate0 {
            invalidate()
        }

        // SAFETY: If we are about to hit the 5th step, show a message template to reset the task.
        if (taskStepCount >= 4) {
            return MessageTemplate.Builder("Safety Limit: Please refresh to continue.")
                .setHeaderAction(Action.BACK)
                .addAction(Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener {
                        taskStepCount = 0
                        invalidate()
                    }
                    .build())
                .build()
        }

        val libraryTab = Tab.Builder()
            .setTitle("Library")
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.play_solid)).build())
            .setContentId("library")
            .build()

        val eqTab = Tab.Builder()
            .setTitle("Equalizer")
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.lever_vert)).build())
            .setContentId("equalizer")
            .build()

        val currentContent: Template = if (activeTabId == "library") {
            createAlbumGridTemplate()
        } else {
            createEqualizerTemplate()
        }

        return TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabTag: String) {
                activeTabId = tabTag
                taskStepCount = 0 // Reset steps on tab switch
                invalidate()
            }
        })
        .addTab(libraryTab)
        .addTab(eqTab)
        .setActiveTabContentId(activeTabId)
        .setTabContents(TabContents.Builder(currentContent).build())
        .setHeaderAction(Action.APP_ICON)
        .build()
    }

    private fun createAlbumGridTemplate(): Template {
        val builder = GridTemplate.Builder()
        if (isLoading) return builder.setLoading(true).build()

        val gridBuilder = ItemList.Builder().setNoItemsMessage("No albums found")

        mediaItems.forEach { item ->
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle(item.mediaMetadata.title ?: "Unknown")
                    .setText(item.mediaMetadata.artist ?: "")
                    .setImage(createCarIcon(item.mediaMetadata, true), GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener {
                        taskStepCount++
                        screenManager.push(SongListScreen(carContext, playControl, item.mediaId, item.mediaMetadata.title?.toString() ?: "Album"))
                    }
                    .build()
            )
        }

        return builder.setSingleList(gridBuilder.build()).build()
    }

    private fun createEqualizerTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // Build frequency rows. Tapping opens detail screen with +/- buttons.
        for (i in 0 until 8) {
            val vol = playControl.volPerFreq[i]
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(playControl.frequencyLabels[i])
                    .addText("Volume: ${String.format(Locale.GERMAN, "%.1f", vol)}")
                    .setOnClickListener {
                        taskStepCount++
                        screenManager.push(BandDetailScreen(carContext, playControl, i))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .build()
    }
}
