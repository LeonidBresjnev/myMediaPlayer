package com.equalizer.carservice

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.media3.common.util.UnstableApi
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