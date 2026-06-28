package com.equalizer.carservice

import android.content.Intent
import androidx.annotation.OptIn
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.media3.common.util.UnstableApi

class ModernCarService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return object : Session() {
            @OptIn(UnstableApi::class)
            override fun onCreateScreen(intent: Intent): Screen {
                // Ensure we ALWAYS start at the MainTabScreen (Browser)
                // even if the system provides an intent hint to jump elsewhere
                val playControl = PlayControl(carContext)
                return MainTabScreen(carContext, playControl)
            }
        }
    }
}
