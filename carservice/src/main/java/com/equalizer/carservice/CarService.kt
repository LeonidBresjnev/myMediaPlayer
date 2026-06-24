package com.equalizer.carservice

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class CarService : CarAppService() {

    private fun log(message: String) {
        Log.i("CarService", message)
    }




    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        log("onCreateSession: ")
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

                val playControl = PlayControl(carContext)

                return MediaScreen(carContext = this.carContext, playControl = playControl )

            }


        }

    }
}