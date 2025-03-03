package com.equalizer.carservice

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Item
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat.getString
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.equalizer.common.MyMediaService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
class MyManager: CarHardwareManager {
    override fun on

}*/

class MainScreen(
    carContext: CarContext,
): Screen(carContext) {

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var controller: MediaController

    enum class Status {
        PLAYING ,
        PAUSED,
        STOPPED
    }

    val viewModelStoreOwner = getViewModelStoreOwner() // Voila!

    private var isPlaying = Status.PAUSED

    private val viewModel: MyViewModel by viewModel<MyViewModel>()
    init {
        val player0 = ExoPlayer
            .Builder(carContext)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player0.setAudioAttributes(audioAttributes, true)
        val mediaItem = MediaItem.Builder()
            .setUri( Uri.parse("/storage/emulated/0/Music/snothvalp.mp3"))
            .build()
        player0.playWhenReady=true
        player0.setMediaItem(mediaItem)
        player0.prepare()
        player0.play()


        val sessionToken = SessionToken(this.carContext, ComponentName(this.carContext, MyMediaService::class.java))


        mediaControllerFuture = MediaController
            .Builder(this.carContext, sessionToken)
            .buildAsync()

        mediaControllerFuture?.apply {
            addListener({
                controller = get()
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isitplaying: Boolean) {
                        log("is it playing = $isitplaying")
                        isPlaying = if (isitplaying) {
                            Status.PLAYING
                        } else {
                            if (controller.playWhenReady) Status.PAUSED
                            else Status.STOPPED
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        log("video size changed: ${videoSize.width}, ${videoSize.height}, ${videoSize.pixelWidthHeightRatio}")

                        volPerFreq[videoSize.width] = videoSize.pixelWidthHeightRatio
                        setVolPerFreqText(videoSize.width)
                        invalidate()
                        super.onVideoSizeChanged(videoSize)
                    }


                    override fun onVolumeChanged(volume: Float) {
                        volPerFreq[currentInterval] = volume
                        setVolPerFreqText(currentInterval)
                        /*val interval=controller.sessionExtras.getInt("Interval")
                        log("interval: $interval")*/
                        invalidate()

//                log((controller as Equalizer).getVolOnFreqs().joinToString(", "))
                        super.onVolumeChanged(volume)
                    }
                    /*
                                override fun onPlaybackStateChanged(playbackState: Int) {

                                    when (playbackState) {
                                        Player.STATE_IDLE -> {
                                            log("Player is idle")
                                        }

                                        Player.STATE_BUFFERING -> {
                                            log("Player is buffering")
                                        }

                                        Player.STATE_ENDED -> {
                                            log("The player is finished")
                                        }

                                        Player.STATE_READY -> {
                                            log("Player is ready")
                                        }
                                    }
                                }*/


                })
            }, MoreExecutors.directExecutor())
        }

        lifecycleScope.launch {
            viewModel.activeInterval.collect {
                invalidate()
            }
        }


    }

    private val frequencyLabels = listOf(
        getString(carContext,R.string.sub_bass_0_125_hz) ,
        getString(carContext,R.string.bass_125_250_hz),
        getString(carContext,R.string.low_250_500_hz),
        getString(carContext,R.string.low_mids_500_hz_1_khz),
        getString(carContext,R.string.high_mids_1_khz_2_khz),
        getString(carContext,R.string.high_2_4_khz),
        getString(carContext,R.string.upper_highs_4_8_khz),
        getString(carContext,R.string.air_8_khz_and_above)
    )
    private var currentInterval=0

    private fun log(msg: String) {
        Log.d("Car Main Screen", msg)
    }

    private fun playMedia(mediaItem: MediaItem) {

        log("playbackState is ${controller.playbackState}, playwhenready=${controller.playWhenReady}")

        when (controller.playbackState) {
            Player.STATE_IDLE -> {
                controller.addMediaItem(mediaItem)
                controller.prepare()
                controller.play()
                log("player is prepared, and playing")
            }


            Player.STATE_BUFFERING -> {
                log("Player is buffering")
            }


            Player.STATE_READY -> {
                controller.play()
                log("player is playing")
            }

            Player.STATE_ENDED -> {
                log("The player is finished")
                controller.addMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }
        }
    }

    private val volPerFreq= MutableList(8) { 1.0f}

    private val intervalItems = MutableList<Item>(size=8) {
        Row.Builder()
            .setTitle(frequencyLabels[it])
            .addText("volume: ${String.format(Locale.GERMAN,"%.1f",volPerFreq[it])}")

            .build()
    }


    private fun setVolPerFreqText(activeRow: Int) {
        intervalItems[activeRow] = Row.Builder()
            .setTitle(frequencyLabels[activeRow])
            .addText("volume: ${String.format(Locale.GERMAN,"%.1f",volPerFreq[activeRow])}")
            .build()

    }

    private val playPause = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(IconCompat.createWithResource(carContext,R.drawable.play_solid)
                .setTint(CarColor.TYPE_RED))
            .build())
        .setOnClickListener {

            log("play clicked")
            log("status is ${Status.PLAYING}")

            if (isPlaying==Status.PLAYING) {
                controller.pause()
                return@setOnClickListener
            }

            val folder = File(Environment.getExternalStorageDirectory(),"/Music")

            val file = folder.listFiles()?.get(0)
            file?.let {
                val myItem = MediaItem
                    .Builder()
                    .setMediaId("media-1")
                    .setUri(Uri.fromFile(file))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setArtist("David Bowie")
                            .setTitle(it.name)
                            .build()
                    ).build()
                playMedia(myItem)
            }
        }
        .setBackgroundColor(CarColor.RED)
        .build()

    private val actionPlus = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(IconCompat
                .createWithResource(carContext,R.mipmap.audio_plus)
                .setTint(CarColor.TYPE_RED))
            .setTint(CarColor.RED)
            .build()
        )

        .setEnabled(volPerFreq[currentInterval]<2.0f)
        .setOnClickListener {
            val volumeInDb = min(2.0f,volPerFreq[currentInterval]+0.1f)

            setVolPerFreqText(currentInterval)

            val extras = Bundle().apply {
                putInt("KEY_INDEX", currentInterval)
                putFloat("KEY_VOLUME", volumeInDb)
            }
            val customCommand = SessionCommand("setVolOnFreq", Bundle())

            controller.sendCustomCommand(customCommand, extras)
        }
        .build()

    private val actionMinus = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(IconCompat.createWithResource(carContext,R.mipmap.audio_minus))
            .setTint(CarColor.RED)
            .build()
        )
        .setOnClickListener {
            val volumeInDb = max(0.0f,volPerFreq[currentInterval]-0.1f)
            setVolPerFreqText(currentInterval)

            val extras = Bundle().apply {
                putInt("KEY_INDEX", currentInterval)
                putFloat("KEY_VOLUME", volumeInDb)
            }

            val customCommand = SessionCommand("setVolOnFreq", Bundle())

            controller.sendCustomCommand(customCommand, extras)

            invalidate()
        }
        .setEnabled(volPerFreq[currentInterval]>0.0f)
        .build()

    private val actionStrip = ActionStrip
        .Builder()
        .addAction(actionMinus)
        .addAction(actionPlus)
        .build()


    override fun onGetTemplate(): Template {

        val singleListBuilder = ItemList.Builder()
            .setSelectedIndex(currentInterval)
            .setOnSelectedListener {
                log("item selected: $it")
                currentInterval=it
            }
        //val x= CarAppApiLevels.getLatest()



        //val plus = CarText.Builder("+").addVariant("plus").build()
        //val plusIcon = CarIcon.Builder(IconCompat())





        intervalItems.forEach { singleListBuilder.addItem(it) }

        //val itemList = ItemList.Builder().addItem(row).addItem(row).addItem(row).build()
        val singleList = singleListBuilder.build()




        return ListTemplate.Builder()
            .setTitle("Equalizer")
            .setActionStrip(actionStrip)
            .setSingleList(singleList)
            /*.addSectionedList(sectionedItemList)*/
            .addAction(playPause)
            .build()

        /*
        return PaneTemplate
            .Builder(
                Pane
                    .Builder()
                    .addRow(row2)
                    .addRow(row2)
                    .addRow(row2)
                    .build())
            .setTitle("Equalizer")
            .setActionStrip(actionStrip)
            .setActionStrip(actionStrip)

            .build()*/
    }
}

