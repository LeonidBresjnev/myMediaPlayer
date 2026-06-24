package com.equalizer.carservice

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import com.google.common.util.concurrent.MoreExecutors
import java.io.File

class MediaScreen(
    carContext: CarContext,
    val playControl: PlayControl
): Screen(carContext) {

    private var currentPath = "root"
    private var mediaItems: List<MediaItem> = emptyList()
    private val navStack = mutableListOf<String>()
    private var currentSelection = -1

    init {
        browse(currentPath)
    }

    private fun browse(parentId: String, addToStack: Boolean = true) {
        if (playControl.mediaControllerFuture.isDone) {
            val controller = playControl.controller
            log("Browsing: $parentId")
            val childrenFuture = controller.getChildren(parentId, 0, Int.MAX_VALUE, null)
            childrenFuture.addListener({
                try {
                    val result = childrenFuture.get()
                    if (result.value != null) {
                        if (addToStack && parentId != currentPath) {
                            navStack.add(currentPath)
                        }
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
                browse(parentId, addToStack)
            }, MoreExecutors.directExecutor())
        }
    }

    private fun navigateBack() {
        if (navStack.isNotEmpty()) {
            val lastPath = navStack.removeAt(navStack.size - 1)
            browse(lastPath, addToStack = false)
        }
    }

    private fun log(msg: String) {
        Log.d("Car Media Screen", msg)
    }

    private val stopAction = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(IconCompat.createWithResource(carContext, R.drawable.stopplay )
                ).setTint(CarColor.createCustom(
                Color.LTGRAY,
                Color.DKGRAY
            ))
            .build())
        .setOnClickListener {
            log("stop clicked")
            playControl.controller.pause()
        }
        .setBackgroundColor(CarColor.BLUE)
        .build()

    private fun showInfo(item: MediaItem) {
        log("show info")
        val metadata = item.mediaMetadata
        val message = StringBuilder()
        message.append("Title: ${metadata.title ?: "Unknown"}\n")
        message.append("Artist: ${metadata.artist ?: "Unknown"}\n")
        metadata.totalDiscCount?.let { message.append("Sample Rate: $it\n") }
        metadata.releaseMonth?.let { message.append("Channels: $it\n") }
        
        val template = MessageTemplate.Builder(message.toString())
            .setTitle("Media Information")
            .setHeaderAction(Action.BACK)
            .build()
        
        this.screenManager.push(object : Screen(carContext) {
            override fun onGetTemplate(): Template = template
        })
    }

    override fun onGetTemplate(): Template {
        log("hello from auto")
        playControl.setInvalidate0 {
            invalidate()
        }

        val playPause = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.play_solid)).build())
            .setBackgroundColor(if (currentSelection >= 0) CarColor.RED else CarColor.SECONDARY)
            .setOnClickListener {
                if (currentSelection >= 0) {
                    val item = mediaItems[currentSelection]
                    if (item.mediaMetadata.isBrowsable != true) {
                        playControl.playMedia(item)
                    }
                }
            }
            .build()

        val itemListBuilder = ItemList.Builder()
        
        // Root menu link always at top

        log(currentPath)
        if (currentPath == "root") {
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("Equalizer Settings")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.lever_vert)).build())
                    .setOnClickListener {
                        this.screenManager.push(EqualizerScreen(carContext, playControl))
                    }
                    .build()
            )
        } else if (navStack.isNotEmpty()) {
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(".. (Back to Albums)")
                    .setOnClickListener { navigateBack() }
                    .build()
            )
        }

        mediaItems.forEachIndexed { idx, item ->
            val metadata = item.mediaMetadata
            
            // At root, we only show albums/folders
            if (currentPath == "root" && metadata.isBrowsable != true) {
                return@forEachIndexed
            }

            val rowBuilder = Row.Builder()
                .setTitle(metadata.title ?: "Unknown")
                .setBrowsable(metadata.isBrowsable ?: false)

            metadata.artist?.let {
                val artistText = SpannableString("artist: $it")
                if (idx == currentSelection) {
                    artistText.setSpan(
                        ForegroundCarColorSpan.create(CarColor.GREEN),
                        0,
                        artistText.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                rowBuilder.addText(artistText)
            }

            if (metadata.isBrowsable == true) {
                metadata.artworkUri?.let { uri ->
                    rowBuilder.setImage(CarIcon.Builder(IconCompat.createWithContentUri(uri)).build())
                } ?: run {
                    rowBuilder.setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.lever_vert)).build())
                }
            }

            if (metadata.isBrowsable != true) {
                rowBuilder.addAction(
                    Action.Builder()
                        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, androidx.media3.session.R.drawable.media3_icon_artist)).build())
                        .setOnClickListener { showInfo(item) }
                        .build()
                )
            }

            rowBuilder.setOnClickListener {
                if (metadata.isBrowsable == true) {
                    browse(item.mediaId)
                } else {
                    currentSelection = idx
                    invalidate()
                }
            }
            itemListBuilder.addItem(rowBuilder.build())
        }

        val headerBuilder = Header.Builder()
            .setTitle(if (currentPath == "root") "Music Library" else File(currentPath).name)
            .addEndHeaderAction(if (playControl.isPlaying != PlayControl.Status.PLAYING) playPause else stopAction)
        
        if (currentPath != "root") {
            headerBuilder.setStartHeaderAction(Action.BACK)
        } else {
            // Ensure APP_ICON is visible as the header action on root if needed
            headerBuilder.setStartHeaderAction(Action.APP_ICON)
        }

        return ListTemplate.Builder()
            .setSingleList(itemListBuilder.build())
            .setHeader(headerBuilder.build())
            .build()
    }
}
