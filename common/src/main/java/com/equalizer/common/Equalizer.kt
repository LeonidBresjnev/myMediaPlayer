package com.equalizer.common

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
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
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.PositionInfo
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import android.media.AudioAttributes as AndroidAudioAttributes

@UnstableApi
class Equalizer(
    private var equalizerHandle: Long = 0L,
    private val context: Context
) : BasePlayer(), Player, DefaultLifecycleObserver {

    private val equalizerMutex = Object()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun createNativeHandleIfNotExists() {
        synchronized(equalizerMutex) {
            if (equalizerHandle != 0L) {
                return
            }
            equalizerHandle = nativeCreate()
        }
    }

    private val commands = Player.Commands.Builder()
        .addAllCommands()
        .add(COMMAND_SEEK_BACK)
        .add(COMMAND_SEEK_FORWARD)
        .build()

    private external fun nativeCreate(): Long
    private external fun nativeInit(context: Context)
    private external fun nativeDelete(synthesizerHandle: Long)
    private external fun nativeStop(synthesizerHandle: Long)
    private external fun nativePlayWithVol(synthesizerHandle: Long, name: String, vol: FloatArray, deviceId: Int)
    private external fun nativeSetVolumenLow(synthesizerHandle: Long, volumeInDb: Float, freqInterval: Int)
    private external fun nativeGetDuration(synthesizerHandle: Long): Double
    private external fun nativeGetCurrentPosition(synthesizerHandle: Long): Double
    private external fun nativeSeekTo(synthesizerHandle: Long, positionSeconds: Double)
    private external fun nativeSetDelay(synthesizerHandle: Long, leftDelay: Float, rightDelay: Float)
    private external fun nativeGetFilterDesign(synthesizerHandle: Long): FloatArray?

    @UnstableApi
    data class Complex(val re: Float, val im: Float)
    @UnstableApi
    data class BandDesign(val poles: List<Complex>, val zeros: List<Complex>)
    @UnstableApi
    data class FilterDesignData(val bands: List<BandDesign>)

    @UnstableApi
    fun getRawFilterDesign(): FloatArray? {
        createNativeHandleIfNotExists()
        return synchronized(equalizerMutex) {
            if (equalizerHandle == 0L) return@synchronized null
            nativeGetFilterDesign(equalizerHandle)
        }
    }

    @UnstableApi
    fun getFilterDesign(): FilterDesignData? {
        val raw = getRawFilterDesign() ?: return null
        return parseFilterDesign(raw)
    }

    private val volPerFreq = MutableList(16) { 1f }

    fun setVolOnFreq(volumeInDb: Float, freqInterval: Int) {
        synchronized(equalizerMutex) {
            createNativeHandleIfNotExists()
            nativeSetVolumenLow(equalizerHandle, volumeInDb, freqInterval)
        }
        volPerFreq[freqInterval] = volumeInDb
        listeners.sendEvent(EVENT_VIDEO_SIZE_CHANGED) { it.onVideoSizeChanged(VideoSize(freqInterval, 0, volumeInDb)) }
    }

    fun setAllVolOnFreq(volumes: FloatArray) {
        synchronized(equalizerMutex) {
            createNativeHandleIfNotExists()
            for (i in 0 until volumes.size.coerceAtMost(16)) {
                volPerFreq[i] = volumes[i]
                nativeSetVolumenLow(equalizerHandle, volumes[i], i)
            }
        }
        for (i in 0 until volumes.size.coerceAtMost(16)) {
            listeners.sendEvent(EVENT_VIDEO_SIZE_CHANGED) { it.onVideoSizeChanged(VideoSize(i, 0, volumes[i])) }
        }
    }

    fun setDelay(left: Float, right: Float) {
        synchronized(equalizerMutex) {
            createNativeHandleIfNotExists()
            nativeSetDelay(equalizerHandle, left, right)
        }
    }

    private var applicationLooper: Looper = getCurrentOrMainLooper()
    private val clock = Clock.DEFAULT
    //private val handler = android.os.Handler(applicationLooper)

    private var listeners: ListenerSet<Player.Listener> =
        ListenerSet(applicationLooper, clock) { listener: Player.Listener, flags: FlagSet ->
            listener.onEvents(this, Player.Events(flags))
        }

    private var isAutoAdvancing = false

    private var playBackParameters = PlaybackParameters.DEFAULT
    private val seekBackIncrementMs: Long = 15000L
    private val seekForwardIncrementMs: Long = 15000L

    companion object {
        init {
            System.loadLibrary("myMediaPlayer")
        }

        @UnstableApi
        fun parseFilterDesign(raw: FloatArray): FilterDesignData {
            var idx = 0
            val numBands = raw[idx++].toInt()
            val bandInfo = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until numBands) {
                val numPoles = raw[idx++].toInt()
                val numZeros = raw[idx++].toInt()
                bandInfo.add(numPoles to numZeros)
            }

            val bands = mutableListOf<BandDesign>()
            for (info in bandInfo) {
                val poles = mutableListOf<Complex>()
                for (p in 0 until info.first) {
                    poles.add(Complex(raw[idx++], raw[idx++]))
                }
                val zeros = mutableListOf<Complex>()
                for (z in 0 until info.second) {
                    zeros.add(Complex(raw[idx++], raw[idx++]))
                }
                bands.add(BandDesign(poles, zeros))
            }

            return FilterDesignData(bands)
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

    private fun triggerNativeLoad() {
        val mediaItem = mediaItems.getOrNull(currentMediaItemIndex)
        val uri = mediaItem?.localConfiguration?.uri
        val path = if (uri != null) {
            if (uri.scheme == "http" || uri.scheme == "https") uri.toString() else uri.path
        } else {
            mediaItem?.mediaId
        }
        
        log("triggerNativeLoad: $path at index $currentMediaItemIndex")

        if (path != null) {
            val isNetworkStream = path.startsWith("http://") || path.startsWith("https://")
            val file = if (isNetworkStream) null else File(path)
            
            if (isNetworkStream || (file != null && file.exists())) {
                scope.launch(Dispatchers.IO) {
                    createNativeHandleIfNotExists()
                    nativePlayWithVol(equalizerHandle, path, volPerFreq.toFloatArray(), deviceId = getBestDeviceId())
                }
            } else {
                log("triggerNativeLoad: Path does not exist or is invalid: $path")
            }
        }
    }

    init {
        log("inited")
        nativeInit(context)
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
                    triggerNativeLoad()
                } else {
                    synchronized(equalizerMutex) {
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
    private var currentMediaItemIndex = 0
    private var repeatMode = REPEAT_MODE_OFF
    private val deviceInfo: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).build()
    private var playWhenReady = false
    private var playbackState: Int = STATE_IDLE

    override fun getApplicationLooper(): Looper = this.applicationLooper
    override fun addListener(listener: Player.Listener) = listeners.add(listener)
    override fun removeListener(listener: Player.Listener) = listeners.remove(Preconditions.checkNotNull(listener))

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        setMediaItems(mediaItems, if (resetPosition) 0 else currentMediaItemIndex, C.TIME_UNSET)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        log("setMediaItems: size=${mediaItems.size} startIndex=$startIndex")
        val oldIndex = currentMediaItemIndex
        val oldItem = this.mediaItems.getOrNull(oldIndex)
        val wasPlaying = isPlaying

        this.mediaItems.clear()
        this.mediaItems.addAll(mediaItems)
        this.currentMediaItemIndex = if (startIndex in mediaItems.indices) startIndex else 0
        
        val newTimeline = currentTimeline
        val newItem = this.mediaItems.getOrNull(currentMediaItemIndex)

        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(newTimeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }
        listeners.sendEvent(EVENT_TRACKS_CHANGED) { it.onTracksChanged(currentTracks) }
        listeners.sendEvent(EVENT_MEDIA_METADATA_CHANGED) { it.onMediaMetadataChanged(mediaMetadata) }

        val oldUid = oldItem?.mediaId ?: oldIndex.toString()
        val currentUid = newItem?.mediaId ?: currentMediaItemIndex.toString()
        val finalPosMs = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs
        
        val oldPos = PositionInfo(oldUid, oldIndex, oldItem, oldUid, oldIndex, 0L, 0L, C.INDEX_UNSET, C.INDEX_UNSET)
        val newPos = PositionInfo(currentUid, currentMediaItemIndex, newItem, currentUid, currentMediaItemIndex, finalPosMs, finalPosMs, C.INDEX_UNSET, C.INDEX_UNSET)
        
        listeners.sendEvent(EVENT_POSITION_DISCONTINUITY) { it.onPositionDiscontinuity(oldPos, newPos,
            DISCONTINUITY_REASON_AUTO_TRANSITION
        ) }
        
        if (this.mediaItems.isNotEmpty() && playbackState == STATE_IDLE) {
            playbackState = STATE_READY
            listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { it.onPlaybackStateChanged(playbackState) }
        } else if (this.mediaItems.isEmpty() && playbackState != STATE_IDLE) {
            playbackState = STATE_IDLE
            listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { it.onPlaybackStateChanged(playbackState) }
        }

        if (wasPlaying != isPlaying) {
            listeners.sendEvent(EVENT_IS_PLAYING_CHANGED) { it.onIsPlayingChanged(isPlaying) }
        }

        if (playWhenReady) triggerNativeLoad()
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        this.mediaItems.addAll(index, mediaItems)
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }
        
        if (this.mediaItems.isNotEmpty() && playbackState == STATE_IDLE) {
            playbackState = STATE_READY
            listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { it.onPlaybackStateChanged(playbackState) }
        }
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        if (fromIndex in mediaItems.indices && newIndex in mediaItems.indices) {
            val mediaItem = mediaItems.removeAt(fromIndex)
            mediaItems.add(newIndex, mediaItem)
            listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }
        }
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
        mediaItems.forEachIndexed { idx, it ->
            val targetIdx = idx + fromIndex
            if (targetIdx in this.mediaItems.indices) {
                this.mediaItems[targetIdx] = it
            }
        }
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        repeat(toIndex - fromIndex) { if (fromIndex in mediaItems.indices) mediaItems.removeAt(fromIndex) }
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }
        
        if (mediaItems.isEmpty() && playbackState != STATE_IDLE) {
            playbackState = STATE_IDLE
            listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { it.onPlaybackStateChanged(playbackState) }
        }
    }

    override fun getAvailableCommands(): Player.Commands = this.commands

    override fun prepare() {
        log("prepare() current state: $playbackState items: ${mediaItems.size}")
        if (playbackState != STATE_IDLE) return
        
        val wasPlaying = isPlaying
        if (mediaItems.isNotEmpty()) {
            playbackState = STATE_BUFFERING
            listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { it.onPlaybackStateChanged(playbackState) }
            
            // Concepts: Buffering finishes immediately in this simple sync model
            playbackState = STATE_READY
            listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { it.onPlaybackStateChanged(playbackState) }
        }
        
        if (wasPlaying != isPlaying) {
            listeners.sendEvent(EVENT_IS_PLAYING_CHANGED) { it.onIsPlayingChanged(isPlaying) }
        }
    }

    override fun getPlaybackState(): Int = playbackState
    override fun getPlaybackSuppressionReason(): Int = PLAYBACK_SUPPRESSION_REASON_NONE
    override fun getPlayerError(): PlaybackException? = null

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        log("setPlayWhenReady: $playWhenReady")
        val wasPlaying = isPlaying
        this.playWhenReady = playWhenReady
        
        listeners.sendEvent(EVENT_PLAY_WHEN_READY_CHANGED) { it.onPlayWhenReadyChanged(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        
        if (wasPlaying != isPlaying) {
            listeners.sendEvent(EVENT_IS_PLAYING_CHANGED) { it.onIsPlayingChanged(isPlaying) }
        }
        
        if (playWhenReady) {
            if (playbackState == STATE_IDLE) prepare()

            listeners.sendEvent(EVENT_MEDIA_METADATA_CHANGED) { it.onMediaMetadataChanged(mediaMetadata) }
            listeners.sendEvent(EVENT_TRACKS_CHANGED) { it.onTracksChanged(currentTracks) }
            listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_SOURCE_UPDATE) }
        }
    }

    override fun getPlayWhenReady(): Boolean = playWhenReady
    override fun setRepeatMode(repeatMode: Int) {
        this.repeatMode = repeatMode
        listeners.sendEvent(EVENT_REPEAT_MODE_CHANGED) { it.onRepeatModeChanged(repeatMode) }
    }
    override fun getRepeatMode(): Int = repeatMode
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
        scope.cancel()
        synchronized(equalizerMutex) {
            if (equalizerHandle != 0L) {
                nativeDelete(equalizerHandle)
                equalizerHandle = 0L
            }
        }
        listeners.release()
    }

    override fun getCurrentTracks(): Tracks {
        if (mediaItems.isEmpty()) return Tracks.EMPTY
        val metadata = getMediaMetadata()
        val format = Format.Builder()
            .setId("audio-${currentMediaItemIndex}")
            .setLabel(metadata.title?.toString() ?: "Track")
            // Use standard MPEG MIME type to help UI components recognize this as music with artwork
            .setSampleMimeType(MimeTypes.AUDIO_MPEG)
            .build()
        val audioTrackGroup = TrackGroup(format)
        return Tracks(listOf(Tracks.Group(audioTrackGroup, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
    }
    override fun getTrackSelectionParameters(): TrackSelectionParameters = TrackSelectionParameters.DEFAULT
    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}

    override fun getMediaMetadata(): MediaMetadata = mediaItems.getOrNull(currentMediaItemIndex)?.mediaMetadata ?: MediaMetadata.EMPTY
    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
    override fun getCurrentTimeline(): Timeline = EqualizerTimeline(mediaItems.toList())

    override fun getDuration(): Long {
        synchronized(equalizerMutex) {
            if (equalizerHandle == 0L) return C.TIME_UNSET
            val durationSeconds = nativeGetDuration(equalizerHandle)
            val durMs = (durationSeconds * 1000).toLong()
            // Return actual duration or a safe fallback if engine is still loading
            return if (durMs > 0) durMs else 300_000L 
        }
    }
    override fun getCurrentPosition(): Long {
        synchronized(equalizerMutex) {
            if (equalizerHandle == 0L) return 0L
            val positionSeconds = nativeGetCurrentPosition(equalizerHandle)
            val posMs = (positionSeconds * 1000).toLong()
            
            // Auto-advance check
            val duration = getDuration()
            if (duration > 0 && posMs >= (duration - 500) && playWhenReady && !isAutoAdvancing) {
                isAutoAdvancing = true
                scope.launch(Dispatchers.Main) { handleEndOfSong() }
            }
            return posMs
        }
    }

    private fun handleEndOfSong() {
        if (!playWhenReady) {
            isAutoAdvancing = false
            return
        }
        when (repeatMode) {
            REPEAT_MODE_ONE -> seekTo(currentMediaItemIndex, 0L)
            REPEAT_MODE_ALL -> seekTo((currentMediaItemIndex + 1) % mediaItems.size, 0L)
            else -> {
                if (currentMediaItemIndex < mediaItems.size - 1) seekTo(currentMediaItemIndex + 1, 0L)
                else {
                    setPlayWhenReady(false)
                    isAutoAdvancing = false
                }
            }
        }
    }
    override fun getBufferedPosition(): Long = 0L
    override fun getTotalBufferedDuration(): Long = 0L
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

    override fun getCurrentPeriodIndex(): Int = currentMediaItemIndex
    override fun getCurrentMediaItemIndex(): Int = currentMediaItemIndex

    override fun seekTo(mediaItemIndex: Int, positionMs: Long, seekCommand: Int, isRepeatingCurrentItem: Boolean) {
        log("seekTo: index=$mediaItemIndex position=$positionMs cmd=$seekCommand")
        
        var finalPositionMs = positionMs
        if (seekCommand == COMMAND_SEEK_BACK) {
             finalPositionMs = (currentPosition - seekBackIncrementMs).coerceAtLeast(0)
        } else if (seekCommand == COMMAND_SEEK_FORWARD) {
             val dur = duration
             finalPositionMs = (currentPosition + seekForwardIncrementMs)
             if (dur != C.TIME_UNSET) finalPositionMs = finalPositionMs.coerceAtMost(dur)
        }

        val wasAutoAdvancing = isAutoAdvancing
        isAutoAdvancing = false 
        
        if (mediaItemIndex in mediaItems.indices) {
            val oldIndex = currentMediaItemIndex
            val indexChanged = (currentMediaItemIndex != mediaItemIndex)
            currentMediaItemIndex = mediaItemIndex

            // Trigger load if track changed OR if we are restarting the same track (repeat one)
            val isRestart = !indexChanged && finalPositionMs == 0L && wasAutoAdvancing
            if ((indexChanged || isRestart) && playWhenReady) triggerNativeLoad()

            synchronized(equalizerMutex) {
                if (equalizerHandle != 0L) {
                    nativeSeekTo(equalizerHandle, finalPositionMs / 1000.0)
                }
            }

            if (indexChanged) {
                listeners.sendEvent(EVENT_TRACKS_CHANGED) { it.onTracksChanged(currentTracks) }
                listeners.sendEvent(EVENT_MEDIA_ITEM_TRANSITION) { it.onMediaItemTransition(mediaItems[currentMediaItemIndex], if (wasAutoAdvancing) MEDIA_ITEM_TRANSITION_REASON_AUTO else MEDIA_ITEM_TRANSITION_REASON_SEEK) }
                listeners.sendEvent(EVENT_MEDIA_METADATA_CHANGED) { it.onMediaMetadataChanged(mediaMetadata) }
                
                listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_SOURCE_UPDATE) }
                scope.launch(Dispatchers.Main) {
                    kotlinx.coroutines.delay(200)
                    listeners.sendEvent(EVENT_TIMELINE_CHANGED) { it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_SOURCE_UPDATE) }
                }
            }

            val oldItem = mediaItems.getOrNull(oldIndex)
            val currentItem = mediaItems.getOrNull(currentMediaItemIndex)
            
            // Use mediaId as stable String UID for cross-process robustness
            val oldUid = oldItem?.mediaId ?: oldIndex.toString()
            val currentUid = currentItem?.mediaId ?: currentMediaItemIndex.toString()

            val oldPos = PositionInfo(oldUid, oldIndex, oldItem, oldUid, oldIndex, 0L, 0L, C.INDEX_UNSET, C.INDEX_UNSET)
            val newPos = PositionInfo(currentUid, currentMediaItemIndex, currentItem, currentUid, currentMediaItemIndex, finalPositionMs, finalPositionMs, C.INDEX_UNSET, C.INDEX_UNSET)

            listeners.sendEvent(EVENT_POSITION_DISCONTINUITY) { it.onPositionDiscontinuity(oldPos, newPos, DISCONTINUITY_REASON_SEEK) }
        }
    }

    @Deprecated("Deprecated in Java") override fun setDeviceVolume(volume: Int) {}
    @Deprecated("Deprecated in Java") override fun increaseDeviceVolume() {}
    @Deprecated("Deprecated in Java") override fun decreaseDeviceVolume() {}
    @Deprecated("Deprecated in Java") override fun setDeviceMuted(muted: Boolean) {}

    private inner class EqualizerTimeline(private val itemsSnapshot: List<MediaItem>) : Timeline() {
        override fun getWindowCount(): Int = itemsSnapshot.size
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            if (windowIndex !in itemsSnapshot.indices) throw IndexOutOfBoundsException()
            val mediaItem = itemsSnapshot[windowIndex]
            val durationUs = duration.let { if (it == C.TIME_UNSET) 300_000_000L else it * 1000 }
            // Use mediaId as UID
            val uid = mediaItem.mediaId
            window.set(uid, mediaItem, mediaItem, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, true, false, null, 0, durationUs, windowIndex, windowIndex, 0)
            return window
        }
        override fun getPeriodCount(): Int = itemsSnapshot.size
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            if (periodIndex !in itemsSnapshot.indices) throw IndexOutOfBoundsException()
            val mediaItem = itemsSnapshot[periodIndex]
            val durationUs = duration.let { if (it == C.TIME_UNSET) 300_000_000L else it * 1000 }
            val uid = mediaItem.mediaId
            period.set(uid, uid, periodIndex, durationUs, 0)
            return period
        }
        override fun getIndexOfPeriod(uid: Any): Int {
            if (uid !is String) return C.INDEX_UNSET
            val index = itemsSnapshot.indexOfFirst { it.mediaId == uid }
            return if (index != -1) index else C.INDEX_UNSET
        }
        override fun getUidOfPeriod(periodIndex: Int): Any = itemsSnapshot[periodIndex].mediaId
    }
}
