package com.equalizer.carservice

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Environment
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
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import java.io.File


class MediaScreen(
    carContext: CarContext,
    val playControl: PlayControl
): Screen(carContext) {


    var currentDir = ""

    var folder = File(Environment.getExternalStorageDirectory(),"/Music")

    var files =folder.listFiles()?.toList()?:emptyList()

    var currentDepth = 0

    private var fileItems: List<Row> = emptyList()

    var currentSelection = -1

    private fun makeRows() {
        fileItems = files
            .filter {
                it.isDirectory || it.name.endsWith(
                    ".wav",
                    ignoreCase = true
                ) || it.name.endsWith(
                    ".mp3",
                    ignoreCase = true
                )
            }
            .mapIndexed { idx,it->
            val row = Row.Builder()
                .setTitle(it.name)
                .setBrowsable(it.isDirectory)

            if (it.isDirectory) {

                it.listFiles()?.firstNotNullOfOrNull {
                    val mp3Info =  MusicMeta(File(it.absolutePath))
                    mp3Info._imageArray
                }?.let { array ->
                    if (array.isEmpty()) return@let
                    val bitmap = BitmapFactory.decodeByteArray(array, 0, array.size)
                    val musicIcon = CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
                    row.setImage(musicIcon)
                }

                row.setOnClickListener {
                    currentSelection=-1
                    log("dir clicked")
                    if ((currentDepth > 0) && idx == 0) {
                        currentDepth--
                        currentDir =
                            currentDir.split("/").dropLast(1).joinToString(separator = "/")
                        Log.d("file selection", "parent clicked")
                    } else {
                        currentDepth++
                        Log.d("file selection", "folder clicked")
                        currentDir = currentDir + "/" + it.name
                    }
                    folder = File(Environment.getExternalStorageDirectory()
                        .absolutePath + "/Music$currentDir"
                    )

                    files = folder.listFiles()?.toList() ?: emptyList()
                    if (currentDepth > 0) {
                        val parent =
                            currentDir.split("/").dropLast(1).joinToString(separator = "/")
                        val parentFile =
                            File(Environment.getExternalStorageDirectory().absolutePath + "/Music" + parent)
                        files = listOf(parentFile) + files
                    }
                        //log(files.joinToString(separator = ", ") { it.name })

                    makeRows()
                    invalidate()
                }
                /* row.set {
                log("dir clicked")
            }*/
            } else {
                val mp3Info =  MusicMeta(File(it.absolutePath))


                val string = SpannableString("artist: ${mp3Info._artist}")

                if (idx == currentSelection) {
                    string.setSpan(
                        ForegroundCarColorSpan.create(CarColor.GREEN),
                        0,
                        string.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                val title = SpannableString(mp3Info._name)

                row
                    .setTitle(title)
                    .addText(string)
                    .setOnClickListener {
                        currentSelection = idx
                        makeRows()
                        invalidate()
                    }

            }

            row.build()
        }
    }

    init {
        makeRows()
    }

    private fun log(msg: String) {
        Log.d("Car Media Screen", msg)
    }

    private val equalizerAction = Action.Builder()
        .setIcon(CarIcon
            .Builder(
                IconCompat
                    .createWithResource(
                        carContext,
                        R.drawable.lever_vert
                    )
                ).setTint(CarColor.createCustom(
                Color.LTGRAY,
                Color.DKGRAY
            ))
            .build())
        .setOnClickListener {
            this.screenManager.push(
                EqualizerScreen(
                    carContext = carContext,
                    playControl
                )
            ) }
        .setBackgroundColor(CarColor.RED)
        .build()


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
            log("status is ${playControl.isPlaying}")

            playControl.controller.pause()
        }
        .setBackgroundColor(CarColor.BLUE)
        .build()


    val playPauseBuilder = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(
                IconCompat.createWithResource(carContext,
                    R.drawable.play_solid)
            )
            .build())

    override fun onGetTemplate(): Template {
        playControl.setInvalidate0 {
            invalidate()
        }

        val playPause = playPauseBuilder

            .setBackgroundColor(if (currentSelection>=0) CarColor.RED else CarColor.SECONDARY)

            .setOnClickListener {
                log("play clicked")

                if (currentSelection<0) {
                    log("returned: currentselection = $currentSelection")
                    return@setOnClickListener
                }
                val file = File(files[currentSelection].absolutePath)
                if (file.isDirectory) {
                    log("returned: is directory")
                    return@setOnClickListener
                }
                log("file: $file")
                file.let {
                    val myItem = MediaItem
                        .Builder()
                        .setMediaId("media-1")
                        .setUri(Uri.fromFile(file))
                        /*.setMediaMetadata(
                            MediaMetadata.Builder()
                                .build()
                        )*/.build()
                    playControl.playMedia(myItem)
                }
            }
            .build()




        val singleList = ItemList.Builder().apply {
            fileItems.forEach { this.addItem(it) }
        }.build()

        // 1. Create a Header object for your Title and Actions
        val header = Header.Builder()
            .setTitle("Media")
            .addEndHeaderAction(if (playControl.isPlaying != PlayControl.Status.PLAYING) playPause else stopAction)
            .addEndHeaderAction(equalizerAction)
            .build()

        // 2. Pass the header to the ListTemplate
        return ListTemplate.Builder()
            .setSingleList(singleList)
            .setHeader(header) // Use setHeader instead of setTitle/addAction
            .build()
        /*
        return ListTemplate.Builder()
            .setTitle("Media")
            .setSingleList(singleList)
            .addAction(if (playControl.isPlaying != PlayControl.Status.PLAYING) playPause else stopAction)
            .addAction(equalizerAction)
            .build()*/
    }
}