package com.equalizer.carservice

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
class CarService : CarAppService() {

    private fun log(message: String) {
        Log.i("CarService", message)
    }




    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        //getCarAppApiLevels


        return object : Session() {
            /*
            private lateinit var mediaSession: MediaSessionCompat
            init {
                log("init")
                val observer = LifecycleEventObserver { _, event ->
                    mediaSession = MediaSessionCompat(
                        carContext,
                        "MyCarAppMediaSession" // A unique tag for debugging
                    )
                    mediaSession.isActive = true // Make the session active

                    val token = mediaSession.sessionToken
                    (carContext.getCarService(CarContext.MEDIA_PLAYBACK_SERVICE) as MediaPlaybackManager)
                        .registerMediaPlaybackToken(token)
                }
                lifecycle.addObserver(observer

                )
            }*/



            override fun onCreateScreen(intent: Intent): Screen {
/*
                val session = MediaSessionCompat(this, "session tag").apply {
                    // Set a callback object that implements MediaSession.Callback
                    // to handle play control requests.
                    setCallback(MyMediaSessionCallback())
                }*/


                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()

                val player = ExoPlayer.Builder(carContext)
                    .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri("/storage/emulated/0/Music/snothvalp.mp3".toUri())
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()


                /*   val audioManager: AudioManager =  carContext.getSystemService(AUDIO_SERVICE) as (AudioManager)
                   val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                   Log.d("audio device","antal device: ${devices.size}")
                   for (device in devices) {
                       // Set the audio output to the car's audio system

                       Log.d(
                           "audio device",
                           " ${device.id}, ${device.type}, ${device.productName}, ${
                               device.sampleRates.joinToString(";")
                           }"
                       )

                   }
                   val androidAutoDevice: AudioDeviceInfo? = devices.find {
                       it.type == AudioDeviceInfo.TYPE_TELEPHONY
                   }
                   // Set the communication device
                   androidAutoDevice?.let { it->
                       val success = audioManager.setCommunicationDevice(it)
                       if (success) {
                           log("Audio routed to Android Auto")
                       } else {
                           log("Failed to route audio")
                       }
                   }?: log("No Android Auto device found")
   */

                    val playControl = PlayControl(carContext)
         /*       log( "onCreateSession1: ")
                val player0 = ExoPlayer
                    .Builder(carContext)
                    .setName("ExoPlayer")
                    .build()
                val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
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
                player0.stop()

                player0.release()*/

                /*
                val callback = object: MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        log("onPlay")
                        super.onPlay()
                    }

                    override fun onPause() {
                        log("onPause")
                        super.onPause()
                    }
                }*/


                return MediaScreen(carContext = this.carContext, playControl = playControl )

            }


        }

    }
}