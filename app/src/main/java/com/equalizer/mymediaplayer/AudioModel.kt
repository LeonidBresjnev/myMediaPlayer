package com.equalizer.mymediaplayer

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.equalizer.common.Equalizer
import com.equalizer.common.MyMediaService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class AudioModel: ViewModel() {
    private val _volumenLow = MutableLiveData(List(8){1f})

    val volumenLow: LiveData<List<Float>>
        get() {
            return _volumenLow
        }

    val volumeRange = 0f..2f

    private var currentSlider=-1

    fun setVolumen(volumeInDb: Float, index: Int) {
        currentSlider=index
        val extras = Bundle().apply {
            putInt("KEY_INDEX", index)
            putFloat("KEY_VOLUME", volumeInDb)
        }
        val customCommand = SessionCommand("setVolOnFreq", Bundle())

        controller.sendCustomCommand(customCommand, extras)
        /*_volumenLow.value = _volumenLow.value!!.mapIndexed { i, v -> if (i==index) volumeInDb else v }
        viewModelScope.launch {
            equalizer?.setVolumenLow(volumeInDb, index)
        }*/
    }

    private val _playButtonLabel = MutableLiveData(R.string.play)

    val playButtonLabel: LiveData<Int>
        get() {
            return _playButtonLabel
        }

    private val _isPlaying = MutableLiveData(Status.STOPPED)
    val isPlaying: LiveData<Status>
        get() {
            return _isPlaying
        }

    enum class Status {
        PLAYING ,
        PAUSED,
        STOPPED
    }

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    private lateinit var controller: MediaController



    private fun log(message: String) {
        Log.i("AudioModel", message)

    }

    private fun handlePlaybackBasedOnState() {
        /*if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
            playMedia()
        } else */

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isitplaying: Boolean) {
                log("is it playing = $isitplaying")
                _isPlaying.value = if (isitplaying) {
                     Status.PLAYING
                } else {
                    if (controller.playWhenReady) Status.PAUSED
                    else Status.STOPPED
                }
            }

            @OptIn(UnstableApi::class)
            override fun onVolumeChanged(volume: Float) {
                _volumenLow.value = _volumenLow.value!!.mapIndexed { i, v -> if (i==currentSlider) volume else v }
//                log((controller as Equalizer).getVolOnFreqs().joinToString(", "))
                super.onVolumeChanged(volume)
            }



/*
            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
            }*/


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
            }


        }
        )

    }

    internal fun initializeMediaController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MyMediaService::class.java))

        mediaControllerFuture = MediaController
            .Builder(context, sessionToken)
            .buildAsync()
        mediaControllerFuture?.apply {
            addListener({
                controller = get()

                //updateUIWithMediaController(controller)

                // Ensure media is played appropriately based on state
                log("INITIAL STATE = ${controller.playbackState}")
                handlePlaybackBasedOnState()

            }, MoreExecutors.directExecutor()

            )
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




    internal fun stopMedia() {
        controller.pause()
       // controller.stop()

    }


    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controller.release()
    }
}