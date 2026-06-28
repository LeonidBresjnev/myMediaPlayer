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
    
    // Equalizer state
    //private var currentEqInterval = 0

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
                        Log.d("Car - Maintab", it)
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
        
        // Fallbacks using standard Android resources
        return CarIcon
            .Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_gallery))
            .build()
    }

    override fun onGetTemplate(): Template {
        playControl.setInvalidate0 {
            invalidate()
        }

        val libraryTab = Tab.Builder()
            .setTitle("Library")
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_gallery)).build())
            .setContentId("library")
            .build()

        val eqTab = Tab.Builder()
            .setTitle("Equalizer")
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_preferences)).build())
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
                    .setImage(createCarIcon(item.mediaMetadata), GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener {
                        if (item.mediaMetadata.isBrowsable == true) {
                            screenManager.push(SongListScreen(carContext, playControl, item.mediaId, item.mediaMetadata.title?.toString() ?: "Album"))
                        } else {
                            screenManager.push(SongDetailScreen(carContext, playControl, item))
                        }
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
                        screenManager.push(BandDetailScreen(carContext, playControl, i))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .build()
    }
/*
    private fun updateFrequency(index: Int, volume: Float) {
        playControl.volPerFreq[index] = volume
        invalidate()

        val extras = Bundle().apply {
            putInt("KEY_INDEX", index)
            putFloat("KEY_VOLUME", volume)
        }
        val customCommand = SessionCommand("setVolOnFreq", Bundle())
        if (playControl.mediaControllerFuture.isDone) {
            playControl.controller.sendCustomCommand(customCommand, extras)
        }
    }*/
}
