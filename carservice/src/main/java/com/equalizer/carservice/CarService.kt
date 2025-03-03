package com.equalizer.carservice

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    viewModel { MyViewModel() }
}
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




            override fun onCreateScreen(intent: Intent): Screen {
                log( "onCreateSession1: ")
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
                startKoin {
                    androidContext(carContext)
                    androidLogger()
                    modules(appModule)
                }
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



                return MainScreen(carContext = this.carContext )

            }


        }

    }
}