package com.equalizer.carservice

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import java.util.Locale

@OptIn(UnstableApi::class)
class ModernCarService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return object : Session() {
            private lateinit var playControl: PlayControl

            init {
                lifecycle.addObserver(LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_CREATE) {
                        doTokenRegistration()
                    }
                })
            }

            @OptIn(ExperimentalCarApi::class, UnstableApi::class)
            private fun doTokenRegistration() {
                if (::playControl.isInitialized) {
                    playControl.registerToken()
                }
            }

            @OptIn(UnstableApi::class, ExperimentalCarApi::class)
            override fun onCreateScreen(intent: Intent): Screen {
                return try {
                    val apiLevel = carContext.carAppApiLevel
                    Log.d("ModernCarService", "Car App API Level: $apiLevel")
                    
                    playControl = PlayControl(carContext)
                    try {
                        playControl.registerToken()
                    } catch (e: Exception) {
                        Log.e("ModernCarService", "Token registration failed: ${e.message}")
                    }
                    
                    if (apiLevel <= 5) {
                        Log.d("ModernCarService", "Starting SimpleMainScreen (Legacy Mode)")
                        SimpleMainScreen(carContext, playControl)
                    } else {
                        Log.d("ModernCarService", "Starting MainTabScreen (Modern Mode)")
                        MainTabScreen(carContext, playControl)
                    }
                } catch (e: Exception) {
                    Log.e("ModernCarService", "Error creating screen: ${e.message}", e)
                    // Emergency fallback: return a minimal screen
                    object : Screen(carContext) {
                        override fun onGetTemplate(): Template {
                            return ListTemplate.Builder()
                                .setTitle("Error Starting App")
                                .setSingleList(ItemList.Builder().addItem(Row.Builder().setTitle("Please restart").build()).build())
                                .build()
                        }
                    }
                }
            }

            override fun onNewIntent(intent: Intent) {
                Log.d("ModernCarService", "onNewIntent: $intent")
                val topScreen = carContext.getCarService(androidx.car.app.ScreenManager::class.java).top
                if (topScreen is MainTabScreen) {
                    topScreen.handleIntent(intent)
                }
            }
        }
    }
}

/**
 * A simplified version of the main screen for older head units (API <= 5).
 * Uses ListTemplate which is compatible with API 1.
 */
@OptIn(UnstableApi::class)
class SimpleMainScreen(
    carContext: CarContext,
    private val playControl: PlayControl
) : Screen(carContext) {

    init {
        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions =
            listOf(android.Manifest.permission.READ_MEDIA_AUDIO)
        carContext.requestPermissions(permissions) { _, _ ->
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Playlists")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_agenda)).build())
                .setOnClickListener {
                    screenManager.push(SongListScreen(carContext, playControl, "playlists_root", "Playlists"))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Music & Radio Library")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_gallery)).build())
                .setOnClickListener {
                    screenManager.push(SongListScreen(carContext, playControl, "library_combined", "Music & Radio"))
                }
                .build()
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Equalizer Settings")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_preferences)).build())
                .setOnClickListener {
                    // Navigate to a simplified list of bands
                    screenManager.push(SimpleEqualizerScreen(carContext, playControl))
                }
                .build()
        )

        val builder = ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeaderAction(Action.APP_ICON)
        
        // setTitle is deprecated in Builder but setHeader is the modern way if available.
        // For max compatibility with API 1-5, we'll try to use setHeader if possible, 
        // or just accept the deprecation for now as it's the most stable way for legacy.
        try {
            builder.setTitle("My Media Player")
        } catch (e: Exception) {
            e.message?.let { Log.d("Modern Car Service", it) }
        }

        return builder.build()
    }
}

/**
 * Simplified Equalizer screen for older systems.
 */
@OptIn(UnstableApi::class)
class SimpleEqualizerScreen(
    carContext: CarContext,
    private val playControl: PlayControl
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        for (i in 0 until 8) {
            val vol = playControl.volPerFreq[i]
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(playControl.frequencyLabels[i])
                    .addText("Volume: ${String.format(Locale.GERMAN, "%.1f", vol)}")
                    .setOnClickListener {
                        screenManager.push(BandDetailScreen(carContext, playControl, i))
                    }
                    .build()
            )
        }

        val builder = ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeaderAction(Action.BACK)
            
        try {
            builder.setTitle("Equalizer")
        } catch (_: Exception) {}

        return builder.build()
    }
}
