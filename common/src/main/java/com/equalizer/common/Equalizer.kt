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
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.Log
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util.getCurrentOrMainLooper
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
                    val file = mediaItems[0].localConfiguration?.uri?.path?.let { File(it) }

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
                        createNativeHandleIfNotExists()
                        nativeStop(equalizerHandle)
                        equalizerHandle=0L
                    }
                    mediaItems.clear()
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
        return DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL)
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
        listeners.remove(Assertions.checkNotNull(listener))
    }

    override fun setMediaItems(mediaItems: List<MediaItem>,
                               resetPosition: Boolean) {
        log("setMediaItems1")
        this.mediaItems.clear()
        this.mediaItems.addAll(mediaItems)
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
        TODO("Not yet implemented")
    }

    override fun getCurrentTracks(): Tracks {
        TODO("Not yet implemented")
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters = TrackSelectionParameters.getDefaults(context)


    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        TODO("Not yet implemented")
    }

    override fun getMediaMetadata(): MediaMetadata {
        TODO("Not yet implemented")
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        TODO("Not yet implemented")
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        TODO("Not yet implemented")
    }

    override fun getCurrentTimeline(): Timeline {
        TODO("Not yet implemented")
    }

    override fun getCurrentPeriodIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getCurrentMediaItemIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getDuration(): Long {
        TODO("Not yet implemented")
    }

    override fun getCurrentPosition(): Long {
        TODO("Not yet implemented")
    }

    override fun getBufferedPosition(): Long {
        TODO("Not yet implemented")
    }

    override fun getTotalBufferedDuration(): Long {
        TODO("Not yet implemented")
    }

    override fun isPlayingAd(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCurrentAdGroupIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getCurrentAdIndexInAdGroup(): Int {
        TODO("Not yet implemented")
    }

    override fun getContentPosition(): Long {
        TODO("Not yet implemented")
    }

    override fun getContentBufferedPosition(): Long {
        TODO("Not yet implemented")
    }

    override fun getAudioAttributes(): AudioAttributes {
        TODO("Not yet implemented")
    }

    override fun setVolume(volume: Float) {
        TODO("Not yet implemented")
    }

    override fun getVolume(): Float = volPerFreq[0]

    override fun clearVideoSurface() {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        TODO("Not yet implemented")
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN

    override fun getSurfaceSize(): Size {
        TODO("Not yet implemented")
    }

    override fun getCurrentCues(): CueGroup {
        TODO("Not yet implemented")
    }

    override fun getDeviceInfo(): DeviceInfo {
        return deviceInfo
    }

    override fun getDeviceVolume(): Int {
        TODO("Not yet implemented")
    }

    override fun isDeviceMuted(): Boolean {
        TODO("Not yet implemented")
    }



    override fun setDeviceVolume(volume: Int, flags: Int) {
        TODO("Not yet implemented")
    }

    override fun increaseDeviceVolume(flags: Int) {
        TODO("Not yet implemented")
    }


    override fun decreaseDeviceVolume(flags: Int) {
        TODO("Not yet implemented")
    }


    override fun setDeviceMuted(muted: Boolean, flags: Int) {
        TODO("Not yet implemented")
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        TODO("Not yet implemented")
    }

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
        isRepeatingCurrentItem: Boolean
    ) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
    override fun setDeviceVolume(volume: Int) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
    override fun increaseDeviceVolume() {
        TODO("Not yet implemented")
    }


    @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
    override fun decreaseDeviceVolume() {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
    override fun setDeviceMuted(muted: Boolean) {
        TODO("Not yet implemented")
    }

}
