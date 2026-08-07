package com.equalizer.carservice

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridSection
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.RowSection
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.car.app.annotations.RequiresCarApi
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import com.equalizer.common.MediaThumbnailProvider
import com.google.common.util.concurrent.MoreExecutors
import java.util.Locale

@RequiresCarApi(6)
@OptIn(UnstableApi::class, ExperimentalCarApi::class)
class MainTabScreen(
    carContext: CarContext,
    private val playControl: PlayControl
) : Screen(carContext) {

    private var activeTabId = "playlists"
    private var mediaItems: List<MediaItem> = emptyList()
    private var isLoading = true
    private var hasPushedInitialPlayback = false

    private val invalidateListener = {
        // Auto-push to full screen player when music starts, so tabs are not visible
        if (!hasPushedInitialPlayback && playControl.isPlaying == PlayControl.Status.PLAYING) {
            playControl.currentMediaItem?.let {
                hasPushedInitialPlayback = true
                if (screenManager.top === this) {
                    screenManager.push(SongDetailScreen(carContext, playControl, it))
                }
            }
        }
        invalidate()
    }

    fun handleIntent(intent: Intent) {
        if (playControl.isPlaying == PlayControl.Status.PLAYING) {
            playControl.currentMediaItem?.let {
                if (screenManager.top !is SongDetailScreen) {
                    screenManager.push(SongDetailScreen(carContext, playControl, it))
                }
            }
        }
        invalidate()
    }


    init {
        checkPermissionsAndLoad()
        
        playControl.setVolPerFreqSetter0 {
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
        val permissions = listOf(android.Manifest.permission.READ_MEDIA_AUDIO)
        carContext.requestPermissions(permissions) { granted, _ ->
            if (granted.containsAll(permissions)) {
                loadMediaItems("playlists_root")
            } else {
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
            
            if (parentId == "library_combined") {
                val musicFuture = controller.getChildren("music_library_root", 0, Int.MAX_VALUE, null)
                musicFuture.addListener({
                    try {
                        val musicResult = musicFuture.get().value ?: emptyList()
                        val radioFuture = controller.getChildren("icecast_root", 0, 15, null)
                        radioFuture.addListener({
                            try {
                                val radioResult = radioFuture.get().value ?: emptyList()
                                mediaItems = musicResult + radioResult
                                isLoading = false
                                invalidate()
                            } catch (e: Exception) {
                                mediaItems = musicResult
                                isLoading = false
                                invalidate()
                            }
                        }, MoreExecutors.directExecutor())
                    } catch (e: Exception) {
                        isLoading = false
                        invalidate()
                    }
                }, MoreExecutors.directExecutor())
            } else {
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
                        invalidate()
                    }
                }, MoreExecutors.directExecutor())
            }
        } else {
            playControl.mediaControllerFuture.addListener({
                loadMediaItems(parentId)
            }, MoreExecutors.directExecutor())
        }
    }

    private fun createCarIcon(metadata: MediaMetadata): CarIcon {
        metadata.artworkUri?.let { uri ->
            val uriString = uri.toString()
            if (uriString.startsWith("android.resource://")) {
                if (uriString.contains("ic_icecast")) {
                    return CarIcon.Builder(IconCompat.createWithResource(carContext, com.equalizer.common.R.drawable.ic_icecast)).build()
                }
            }

            val finalUri = if (uriString.startsWith("content://${MediaThumbnailProvider.getAuthority(carContext)}")) {
                uri
            } else if (uriString.isNotEmpty()) {
                MediaThumbnailProvider.getArtworkUri(carContext, uriString)
            } else null

            return if (finalUri != null) {
                CarIcon.Builder(IconCompat.createWithContentUri(finalUri)).build()
            } else {
                CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_artist)).build()
            }
        }

        return if (metadata.isBrowsable == true) {
            CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_album)).build()
        } else {
            CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_artist)).build()
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
            "playlists" -> createSectionedContent()
            "library" -> createSectionedContent()
            else -> createEqualizerTemplate()
        }

        val tabTemplateBuilder = TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabTag: String) {
                if (activeTabId != tabTag) {
                    activeTabId = tabTag
                    when (tabTag) {
                        "playlists" -> loadMediaItems("playlists_root")
                        "library" -> loadMediaItems("library_combined")
                    }
                    invalidate()
                }
            }
        })
        
        tabTemplateBuilder.addTab(playlistsTab).addTab(libraryTab).addTab(eqTab)
        
        return tabTemplateBuilder
            .setActiveTabContentId(activeTabId)
            .setTabContents(TabContents.Builder(currentContent).build())
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun createSectionedContent(): Template {
        val builder = SectionedItemTemplate.Builder()
        if (isLoading) return builder.setLoading(true).build()

        if (mediaItems.isEmpty()) {
            val sectionBuilder = RowSection.Builder()
            sectionBuilder.addItem(Row.Builder().setTitle("No items found").setEnabled(false).build())
            builder.addSection(sectionBuilder.build())
            return builder.build()
        }

        val folders = mediaItems.filter { it.mediaMetadata.isBrowsable == true }
        val radioStations = mediaItems.filter { it.mediaMetadata.extras?.getBoolean("IS_RADIO") == true }
        val songs = mediaItems.filter { it.mediaMetadata.isBrowsable != true && it.mediaMetadata.extras?.getBoolean("IS_RADIO") != true }

        if (folders.isNotEmpty()) {
            val gridSectionBuilder = GridSection.Builder().setTitle("Folders")
            folders.forEach { item ->
                gridSectionBuilder.addItem(GridItem.Builder()
                    .setTitle(item.mediaMetadata.title ?: "Unknown")
                    .setImage(createCarIcon(item.mediaMetadata), GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener { screenManager.push(SongListScreen(carContext, playControl, item.mediaId, item.mediaMetadata.title?.toString() ?: "Folder")) }
                    .build())
            }
            builder.addSection(gridSectionBuilder.build())
        }

        if (radioStations.isNotEmpty()) {
            val radioSectionBuilder = RowSection.Builder().setTitle("Radio Stations")
            radioStations.forEach { item ->
                radioSectionBuilder.addItem(Row.Builder()
                    .setTitle(item.mediaMetadata.title ?: "Unknown Station")
                    .addText(item.mediaMetadata.subtitle ?: "Live Radio")
                    .setImage(createCarIcon(item.mediaMetadata), Row.IMAGE_TYPE_SMALL)
                    .setOnClickListener {
                        playControl.playMedia(item)
                        screenManager.push(SongDetailScreen(carContext, playControl, item))
                    }
                    .build())
            }
            builder.addSection(radioSectionBuilder.build())
        }

        if (songs.isNotEmpty()) {
            val songSectionBuilder = RowSection.Builder().setTitle("Songs")
            songs.forEach { item ->
                songSectionBuilder.addItem(Row.Builder()
                    .setTitle(item.mediaMetadata.title ?: "Unknown")
                    .addText(item.mediaMetadata.artist ?: "")
                    .setImage(createCarIcon(item.mediaMetadata), Row.IMAGE_TYPE_SMALL)
                    .setOnClickListener {
                        playControl.playMedia(item)
                        screenManager.push(SongDetailScreen(carContext, playControl, item))
                    }
                    .build())
            }
            builder.addSection(songSectionBuilder.build())
        }

        return builder.build()
    }

    private fun createEqualizerTemplate(): Template {
        val listBuilder = ItemList.Builder()
        listBuilder.addItem(Row.Builder()
                .setTitle("Equalizer Mode: ${if (playControl.isAdvancedMode) "Advanced" else "Basic"}")
                .addText("Tap to switch mode")
                .setOnClickListener {
                    playControl.isAdvancedMode = !playControl.isAdvancedMode
                    val extras = Bundle().apply { putBoolean("IS_ADVANCED", playControl.isAdvancedMode) }
                    if (playControl.mediaControllerFuture.isDone) {
                        playControl.controller.sendCustomCommand(SessionCommand("setEqMode", Bundle()), extras)
                    }
                    invalidate()
                }
                .build())

        val maxBands = if (playControl.isAdvancedMode) 16 else 8
        for (i in 0 until maxBands) {
            val vol = playControl.volPerFreq[i]
            val bandName = playControl.frequencyLabels[i % 8]
            val title = if (playControl.isAdvancedMode) "${if (i < 8) "Left" else "Right"}: $bandName" else bandName
            listBuilder.addItem(Row.Builder()
                    .setTitle(title)
                    .addText("Volume: ${String.format(Locale.GERMAN, "%.1f", vol)}")
                    .setOnClickListener { screenManager.push(BandDetailScreen(carContext, playControl, i)) }
                    .build())
        }
        return ListTemplate.Builder().setSingleList(listBuilder.build()).build()
    }
}
