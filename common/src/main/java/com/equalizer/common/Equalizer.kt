package com.equalizer.common

import android.content.Context
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.BasePlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.Log
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util.getCurrentOrMainLooper
import com.google.common.base.Preconditions
import java.io.File

@UnstableApi
class Equalizer(
    private var equalizerHandle: Long=0L,
    private val context: Context
):
    BasePlayer(), Player, DefaultLifecycleObserver {

    private val equalizerMutex = Object()

    private fun createNativeHandleIfNotExists() {
        if (equalizerHandle != 0L) {
            return
        }
        equalizerHandle = nativeCreate()
    }

    private external fun nativeCreate(): Long
    private external fun nativeDelete(synthesizerHandle: Long)
    private external fun nativeStop(synthesizerHandle: Long)
    private external fun nativePlay(synthesizerHandle: Long, name: String, deviceId: Int)
    private external fun nativePlayWithVol(synthesizerHandle: Long,
                                           name: String,
                                           vol: FloatArray,
                                           deviceId: Int)
    private external fun nativeIsPlaying(synthesizerHandle: Long): Boolean
    private external fun nativeSetVolumenLow(synthesizerHandle: Long,volumeInDb: Float, freqInterval: Int)

    private val volPerFreq = MutableList(8) { 1f }

    fun setVolOnFreq(volumeInDb: Float, freqInterval: Int) {
        synchronized(equalizerMutex){
            createNativeHandleIfNotExists()
            nativeSetVolumenLow(equalizerHandle, volumeInDb, freqInterval)
        }
        volPerFreq[freqInterval] = volumeInDb
        listeners.sendEvent(1) { listener: Player.Listener ->
            listener.onVideoSizeChanged(VideoSize(freqInterval, 0, volumeInDb))
        }
    }

    fun setAllVolOnFreq(volumes: FloatArray) {
        synchronized(equalizerMutex) {
            createNativeHandleIfNotExists()
            for (i in 0 until volumes.size.coerceAtMost(8)) {
                volPerFreq[i] = volumes[i]
                nativeSetVolumenLow(equalizerHandle, volumes[i], i)
            }
        }
        // Notify listener for each band to ensure UI updates
        for (i in 0 until volumes.size.coerceAtMost(8)) {
            listeners.sendEvent(1) { listener: Player.Listener ->
                listener.onVideoSizeChanged(VideoSize(i, 0, volumes[i]))
            }
        }
    }

    fun getVolOnFreqs(): List<Float> = volPerFreq

    private var applicationLooper: Looper= getCurrentOrMainLooper()
    private val clock = Clock.DEFAULT

    private var listeners: ListenerSet<Player.Listener> =
        ListenerSet(applicationLooper, clock) { listener: Player.Listener, flags: FlagSet ->
            listener.onEvents(this, Player.Events(flags) )
        }

    private val commands = Player.Commands.Builder()
        .add(COMMAND_PREPARE)
        .add(COMMAND_PLAY_PAUSE)
        .add(COMMAND_STOP)
        .add(COMMAND_SET_MEDIA_ITEM)
        .add(COMMAND_CHANGE_MEDIA_ITEMS)
        .add(COMMAND_GET_VOLUME)
        .add(COMMAND_GET_TIMELINE)
        .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
        .add(COMMAND_GET_METADATA)
        .build()

    private var playBackParameters = PlaybackParameters.DEFAULT
    private val seekBackIncrementMs: Long = C.DEFAULT_SEEK_BACK_INCREMENT_MS
    private val seekForwardIncrementMs: Long = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS

    companion object {
        init {
            System.loadLibrary("myMediaPlayer")
        }
    }

    private fun log(message: String) {
        Log.d("Equalizer", message)
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    private fun requestFocus(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val playbackAttributes = AndroidAudioAttributes.Builder()
            .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
            .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val focusRequestBuilder = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        setPlayWhenReady(false)
                    }
                }
            }
        
        audioFocusRequest = focusRequestBuilder.build()
        val res = audioManager.requestAudioFocus(audioFocusRequest!!)
        return res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun getBestDeviceId(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val carDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BUS }

        return if (carDevice != null) {
            log("Car device detected: ${carDevice.productName}, using id=${carDevice.id}")
            carDevice.id
        } else {
            log("No car device detected, using default (0)")
            0
        }
    }

    init {
        log("inited")
        listeners.add(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                log("onIsPlayingChanged: $isPlaying")
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    if (!requestFocus()) {
                        log("Audio focus denied")
                        setPlayWhenReady(false)
                        return
                    }

                    val mediaItem = mediaItems.getOrNull(0)
                    val path = mediaItem?.localConfiguration?.uri?.path ?: mediaItem?.mediaId
                    log("Attempting to play path: $path")

                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) {
                            synchronized(equalizerMutex) {
                                createNativeHandleIfNotExists()
                                val deviceId = getBestDeviceId()
                                log("nativePlayWithVol: ${file.absolutePath}, deviceId: $deviceId")
                                nativePlayWithVol(equalizerHandle,
                                    file.absolutePath,
                                    volPerFreq.toFloatArray(),
                                    deviceId)
                            }
                        } else {
                            log("File does not exist: $path")
                        }
                    } else {
                        log("No valid path found in MediaItem")
                    }
                } else {
                    synchronized(equalizerMutex){
                        if (equalizerHandle != 0L) {
                            nativeStop(equalizerHandle)
                        }
                    }
                    abandonFocus()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                log("playbackState: $playbackState")
                super.onPlaybackStateChanged(playbackState)
            }

            override fun onPlayerError(error: PlaybackException) {
                log("player error: ${error.message}")
                super.onPlayerError(error)
            }
        })
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        synchronized(equalizerMutex) {
            createNativeHandleIfNotExists()
        }
    }

    val mediaItems: MutableList<MediaItem> = mutableListOf()
    private val deviceInfo: DeviceInfo= DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).build()
    private var playWhenReady=false

    override fun getApplicationLooper(): Looper = this.applicationLooper
    override fun addListener(listener: Player.Listener) = listeners.add(listener)
    override fun removeListener(listener: Player.Listener) = listeners.remove(Preconditions.checkNotNull(listener))

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        log("setMediaItems1: size=${mediaItems.size}")
        this.mediaItems.clear()
        this.mediaItems.addAll(mediaItems)
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }
        listeners.sendEvent(EVENT_MEDIA_METADATA_CHANGED) { it.onMediaMetadataChanged(mediaMetadata) }
        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { it.onPlaybackStateChanged(playbackState) }
    }

    override fun setMediaItems(_mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        log("setMediaItems2: size=${_mediaItems.size}")
        this.mediaItems.clear()
        this.mediaItems.addAll(_mediaItems)
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }
        listeners.sendEvent(EVENT_MEDIA_METADATA_CHANGED) { it.onMediaMetadataChanged(mediaMetadata) }
        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { it.onPlaybackStateChanged(playbackState) }
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        this.mediaItems.addAll(index, mediaItems)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        val mediaItem = mediaItems.removeAt(fromIndex)
        mediaItems.add(newIndex, mediaItem)
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
        mediaItems.forEachIndexed { idx, it -> this.mediaItems[idx + fromIndex] = it }
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        repeat(toIndex - fromIndex) { mediaItems.removeAt(fromIndex) }
    }

    override fun getAvailableCommands(): Player.Commands = this.commands
    override fun prepare() { log("prepare()") }
    override fun getPlaybackState(): Int = if (mediaItems.isNotEmpty()) STATE_READY else STATE_IDLE
    override fun getPlaybackSuppressionReason(): Int = PLAYBACK_SUPPRESSION_REASON_NONE
    override fun getPlayerError(): PlaybackException? = null

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        log("setPlayWhenReady: $playWhenReady")
        this.playWhenReady=playWhenReady
        listeners.sendEvent(EVENT_PLAY_WHEN_READY_CHANGED) { it.onPlayWhenReadyChanged(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        listeners.sendEvent(EVENT_IS_PLAYING_CHANGED) { it.onIsPlayingChanged(playWhenReady) }
    }

    override fun getPlayWhenReady(): Boolean = playWhenReady
    override fun setRepeatMode(repeatMode: Int) {}
    override fun getRepeatMode(): Int = REPEAT_MODE_OFF
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}
    override fun getShuffleModeEnabled(): Boolean = false
    override fun isLoading(): Boolean = false
    override fun getSeekBackIncrement(): Long = seekBackIncrementMs
    override fun getSeekForwardIncrement(): Long = seekForwardIncrementMs
    override fun getMaxSeekToPreviousPosition(): Long = C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) { this.playBackParameters = playbackParameters }
    override fun getPlaybackParameters(): PlaybackParameters = this.playBackParameters
    override fun stop() { setPlayWhenReady(false) }

    override fun release() {
        synchronized(equalizerMutex) {
            if (equalizerHandle != 0L) {
                nativeDelete(equalizerHandle)
                equalizerHandle = 0L
            }
        }
        listeners.release()
    }

    override fun getCurrentTracks(): Tracks = Tracks.EMPTY
    override fun getTrackSelectionParameters(): TrackSelectionParameters = TrackSelectionParameters.DEFAULT
    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}

    override fun getMediaMetadata(): MediaMetadata = mediaItems.getOrNull(0)?.mediaMetadata ?: MediaMetadata.EMPTY
    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
    override fun getCurrentTimeline(): Timeline = EqualizerTimeline()
    override fun getCurrentPeriodIndex(): Int = 0
    override fun getCurrentMediaItemIndex(): Int = 0
    override fun getDuration(): Long = C.TIME_UNSET
    override fun getCurrentPosition(): Long = 0
    override fun getBufferedPosition(): Long = 0
    override fun getTotalBufferedDuration(): Long = 0
    override fun isPlayingAd(): Boolean = false
    override fun getCurrentAdGroupIndex(): Int = C.INDEX_UNSET
    override fun getCurrentAdIndexInAdGroup(): Int = C.INDEX_UNSET
    override fun getContentPosition(): Long = 0
    override fun getContentBufferedPosition(): Long = 0
    override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT
    override fun setVolume(volume: Float) {}
    override fun getVolume(): Float = 1f
    override fun mute() {}
    override fun unmute() {}
    override fun clearVideoSurface() {}
    override fun clearVideoSurface(surface: Surface?) {}
    override fun setVideoSurface(surface: Surface?) {}
    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}
    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}
    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {}
    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {}
    override fun setVideoTextureView(textureView: TextureView?) {}
    override fun clearVideoTextureView(textureView: TextureView?) {}
    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN
    override fun getSurfaceSize(): Size = Size.UNKNOWN
    override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO
    override fun getDeviceInfo(): DeviceInfo = deviceInfo
    override fun getDeviceVolume(): Int = 0
    override fun isDeviceMuted(): Boolean = false
    override fun setDeviceVolume(volume: Int, flags: Int) {}
    override fun increaseDeviceVolume(flags: Int) {}
    override fun decreaseDeviceVolume(flags: Int) {}
    override fun setDeviceMuted(muted: Boolean, flags: Int) {}
    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {}
    override fun seekTo(mediaItemIndex: Int, positionMs: Long, seekCommand: Int, isRepeatingCurrentItem: Boolean) {}

    @Deprecated("Deprecated in Java") override fun setDeviceVolume(volume: Int) {}
    @Deprecated("Deprecated in Java") override fun increaseDeviceVolume() {}
    @Deprecated("Deprecated in Java") override fun decreaseDeviceVolume() {}
    @Deprecated("Deprecated in Java") override fun setDeviceMuted(muted: Boolean) {}

    private inner class EqualizerTimeline : Timeline() {
        override fun getWindowCount(): Int = if (mediaItems.isEmpty()) 0 else 1
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            if (mediaItems.isEmpty()) throw IndexOutOfBoundsException()
            window.set(Window.SINGLE_WINDOW_UID, mediaItems[0], null, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, false, false, null, 0, C.TIME_UNSET, 0, 0, 0)
            return window
        }
        override fun getPeriodCount(): Int = if (mediaItems.isEmpty()) 0 else 1
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            if (mediaItems.isEmpty()) throw IndexOutOfBoundsException()
            period.set(if (setIds) 0 else null, if (setIds) 0 else null, 0, C.TIME_UNSET, 0)
            return period
        }
        override fun getIndexOfPeriod(uid: Any): Int = if (uid == 0) 0 else C.INDEX_UNSET
        override fun getUidOfPeriod(periodIndex: Int): Any = 0
    }
}
