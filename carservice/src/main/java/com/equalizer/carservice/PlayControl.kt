package com.equalizer.carservice

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.media.MediaPlaybackManager
import androidx.core.content.ContextCompat.getString
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.equalizer.common.MyMediaService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors


@UnstableApi
class PlayControl(private val carContext: CarContext) {

    init {
        com.equalizer.common.MediaThumbnailProvider.init(carContext)
    }

    private fun log(msg: String="") {
        Log.d("Car PlayControl", msg)
    }

    enum class Status {
        PLAYING ,
        PAUSED,
        STOPPED
    }

    val sessionToken = SessionToken(
        carContext,
        ComponentName(carContext, MyMediaService::class.java)
    )

    // MediaController/Browser Listener for session-specific events like extras
    private val browserListener = object : MediaBrowser.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            val eqState = extras.getFloatArray("EQ_STATE")
            if (eqState != null && (eqState.size == 8 || eqState.size == 16)) {
                log("Updating car UI from session extras")
                for (i in eqState.indices) {
                    volPerFreq[i] = eqState[i]
                }
                isAdvancedMode = extras.getBoolean("IS_ADVANCED", false)
                val favs = extras.getStringArray("FAVOURITES")
                if (favs != null) {
                    log("Received ${favs.size} favourites from session")
                    favourites.clear()
                    favourites.addAll(favs)
                }
                notifyInvalidate()
                volPerFreqSetter(-1) // Signal a full refresh to components
            }
        }
    }

    val mediaControllerFuture: ListenableFuture<MediaBrowser> = MediaBrowser
        .Builder(carContext, sessionToken)
        .setListener(browserListener)
        .buildAsync()


    lateinit var controller: MediaBrowser

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

    val volPerFreq= MutableList(16) { 1.0f}
    var isAdvancedMode = false
    val favourites = mutableSetOf<String>()

    internal var isPlaying = Status.PAUSED
    internal var currentMediaItem: MediaItem? = null

    private val invalidateListeners = mutableSetOf<() -> Unit>()

    fun addInvalidateListener(listener: () -> Unit) {
        invalidateListeners.add(listener)
    }

    fun removeInvalidateListener(listener: () -> Unit) {
        invalidateListeners.remove(listener)
    }

    private fun notifyInvalidate() {
        carContext.mainExecutor.execute {
            log("Notifying ${invalidateListeners.size} invalidation listeners")
            invalidateListeners.forEach { it() }
        }
    }

    var volPerFreqSetter:  (x:Int) -> Unit  = { x ->
        log("volPerFreq not set yet, value= $x")
    }

    fun setVolPerFreqSetter0(func: (Int) -> Unit) {
        log("setVolPerFreqSetter0")
        volPerFreqSetter = func
    }

    init {
        log("init")
        mediaControllerFuture.apply {
            addListener({
                controller = get()
                
                // Sync initial state if available immediately
                isPlaying = if (controller.isPlaying) Status.PLAYING else {
                    if (controller.playWhenReady) Status.PAUSED else Status.STOPPED
                }
                currentMediaItem = controller.currentMediaItem
                notifyInvalidate()

                // Add Player.Listener for standard events
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isitplaying: Boolean) {
                        log("is it playing = $isitplaying")
                        isPlaying = if (isitplaying) {
                            Status.PLAYING
                        } else {
                            if (controller.playWhenReady) Status.PAUSED
                            else Status.STOPPED
                        }
                        notifyInvalidate()
                        super.onIsPlayingChanged(isitplaying)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        log("Media item transition: ${mediaItem?.mediaMetadata?.title}")
                        currentMediaItem = mediaItem
                        notifyInvalidate()
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        log("video size changed: ${videoSize.width}, ${videoSize.height}, ${videoSize.pixelWidthHeightRatio}")

                        volPerFreq[videoSize.width] = videoSize.pixelWidthHeightRatio
                        volPerFreqSetter(videoSize.width)
                        super.onVideoSizeChanged(videoSize)
                    }
                })

                // Sync initial state if available
                val initialExtras = controller.sessionExtras
                val eqState = initialExtras.getFloatArray("EQ_STATE")
                if (eqState != null && (eqState.size == 8 || eqState.size == 16)) {
                    for (i in eqState.indices) volPerFreq[i] = eqState[i]
                    isAdvancedMode = initialExtras.getBoolean("IS_ADVANCED", false)
                    val favs = initialExtras.getStringArray("FAVOURITES")
                    if (favs != null) {
                        favourites.clear()
                        favourites.addAll(favs)
                    }
                    notifyInvalidate()
                    volPerFreqSetter(-1)
                }

            }, MoreExecutors.directExecutor())
        }
    }


    internal fun playMedia(mediaItem: MediaItem) {
        log("playMedia: ${mediaItem.mediaMetadata.title}")

        if (::controller.isInitialized) {
            // Force replace and play to ensure the car selection is respected
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            log("Playback started for ${mediaItem.mediaMetadata.title}")
        } else {
            log("Controller not ready for playback")
            mediaControllerFuture.addListener({
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }, MoreExecutors.directExecutor())
        }
    }

    @androidx.car.app.annotations.ExperimentalCarApi
    //@OptIn(UnstableApi::class)
    fun registerToken() {
        MyMediaService.getSession()?.let { session ->
            try {
                val playbackManager = carContext.getCarService(CarContext.MEDIA_PLAYBACK_SERVICE) as MediaPlaybackManager
                val getSessionCompatToken = session.javaClass.methods.find { it.name == "getSessionCompatToken" }
                val token = getSessionCompatToken?.invoke(session) as? MediaSessionCompat.Token
                //val token = session.token  /* session.getS .token  .getSessionCompatToken()*/

                token?.apply {
                    playbackManager.registerMediaPlaybackToken(this)
                }?:log("Could not retrieve MediaSessionCompat.Token from session")

                /*if (token != null) {
                    playbackManager.registerMediaPlaybackToken(token)
                    log("MediaPlaybackToken registered successfully")
                } else {
                    log("Could not retrieve MediaSessionCompat.Token from session")
                }*/
            } catch (e: Exception) {
                log("Failed to register MediaPlaybackToken: ${e.message}")
            }
        } ?: log("MyMediaService session not available for registration yet")
    }
}
