package com.equalizer.common

//import androidx.media3.common.util.Log
import android.content.Context
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
//import androidx.media3.common.util.Assertions
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
    private external fun nativePlay(synthesizerHandle: Long, name: String)
    private external fun nativePlayWithVol(synthesizerHandle: Long,
                                           name: String,
                                           vol: FloatArray)
    private external fun nativeIsPlaying(synthesizerHandle: Long): Boolean
    private external fun nativeSetVolumenLow(synthesizerHandle: Long,volumeInDb: Float, freqInterval: Int)

/*
    var onVolArrayChanged: (volPerFreq: List<Float>) -> Unit = {}

    fun setOnVolArrayChanged(listener: (List<Float>) -> Unit) {
       onVolArrayChanged = listener
    }*/

    private val volPerFreq = MutableList(8) { 1f }
    fun setVolOnFreq(volumeInDb: Float, freqInterval: Int) {
        synchronized(equalizerMutex){
            createNativeHandleIfNotExists()
            nativeSetVolumenLow(equalizerHandle, volumeInDb, freqInterval)
        }
        volPerFreq[freqInterval] = volumeInDb
        listeners.sendEvent(1
        ) {
           // listener: Player.Listener -> listener.onVolumeChanged(volumeInDb)
            listener: Player.Listener -> listener.onVideoSizeChanged(
            VideoSize(freqInterval, 0, volumeInDb))
        }

       // onVolArrayChanged(volPerFreq)
       // myListeners.onVolArrayChanged()
    }


    fun getVolOnFreqs(): List<Float> = volPerFreq



    private var applicationLooper: Looper= getCurrentOrMainLooper()
    private val clock = Clock.DEFAULT

    private var listeners: ListenerSet<Player.Listener> =
        ListenerSet(
            applicationLooper,
            clock
        ) { listener: Player.Listener, flags: FlagSet ->
            listener.onEvents(this, Player.Events(flags) )
        }


    /*       ListenerSet(
               applicationLooper,
               clock
           ) { listener: Player.Listener, flags: FlagSet ->
               log(flags.toString())
               listener.onEvents(this, Player.Events( flags ) )
           }*/


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

    // Playback information when there is no pending seek/set source operation.
    //private val playbackInfo: PlaybackInfo? = null


    companion object {
        init {
            System.loadLibrary("myMediaPlayer")
        }
    }


    private fun log(message: String) {
        Log.d("Equalizer", message)
    }

    init {
        log("inited")
        //this.playbackState= STATE_IDLE
        listeners.add(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                log("isPlaying: $isPlaying")
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    val file = mediaItems.getOrNull(0)?.localConfiguration?.uri?.path?.let { File(it) }

                    file?.let {
                        if (file.exists()) {
                            synchronized(equalizerMutex) {
                                createNativeHandleIfNotExists()
                                log("play: ${file.absolutePath}")


                                nativePlayWithVol(equalizerHandle,
                                    file.absolutePath,
                                    volPerFreq.toFloatArray())
                            }
                        }
                    }
                } else {
                    synchronized(equalizerMutex){
                        if (equalizerHandle != 0L) {
                            nativeStop(equalizerHandle)
                        }
                    }
                    // DO NOT CLEAR MEDIA ITEMS ON PAUSE
                }
            }


/*
            override fun onVolumeChanged(volume: Float) {
                log ("volume: $volume")
                super.onVolumeChanged(volume)
            }*/

            override fun onPlaybackStateChanged(playbackState: Int) {
                log("playbackState: $playbackState")
                super.onPlaybackStateChanged(playbackState)
            }

            override fun onPlayerError(error: PlaybackException) {
                log("player error: ${error.message}")
                super.onPlayerError(error)
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                log("playbackParameters: $playbackParameters")
                super.onPlaybackParametersChanged(playbackParameters)
            }

        }

        )
    }


    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        synchronized(equalizerMutex) {
            createNativeHandleIfNotExists()
            if (equalizerHandle == 0L) equalizerHandle = nativeCreate()

        }
    }


    //private external fun create(): Long



    val mediaItems: MutableList<MediaItem> = mutableListOf()

    private val deviceInfo: DeviceInfo= createDeviceInfo()

    private var playWhenReady=false

    private fun createDeviceInfo(): DeviceInfo {
        return DeviceInfo
            .Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL)
            .build()
    }

    private var buildCalled = false
