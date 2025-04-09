package com.equalizer.carservice

import android.content.ComponentName
import android.util.Log
import androidx.car.app.CarContext
import androidx.core.content.ContextCompat.getString
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.equalizer.common.MyMediaService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors


class PlayControl( carContext: CarContext) {

    private fun log(msg: String="") {
        Log.d("Car PlayControl", msg)
    }

    enum class Status {
        PLAYING ,
        PAUSED,
        STOPPED
    }

    val sessionToken = SessionToken(carContext, ComponentName(carContext, MyMediaService::class.java))

    private var mediaControllerFuture: ListenableFuture<MediaController> = MediaController
        .Builder(carContext, sessionToken)
        .buildAsync()


    lateinit var controller: MediaController

    val frequencyLabels = listOf(
        getString(carContext,R.string.sub_bass_0_125_hz) ,
        getString(carContext,R.string.bass_125_250_hz),
        getString(carContext,R.string.low_250_500_hz),
        getString(carContext,R.string.low_mids_500_hz_1_khz),
        getString(carContext,R.string.high_mids_1_khz_2_khz),
        getString(carContext,R.string.high_2_4_khz),
        getString(carContext,R.string.upper_highs_4_8_khz),
        getString(carContext,R.string.air_8_khz_and_above)
    )

    val volPerFreq= MutableList(8) { 1.0f}

    internal var isPlaying = Status.PAUSED

    var invalidate = { }

    fun setInvalidate0 (func: () -> Unit) {
        invalidate = func
    }

    var volPerFreqSetter:  (x:Int) -> Unit  = { x ->
log("mystik")
    }


    fun setVolPerFreqSetter0(func: (Int) -> Unit) {
        log("setVolPerFreqSetter0")
        volPerFreqSetter = func

    }

    init {
        log("init")
        /*     val player0 = ExoPlayer
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
             player0.play()*/


        mediaControllerFuture.apply {
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
                        invalidate()
                        super.onIsPlayingChanged(isitplaying)
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        log("video size changed: ${videoSize.width}, ${videoSize.height}, ${videoSize.pixelWidthHeightRatio}")

                        volPerFreq[videoSize.width] = videoSize.pixelWidthHeightRatio
                        volPerFreqSetter(videoSize.width)
                        //invalidate()
                        super.onVideoSizeChanged(videoSize)
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
    }


    internal fun playMedia(mediaItem: MediaItem) {

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
}
