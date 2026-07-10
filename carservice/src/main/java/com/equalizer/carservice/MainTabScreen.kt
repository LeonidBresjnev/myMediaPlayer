package com.equalizer.carservice

import android.os.Bundle
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
import androidx.car.app.annotations.RequiresCarApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import com.equalizer.common.MediaThumbnailProvider
import com.google.common.util.concurrent.MoreExecutors
import java.util.Locale

@RequiresCarApi(6)
@OptIn(UnstableApi::class)
class MainTabScreen(
    carContext: CarContext,
    private val playControl: PlayControl
) : Screen(carContext) {

    private var activeTabId = "playlists"
    private var mediaItems: List<MediaItem> = emptyList()
    private var isLoading = true

    private val invalidateListener = { invalidate() }


    init {
        checkPermissionsAndLoad()
        
        // Listen for real-time frequency changes from phone/engine
        playControl.setVolPerFreqSetter0 {
            Log.d("MainTabScreen", "Frequency update received: $it")
            invalidate()
        }
        
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                playControl.addInvalidateListener(invalidateListener)
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                playControl.removeInvalidateListener(invalidateListener)
            }
        })
    }

    private fun checkPermissionsAndLoad() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            listOf(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        carContext.requestPermissions(permissions) { granted, rejected ->
            if (granted.containsAll(permissions)) {
                loadMediaItems("playlists_root")
            } else {
                Log.e("MainTabScreen", "Permissions rejected: $rejected")
                // Fallback: try loading anyway or show error
                loadMediaItems("playlists_root")
            }
        }
    }

    private fun loadMediaItems(parentId: String) {
        isLoading = true
        mediaItems = emptyList()
        invalidate()

        if (playControl.mediaControllerFuture.isDone) {
            val controller = playControl.controller
            val childrenFuture = controller.getChildren(parentId, 0, Int.MAX_VALUE, null)
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
                        Log.d("MainTabScreen", it)
                    }
                    invalidate()
                }
            }, MoreExecutors.directExecutor())
        } else {
            playControl.mediaControllerFuture.addListener({
                loadMediaItems(parentId)
            }, MoreExecutors.directExecutor())
        }
    }

    private fun createCarIcon(metadata: MediaMetadata): CarIcon {

        metadata
            .artworkUri
            ?.let { uri ->
            val uriString = uri.toString()
            val finalUri = if (uriString.startsWith("content://${MediaThumbnailProvider.getAuthority(carContext)}")) {
                uri
            } else {
                MediaThumbnailProvider.getArtworkUri(carContext, uriString)
            }
            return CarIcon.Builder(IconCompat.createWithContentUri(finalUri)).build()
            }



            // Fallbacks using Media3 built-in icons
            return if (metadata.isBrowsable == true) {
                CarIcon
                    .Builder(
                        IconCompat
                            .createWithResource(
                                carContext,
                                androidx.media3.session.R.drawable.media3_icon_album
                            )
                    ).build()
            } else {
                CarIcon
                    .Builder(
                        IconCompat
                            .createWithResource(
                                carContext,
                                androidx.media3.session.R.drawable.media3_icon_artist
                            )
                    ).build()

}
    }

    override fun onGetTemplate(): Template {
        val playlistsTab = Tab.Builder()
            .setTitle("Playlists")
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_agenda)).build())
            .setContentId("playlists")
            .build()

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

        val currentContent: Template = when (activeTabId) {
            "playlists" -> createAlbumGridTemplate("No playlists found")
            "library" -> createAlbumGridTemplate("No albums found")
            else -> createEqualizerTemplate()
        }

        return TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabTag: String) {
                if (activeTabId != tabTag) {
                    activeTabId = tabTag
                    when (tabTag) {
                        "playlists" -> loadMediaItems("playlists_root")
                        "library" -> loadMediaItems("music_library_root")
                    }
                    invalidate()
                }
            }
        })
        .addTab(playlistsTab)
        .addTab(libraryTab)
        .addTab(eqTab)
        .setActiveTabContentId(activeTabId)
        .setTabContents(TabContents.Builder(currentContent).build())
        .setHeaderAction(Action.APP_ICON)
        .build()
    }

    private fun createAlbumGridTemplate(noItemsMessage: String): Template {
        val builder = GridTemplate.Builder()
        if (isLoading) return builder.setLoading(true).build()

        val gridBuilder = ItemList.Builder().setNoItemsMessage(noItemsMessage)

        mediaItems.forEach { item ->
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle(item.mediaMetadata.title ?: "Unknown")
                    .setText(item.mediaMetadata.artist ?: "")
                    .setImage(createCarIcon(item.mediaMetadata), GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener {
                        if (item.mediaMetadata.isBrowsable == true) {
                            screenManager
                                .push(SongListScreen(carContext, playControl, item.mediaId, item.mediaMetadata.title?.toString() ?: "Album"))
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

        // Mode Selector Row
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Equalizer Mode: ${if (playControl.isAdvancedMode) "Advanced" else "Basic"}")
                .addText("Tap to switch to ${if (playControl.isAdvancedMode) "Basic" else "Advanced"} mode")
                .setOnClickListener {
                    val newMode = !playControl.isAdvancedMode
                    playControl.isAdvancedMode = newMode
                    
                    val extras = Bundle().apply { putBoolean("IS_ADVANCED", newMode) }
                    if (playControl.mediaControllerFuture.isDone) {
                        playControl.controller.sendCustomCommand(SessionCommand("setEqMode", Bundle()), extras)
                    }
                    invalidate()
                }
                .build()
        )

        // Build frequency rows.
        val maxBands = if (playControl.isAdvancedMode) 16 else 8
        for (i in 0 until maxBands) {
            val vol = playControl.volPerFreq[i]
            val channelLabel = if (i < 8) "Left" else "Right"
            val bandName = playControl.frequencyLabels[i % 8]
            
            val title = if (playControl.isAdvancedMode) "$channelLabel: $bandName" else bandName

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(title)
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
}