/*
    fun build(): Equalizer {
        Assertions.checkState(!buildCalled)
        buildCalled = true
        if (suitableOutputChecker == null && Util.SDK_INT >= 35 && suppressPlaybackOnUnsuitableOutput) {
            suitableOutputChecker = DefaultSuitableOutputChecker(context, Handler(looper))
        }
        return ExoPlayerImpl( /* builder= */this,  /* wrappingPlayer= */null)
    }*/

    override fun getApplicationLooper(): Looper {
        // Don't verify application thread. We allow calls to this method from any thread.
        return this.applicationLooper
    }

    override fun addListener(listener: Player.Listener) {
        log("add listener")

        // Don't verify application thread. We allow calls to this method from any thread.
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        log("remove listener")
        listeners.remove(Preconditions.checkNotNull(listener))
    }

    override fun setMediaItems(mediaItems: List<MediaItem>,
                               resetPosition: Boolean) {
        log("setMediaItems1")
        this.mediaItems.clear()
        this.mediaItems.addAll(mediaItems)
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { listener: Player.Listener ->
            listener.onTimelineChanged(currentTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        }
        listeners.sendEvent(EVENT_MEDIA_METADATA_CHANGED) { listener: Player.Listener ->
            listener.onMediaMetadataChanged(mediaMetadata)
        }
        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener: Player.Listener ->
            listener.onPlaybackStateChanged(playbackState)
        }
    }


    override fun setMediaItems(
        _mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) {
        log("setMediaItems2")
        log(_mediaItems.joinToString(prefix="size=${_mediaItems.size}") { "${it.mediaId}, ${it.mediaMetadata.title} ${it.mediaMetadata.artist}, ${it.mediaMetadata.mediaType}" })
        this.mediaItems.clear()
        this.mediaItems.addAll(_mediaItems)
        log(mediaItems.joinToString(prefix="size=${mediaItems.size}") { "${it.mediaId}, ${it.mediaMetadata.title},  ${it.mediaMetadata.artist}, ${it.mediaMetadata.mediaType}" })
        listeners.sendEvent(EVENT_TIMELINE_CHANGED) { listener: Player.Listener ->
            listener.onTimelineChanged(currentTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        }
        listeners.sendEvent(EVENT_MEDIA_METADATA_CHANGED) { listener: Player.Listener ->
            listener.onMediaMetadataChanged(mediaMetadata)
        }
        listeners.sendEvent(EVENT_PLAYBACK_STATE_CHANGED) { listener: Player.Listener ->
            listener.onPlaybackStateChanged(playbackState)
        }
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        log("addMediaItems3")
        this.mediaItems.addAll(mediaItems)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        log("move Media Items")
        val mediaItem = mediaItems.removeAt(fromIndex)
        mediaItems.add(newIndex, mediaItem)
    }

    override fun replaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: MutableList<MediaItem>
    ) {
        log("replace media items")
        mediaItems.forEachIndexed { idx,it -> this.mediaItems[idx+fromIndex] = it  }
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        log("remove media items")
        repeat(toIndex-fromIndex) {
            mediaItems.removeAt(fromIndex)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        return (this.commands)
    }

    override fun prepare() {

        Log.d("Equalizer", "prepare() called")
    }

    override fun getPlaybackState(): Int {
       // log("get playback state: ${this.playWhenReady}")
        return if (mediaItems.isNotEmpty()) STATE_READY
        else if (mediaItems.isEmpty()) STATE_IDLE
        else STATE_ENDED
    }

    override fun getPlaybackSuppressionReason(): Int = PLAYBACK_SUPPRESSION_REASON_NONE
        //Player.PlaybackSuppressionReason()


    override fun getPlayerError(): PlaybackException? = null

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        log("setPlayWhenReady: $playWhenReady")

        this.playWhenReady=playWhenReady

        listeners.sendEvent(
            EVENT_PLAY_WHEN_READY_CHANGED
        ) { listener: Player.Listener -> listener.onPlayWhenReadyChanged(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        listeners.sendEvent(
            EVENT_IS_PLAYING_CHANGED
        ) { listener: Player.Listener -> listener.onIsPlayingChanged(playWhenReady) }

    }

    override fun getPlayWhenReady(): Boolean = playWhenReady

    override fun setRepeatMode(repeatMode: Int) {
        TODO("Not yet implemented")
    }

    override fun getRepeatMode(): Int = REPEAT_MODE_OFF

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        TODO("Not yet implemented")
    }

    override fun getShuffleModeEnabled(): Boolean = false

    override fun isLoading(): Boolean = false

    override fun getSeekBackIncrement(): Long = seekBackIncrementMs

    override fun getSeekForwardIncrement(): Long = seekForwardIncrementMs

    override fun getMaxSeekToPreviousPosition(): Long = C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playBackParameters = playbackParameters
    }

    override fun getPlaybackParameters(): PlaybackParameters = this.playBackParameters


    override fun stop() {
        Log.d("Equalizer", "stop")
        setPlayWhenReady(playWhenReady=false)
    }

    override fun release() {
        log("release")
        synchronized(equalizerMutex) {
            if (equalizerHandle != 0L) {
                nativeDelete(equalizerHandle)
                equalizerHandle = 0L
            }
        }
        listeners.release()
    }

    override fun getCurrentTracks(): Tracks {
        return Tracks.EMPTY
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters = TrackSelectionParameters.DEFAULT


    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        TODO("Not yet implemented")
    }

    override fun getMediaMetadata(): MediaMetadata {
        return mediaItems.getOrNull(0)?.mediaMetadata ?: MediaMetadata.EMPTY
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        return MediaMetadata.EMPTY
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
    }

    override fun getCurrentTimeline(): Timeline {
        return EqualizerTimeline()
    }

    override fun getCurrentPeriodIndex(): Int {
        return if (mediaItems.isNotEmpty()) 0 else 0
    }

    override fun getCurrentMediaItemIndex(): Int {
        return if (mediaItems.isNotEmpty()) 0 else 0
    }

    override fun getDuration(): Long {
        return C.TIME_UNSET
    }

    override fun getCurrentPosition(): Long {
        return 0
    }

    override fun getBufferedPosition(): Long {
        return 0
    }

    override fun getTotalBufferedDuration(): Long {
        return 0
    }

    override fun isPlayingAd(): Boolean {
        return false
    }

    override fun getCurrentAdGroupIndex(): Int {
        return C.INDEX_UNSET
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        return C.INDEX_UNSET
    }

    override fun getContentPosition(): Long {
        return 0
    }

    override fun getContentBufferedPosition(): Long {
        return 0
    }

    override fun getAudioAttributes(): AudioAttributes {
        return AudioAttributes.DEFAULT
    }

    override fun setVolume(volume: Float) {
        // Not implemented for master volume yet
    }

    override fun getVolume(): Float = 1f
    override fun mute() {
    }

    override fun unmute() {
    }

    override fun clearVideoSurface() {
    }

    override fun clearVideoSurface(surface: Surface?) {
    }

    override fun setVideoSurface(surface: Surface?) {
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
    }

    override fun setVideoTextureView(textureView: TextureView?) {
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
    }

    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN

    override fun getSurfaceSize(): Size {
        return Size.UNKNOWN
    }

    override fun getCurrentCues(): CueGroup {
        return CueGroup.EMPTY_TIME_ZERO
    }

    override fun getDeviceInfo(): DeviceInfo {
        return deviceInfo
    }

    override fun getDeviceVolume(): Int {
        return 0
    }

    override fun isDeviceMuted(): Boolean {
        return false
    }



    override fun setDeviceVolume(volume: Int, flags: Int) {
    }

    override fun increaseDeviceVolume(flags: Int) {
    }


    override fun decreaseDeviceVolume(flags: Int) {
    }


    override fun setDeviceMuted(muted: Boolean, flags: Int) {
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
    }

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
        isRepeatingCurrentItem: Boolean
    ) {
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
    }

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() {
    }


    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() {
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean) {
    }

    private inner class EqualizerTimeline : Timeline() {
        override fun getWindowCount(): Int = if (mediaItems.isEmpty()) 0 else 1

        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            if (mediaItems.isEmpty()) throw IndexOutOfBoundsException()
            val mediaItem = mediaItems[0]
            window.set(
                Window.SINGLE_WINDOW_UID,
                mediaItem,
                /* manifest= */ null,
                /* presentationStartTimeMs= */ C.TIME_UNSET,
                /* windowStartTimeMs= */ C.TIME_UNSET,
                /* elapsedRealtimeEpochOffsetMs= */ C.TIME_UNSET,
                /* isSeekable= */ false,
                /* isDynamic= */ false,
                /* liveConfiguration= */ null,
                /* defaultPositionUs= */ 0,
                /* durationUs= */ C.TIME_UNSET,
                /* firstPeriodIndex= */ 0,
                /* lastPeriodIndex= */ 0,
                /* positionInFirstPeriodUs= */ 0
            )
            return window
        }

        override fun getPeriodCount(): Int = if (mediaItems.isEmpty()) 0 else 1

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            if (mediaItems.isEmpty()) throw IndexOutOfBoundsException()
            period.set(
                /* id= */ if (setIds) 0 else null,
                /* uid= */ if (setIds) 0 else null,
                /* windowIndex= */ 0,
                /* durationUs= */ C.TIME_UNSET,
                /* positionInWindowUs= */ 0
            )
            return period
        }

        override fun getIndexOfPeriod(uid: Any): Int = if (uid == 0) 0 else C.INDEX_UNSET

        override fun getUidOfPeriod(periodIndex: Int): Any = 0
    }

}
