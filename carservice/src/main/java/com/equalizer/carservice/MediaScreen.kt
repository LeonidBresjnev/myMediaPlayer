package com.equalizer.carservice

import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import kotlin.math.max

class MediaScreen @OptIn(UnstableApi::class) constructor
    (
    carContext: CarContext,
    val playControl: PlayControl,
    initialPath: String = "root"
): Screen(carContext) {

    private var currentPath = initialPath
    private var mediaItems: List<MediaItem> = emptyList()
    private var currentSelection = -1
    
    // Pagination state
    private var pageOffset = 0
    private val PAGE_LIMIT = 5 
    
    // Task step tracking
    private var taskStepCount = 0

    init {
        browse(currentPath)
    }

    private fun browse(parentId: String) {
        if (playControl.mediaControllerFuture.isDone) {
            val controller = playControl.controller
            log("Browsing: $parentId")
            
            mediaItems = emptyList()
            currentSelection = -1
            pageOffset = 0 
            taskStepCount = 0 // Reset on new folder
            invalidate()

            val childrenFuture = controller.getChildren(parentId, 0, Int.MAX_VALUE, null)
            childrenFuture.addListener({
                try {
                    val result = childrenFuture.get()
                    if (result.value != null) {
                        currentPath = parentId
                        mediaItems = result.value!!
                        currentSelection = -1
                        invalidate()
                    }
                } catch (e: Exception) {
                    log("Error getting children: ${e.message}")
                }
            }, MoreExecutors.directExecutor())
        } else {
            playControl.mediaControllerFuture.addListener({
                browse(parentId)
            }, MoreExecutors.directExecutor())
        }
    }

    private fun log(msg: String) {
        Log.d("Car Media Screen", msg)
    }

    private fun createCarIcon( isBrowsable: Boolean): CarIcon {
        return if (isBrowsable) {
            CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.lever_vert)).build()
        } else {
            CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_artist)).build()
        }
    }

    @OptIn(UnstableApi::class)
    private fun createHeader(title: String): Header {
        val playPause = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, 
                if (playControl.isPlaying == PlayControl.Status.PLAYING) R.drawable.stopplay else R.drawable.play_solid
            )).build())
            .setOnClickListener {
                if (playControl.isPlaying == PlayControl.Status.PLAYING) {
                    playControl.controller.pause()
                } else if (currentSelection >= 0 && currentSelection < mediaItems.size) {
                    playControl.playMedia(mediaItems[currentSelection])
                }
            }
            .build()

        val eqAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.lever_vert)).build())
            .setOnClickListener {
                this.screenManager.push(EqualizerScreen(carContext, playControl))
            }
            .build()

        val headerBuilder = Header.Builder()
            .setTitle(title)
            .addEndHeaderAction(playPause)
            .addEndHeaderAction(eqAction)
        
        if (currentPath != "root") {
            headerBuilder.setStartHeaderAction(Action.BACK)
        } else {
            headerBuilder.setStartHeaderAction(Action.APP_ICON)
        }
        
        return headerBuilder.build()
    }

    @OptIn(UnstableApi::class)
    override fun onGetTemplate(): Template {
        playControl.setInvalidate0 {
            invalidate()
        }

        // SAFETY: If we are about to hit the 5th step, show a message template to reset the task.
        if (taskStepCount >= 4) {
            return MessageTemplate.Builder("Safety Limit: Please refresh to continue browsing.")
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

        val hasFolders = mediaItems.any { it.mediaMetadata.isBrowsable == true }

        return if (hasFolders || currentPath == "root") {
            createAlbumGridTemplate()
        } else {
            createSongListTemplate()
        }
    }

    private fun createAlbumGridTemplate(): Template {
        val gridBuilder = ItemList.Builder()
            .setNoItemsMessage("No albums found")

        if (pageOffset > 0) {
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle("Previous Page")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.play_solid)).build())
                    .setOnClickListener {
                        pageOffset = max(0, pageOffset - PAGE_LIMIT)
                        taskStepCount++
                        invalidate()
                    }
                    .build()
            )
        }

        val itemsToShow = mediaItems.drop(pageOffset).take(PAGE_LIMIT)
        itemsToShow.forEach { item ->
            val metadata = item.mediaMetadata
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle(metadata.title ?: "Unknown")
                    .setText(metadata.artist ?: "")
                    .setImage(createCarIcon( true), GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener {
                        if (metadata.isBrowsable == true) {
                            screenManager.push(MediaScreen(carContext, playControl, item.mediaId))
                        } else {
                            currentSelection = mediaItems.indexOf(item)
                            taskStepCount++
                            invalidate()
                        }
                    }
                    .build()
            )
        }

        if (pageOffset + itemsToShow.size < mediaItems.size) {
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle("Next Page")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.play_solid)).build())
                    .setOnClickListener {
                        pageOffset += PAGE_LIMIT
                        taskStepCount++
                        invalidate()
                    }
                    .build()
            )
        }

        return GridTemplate.Builder()
            .setHeader(createHeader("Music Library"))
            .setSingleList(gridBuilder.build())
            .build()
    }

    private fun createSongListTemplate(): Template {
        val itemListBuilder = ItemList.Builder()
            .setNoItemsMessage("No songs found")

        if (pageOffset > 0) {
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("Previous Page...")
                    .setOnClickListener {
                        pageOffset = max(0, pageOffset - PAGE_LIMIT)
                        taskStepCount++
                        invalidate()
                    }
                    .build()
            )
        }

        val itemsToShow = mediaItems.drop(pageOffset).take(PAGE_LIMIT)
        itemsToShow.forEach { item ->
            val idx = mediaItems.indexOf(item)
            val metadata = item.mediaMetadata
            val rowBuilder = Row.Builder()
                .setTitle(metadata.title ?: "Unknown")
                .setBrowsable(metadata.isBrowsable ?: false)

            metadata.artist?.let {
                val artistText = SpannableString(it)
                if (idx == currentSelection) {
                    artistText.setSpan(
                        ForegroundCarColorSpan.create(CarColor.GREEN),
                        0, artistText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                rowBuilder.addText(artistText)
            }

            rowBuilder.setImage(createCarIcon( false), Row.IMAGE_TYPE_SMALL)

            rowBuilder.setOnClickListener {
                if (metadata.isBrowsable == true) {
                    screenManager.push(MediaScreen(carContext, playControl, item.mediaId))
                } else {
                    currentSelection = idx
                    taskStepCount++
                    invalidate()
                }
            }
            itemListBuilder.addItem(rowBuilder.build())
        }

        if (pageOffset + itemsToShow.size < mediaItems.size) {
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("Next Page...")
                    .setOnClickListener {
                        pageOffset += PAGE_LIMIT
                        taskStepCount++
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setHeader(createHeader(File(currentPath).name))
            .setSingleList(itemListBuilder.build())
            .build()
    }
}
