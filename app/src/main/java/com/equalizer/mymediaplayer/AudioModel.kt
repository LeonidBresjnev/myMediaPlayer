package com.equalizer.mymediaplayer

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.equalizer.common.Equalizer
import com.equalizer.common.MyMediaService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@UnstableApi
enum class EqPreset(val displayName: String) {
    FLAT("Flat"),
    BASS_BOOST("Bass Boost"),
    TREBLE_BOOST("Treble Boost"),
    VOCAL("Vocal"),
    ROCK("Rock"),
    CUSTOM("Custom")
}

@UnstableApi
enum class ReverbPreset(val displayName: String) {
    OFF("Off"),
    ROOM("Room"),
    CONCERT("Concert"),
    HALL("Hall"),
    ECHO_VALLEY("Echo-valley"),
    CUSTOM("Custom")
}

@UnstableApi
class AudioModel: ViewModel() {
    /*companion object {
        private const val MEDIA_ITEM_ID_KEY = "MEDIA_ITEM_ID_KEY"
    }*/

    private fun log(message: String) {
        Log.i("AudioModel", message)
    }

    val volumenLow: LiveData<List<Float>>
        field = MutableLiveData(List(16) { 1f })

    private val _isAdvancedMode = MutableLiveData(false)
    val isAdvancedMode: LiveData<Boolean> = _isAdvancedMode

    private val _favourites = MutableLiveData<Set<String>>(emptySet())
    val favourites: LiveData<Set<String>> = _favourites

    enum class DelayUnit { MS, CM }

    private val _delayUnit = MutableLiveData(DelayUnit.MS)
    val delayUnit: LiveData<DelayUnit> = _delayUnit

    private val _leftDelayRaw = MutableLiveData(0.0f)
    val leftDelayRaw: LiveData<Float> = _leftDelayRaw

    private val _rightDelayRaw = MutableLiveData(0.0f)
    val rightDelayRaw: LiveData<Float> = _rightDelayRaw

    private val _leftDelayMs = MutableLiveData(0.0f)
    val leftDelayMs: LiveData<Float> = _leftDelayMs

    private val _rightDelayMs = MutableLiveData(0.0f)
    val rightDelayMs: LiveData<Float> = _rightDelayMs

    private val _isReverbEnabled = MutableLiveData(false)
    val isReverbEnabled: LiveData<Boolean> = _isReverbEnabled

    private val _reverbBalance = MutableLiveData(0.0f)
    val reverbBalance: LiveData<Float> = _reverbBalance

    private val _reverbR = MutableLiveData(1.0f)
    val reverbR: LiveData<Float> = _reverbR

    private val _reverbG = MutableLiveData(1.0f)
    val reverbG: LiveData<Float> = _reverbG

    private val _selectedReverbPreset = MutableLiveData(ReverbPreset.OFF)
    val selectedReverbPreset: LiveData<ReverbPreset> = _selectedReverbPreset

    data class ReverbSettings(val enabled: Boolean, val balance: Float, val r: Float, val g: Float)

    val reverbPresets = mapOf(
        ReverbPreset.OFF to ReverbSettings(false, balance=0.0f, 1.0f, 1.0f),
        ReverbPreset.ROOM to ReverbSettings(true, balance=0.5f, 0.4f, 0.5f),
        ReverbPreset.CONCERT to ReverbSettings(true, balance=0.75f, 0.6f, 0.6f),
        ReverbPreset.HALL to ReverbSettings(true, balance=0.85f, 0.85f, 0.75f),
        ReverbPreset.ECHO_VALLEY to ReverbSettings(true, balance=0.9f, 0.95f, 0.9f),
        ReverbPreset.CUSTOM to null
    )

    fun applyReverbPreset(preset: ReverbPreset) {
        val settings = reverbPresets[preset]
        _selectedReverbPreset.value = preset
        
        if (settings != null) {
            _isReverbEnabled.value = settings.enabled
            _reverbBalance.value = settings.balance
            _reverbR.value = settings.r
            _reverbG.value = settings.g
            syncReverbWithController()
        } else if (preset == ReverbPreset.CUSTOM) {
            // If user explicitly selects Custom, we keep current settings but ensure enabled
            _isReverbEnabled.value = true
            syncReverbWithController()
        }
    }

    fun setReverbBalance(v: Float) {
        if (_selectedReverbPreset.value != ReverbPreset.CUSTOM) _selectedReverbPreset.value = ReverbPreset.CUSTOM
        _isReverbEnabled.value = true
        _reverbBalance.value = v
        syncReverbWithController()
    }

    fun setReverbR(v: Float) {
        if (_selectedReverbPreset.value != ReverbPreset.CUSTOM) _selectedReverbPreset.value = ReverbPreset.CUSTOM
        _isReverbEnabled.value = true
        _reverbR.value = v
        syncReverbWithController()
    }

    fun setReverbG(v: Float) {
        if (_selectedReverbPreset.value != ReverbPreset.CUSTOM) _selectedReverbPreset.value = ReverbPreset.CUSTOM
        _isReverbEnabled.value = true
        _reverbG.value = v
        syncReverbWithController()
    }

    private fun syncReverbWithController() {
        if (::controller.isInitialized) {
            val extras = Bundle().apply {
                putBoolean("KEY_REVERB_ENABLED", _isReverbEnabled.value ?: false)
                putFloat("KEY_REVERB_BALANCE", _reverbBalance.value ?: 0.0f)
                putFloat("KEY_REVERB_R", _reverbR.value ?: 1.0f)
                putFloat("KEY_REVERB_G", _reverbG.value ?: 1.0f)
            }
            controller.sendCustomCommand(SessionCommand("setReverb", Bundle()), extras)
        }
    }

    fun setDelayUnit(unit: DelayUnit) {
        _delayUnit.value = unit
        recalculateAndSyncDelay()
    }

    fun setLeftDelay(delay: Float) {
        _leftDelayRaw.value = delay
        recalculateAndSyncDelay()
    }

    fun setRightDelay(delay: Float) {
        _rightDelayRaw.value = delay
        recalculateAndSyncDelay()
    }

    private fun recalculateAndSyncDelay() {
        val unit = _delayUnit.value ?: DelayUnit.MS
        val rawL = _leftDelayRaw.value ?: 0.0f
        val rawR = _rightDelayRaw.value ?: 0.0f
        
        val finalLeft: Float
        val finalRight: Float

        if (unit == DelayUnit.MS) {
            finalLeft = rawL
            finalRight = rawR
        } else {
            // Speed of sound is approx 343 m/s = 34.3 cm/ms
            val leftMs = rawL / 34.3f
            val rightMs = rawR / 34.3f

            if (leftMs < rightMs) {
                finalLeft = rightMs - leftMs
                finalRight = 0.0f
            } else if (rightMs < leftMs) {
                finalLeft = 0.0f
                finalRight = leftMs - rightMs
            } else {
                finalLeft = 0.0f
                finalRight = 0.0f
            }
        }

        _leftDelayMs.value = finalLeft
        _rightDelayMs.value = finalRight
        syncDelayWithController()
    }

    private fun syncDelayWithController() {
        if (::controller.isInitialized) {
            val extras = Bundle().apply {
                putFloat("KEY_LEFT_DELAY", _leftDelayMs.value ?: 0.0f)
                putFloat("KEY_RIGHT_DELAY", _rightDelayMs.value ?: 0.0f)
            }
            controller.sendCustomCommand(SessionCommand("setDelay", Bundle()), extras)
        }
    }

    fun toggleFavourite(songId: String) {
        if (!::controller.isInitialized) return
        val extras = Bundle().apply {
            putString("SONG_ID", songId)
        }
        controller.sendCustomCommand(SessionCommand("toggleFavourite", Bundle()), extras)
    }

    fun setAdvancedMode(enabled: Boolean) {
        if (_isAdvancedMode.value == enabled) return
        
        _isAdvancedMode.value = enabled
        // If disabling advanced mode, sync Left to Right
        if (!enabled) {
            val current = volumenLow.value?.toMutableList() ?: MutableList(16) { 1f }
            for (i in 0 until 8) {
                current[i + 8] = current[i]
            }
            volumenLow.value = current
            syncWithController(current)
        }

        // Notify service
        if (::controller.isInitialized) {
            val extras = Bundle().apply { putBoolean("IS_ADVANCED", enabled) }
            controller.sendCustomCommand(SessionCommand("setEqMode", Bundle()), extras)
        }
    }

    private val _selectedPreset = MutableLiveData(EqPreset.FLAT)
    val selectedPreset: LiveData<EqPreset> = _selectedPreset

    val presets = mapOf(
        EqPreset.FLAT to List(8) { 1.0f },
        EqPreset.BASS_BOOST to listOf(1.5f, 1.4f, 1.2f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f),
        EqPreset.TREBLE_BOOST to listOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.2f, 1.4f, 1.6f),
        EqPreset.VOCAL to listOf(0.8f, 0.9f, 1.0f, 1.3f, 1.4f, 1.2f, 1.0f, 0.9f),
        EqPreset.ROCK to listOf(1.3f, 1.2f, 1.1f, 1.0f, 0.9f, 1.1f, 1.2f, 1.3f),
        EqPreset.CUSTOM to emptyList() // Handled specially
    )

    fun applyPreset(preset: EqPreset) {
        if (preset == EqPreset.CUSTOM) {
            _selectedPreset.value = EqPreset.CUSTOM
            return
        }
        
        val values = presets[preset] ?: return
        _selectedPreset.value = preset
        
        // Apply preset to both L and R channels (0-7 and 8-15)
        val fullValues = values + values
        volumenLow.value = fullValues
        
        syncWithController(fullValues)
        updateFilterDesign()
    }

    private fun syncWithController(values: List<Float>) {
        if (::controller.isInitialized) {
            val extras = Bundle().apply {
                putFloatArray("KEY_VOLUMES", values.toFloatArray())
            }
            controller.sendCustomCommand(SessionCommand("setAllVolOnFreq", Bundle()), extras)
        }
    }

    val volumeRange = 0f..2f

    private var currentSlider=-1

    fun setVolumen(volumeInDb: Float, index: Int) {
        // Switch to Custom if user adjusts a slider manually
        if (_selectedPreset.value != EqPreset.CUSTOM) {
            _selectedPreset.value = EqPreset.CUSTOM
        }

        // Update local state immediately for better responsiveness
        val currentList = volumenLow.value?.toMutableList() ?: MutableList(16) { 1f }
        if (index in 0 until 16) {
            currentList[index] = volumeInDb
            
            // If NOT in advanced mode, sync the other channel
            if (_isAdvancedMode.value != true) {
                if (index < 8) {
                    currentList[index + 8] = volumeInDb
                } else {
                    currentList[index - 8] = volumeInDb
                }
            }
            
            volumenLow.value = currentList
        }

        currentSlider = index
        val extras = Bundle().apply {
            putInt("KEY_INDEX", index)
            putFloatArray("KEY_VOLUMES", currentList.toFloatArray())
        }
        // Send as a bulk update for better sync
        val customCommand = SessionCommand("setAllVolOnFreq", Bundle())

        if (::controller.isInitialized) {
            controller.sendCustomCommand(customCommand, extras)
        }
        updateFilterDesign()
    }

    fun resetEqualizer() {
        Log.d("AudioModel", "resetEqualizer called")
        applyPreset(EqPreset.FLAT)
    }


    private val _isPlaying = MutableLiveData(Status.STOPPED)
    val isPlaying: LiveData<Status> = _isPlaying
    
    private val _nowPlayingId = MutableLiveData<String?>(null)
    val nowPlayingId: LiveData<String?> = _nowPlayingId

    private val _nextMediaItem = MutableLiveData<MediaItem?>(null)
    val nextMediaItem: LiveData<MediaItem?> = _nextMediaItem

    @OptIn(UnstableApi::class)
    private val _filterDesign = MutableLiveData<Equalizer.FilterDesignData?>(null)
    @OptIn(UnstableApi::class)
    val filterDesign: LiveData<Equalizer.FilterDesignData?> = _filterDesign

    private val _magnitudeResponse = MutableLiveData<FloatArray?>(null)
    val magnitudeResponse: LiveData<FloatArray?> = _magnitudeResponse

    private val _unoptimizedMagnitudeResponse = MutableLiveData<FloatArray?>(null)
    val unoptimizedMagnitudeResponse: LiveData<FloatArray?> = _unoptimizedMagnitudeResponse

    private val _phaseResponse = MutableLiveData<FloatArray?>(null)
    val phaseResponse: LiveData<FloatArray?> = _phaseResponse

    private val _unoptimizedPhaseResponse = MutableLiveData<FloatArray?>(null)
    val unoptimizedPhaseResponse: LiveData<FloatArray?> = _unoptimizedPhaseResponse

    private val _isDbScale = MutableLiveData(false)
    val isDbScale: LiveData<Boolean> = _isDbScale

    fun setDbScale(enabled: Boolean) {
        _isDbScale.value = enabled
    }

    private var analysisUpdateJob: kotlinx.coroutines.Job? = null

    @OptIn(UnstableApi::class)
    fun updateFilterDesign() {
        if (!::controller.isInitialized) return
        
        analysisUpdateJob?.cancel()
        analysisUpdateJob = viewModelScope.launch {
            delay(100.milliseconds) // Debounce analysis requests
            
            val future = controller.sendCustomCommand(SessionCommand("getFilterDesign", Bundle()), Bundle())
            future.addListener({
                val result = try { future.get() } catch (e: Exception) {
                    e.message?.let { Log.e("AudioModel", it) }
                    null
                }
                if (result?.resultCode == SessionResult.RESULT_SUCCESS) {
                    val raw = result.extras.getFloatArray("DESIGN_DATA")
                    if (raw != null) {
                        viewModelScope.launch(Dispatchers.Default) {
                            val parsed = Equalizer.parseFilterDesign(raw)
                            _filterDesign.postValue(parsed)
                        }
                    }
                }
            }, MoreExecutors.directExecutor())

            val analysisFuture = controller.sendCustomCommand(SessionCommand("getAnalysis", Bundle()), Bundle())
            analysisFuture.addListener({
                val result = analysisFuture.get()
                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                    val raw = result.extras.getFloatArray("ANALYSIS_DATA")
                    if (raw != null) {
                        val mag = FloatArray(raw.size / 2)
                        val phase = FloatArray(raw.size / 2)
                        for (i in 0 until raw.size / 2) {
                            mag[i] = raw[i * 2]
                            phase[i] = raw[i * 2 + 1]
                        }
                        _magnitudeResponse.postValue(mag)
                        _phaseResponse.postValue(phase)
                    }
                }
            }, MoreExecutors.directExecutor())

            val unoptFuture = controller.sendCustomCommand(SessionCommand("getUnoptimizedAnalysis", Bundle()), Bundle())
            unoptFuture.addListener({
                val result = unoptFuture.get()
                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                    val raw = result.extras.getFloatArray("ANALYSIS_DATA")
                    if (raw != null) {
                        val mag = FloatArray(raw.size / 2)
                        val phase = FloatArray(raw.size / 2)
                        for (i in 0 until raw.size / 2) {
                            mag[i] = raw[i * 2]
                            phase[i] = raw[i * 2 + 1]
                        }
                        _unoptimizedMagnitudeResponse.postValue(mag)
                        _unoptimizedPhaseResponse.postValue(phase)
                    }
                }
            }, MoreExecutors.directExecutor())
        }
    }

    enum class Status {
        PLAYING ,
        PAUSED,
        STOPPED
    }


    val subItemMediaList: LiveData<List<MediaItem>>
        field = MutableLiveData<List<MediaItem>>(emptyList())

    private val _radioMediaList = MutableLiveData<List<MediaItem>>(emptyList())
    val radioMediaList: LiveData<List<MediaItem>> = _radioMediaList

    private val _currentPath = MutableLiveData("music_library_root")
    val currentPath: LiveData<String> = _currentPath

    private val _currentPlaybackContext = MutableLiveData<String?>(null)
    val currentPlaybackContext: LiveData<String?> = _currentPlaybackContext

    private val _playlists = MutableLiveData<List<MediaItem>>(emptyList())
    val playlists: LiveData<List<MediaItem>> = _playlists

    private val navStack = mutableListOf<String>()

    private val handler = Handler(Looper.getMainLooper())

    private val _mediaController = MutableLiveData<Player?>(null)
    val mediaController: LiveData<Player?> = _mediaController

    private var mediaControllerFuture: ListenableFuture<MediaBrowser>? = null

    private lateinit var controller: MediaBrowser

    //private lateinit var libResult : ListenableFuture<LibraryResult<MediaItem>>

    private fun handlePlaybackBasedOnState() {
        // Standard Player listener for playback events
        controller.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isitplaying: Boolean) {
                log("is it playing = $isitplaying")
                _isPlaying.value = if (isitplaying) {
                     Status.PLAYING
                } else {
                    if (controller.playWhenReady) Status.PAUSED
                    else Status.STOPPED
                }
            }

            @OptIn(UnstableApi::class)
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // Keep for legacy compatibility if needed
                volumenLow.value = volumenLow.value!!.mapIndexed { i, v -> if (i==videoSize.width) videoSize.pixelWidthHeightRatio else v }
                super.onVideoSizeChanged(videoSize)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> log("Player is idle")
                    Player.STATE_BUFFERING -> log("Player is buffering")
                    Player.STATE_ENDED -> log("The player is finished")
                    Player.STATE_READY -> log("Player is ready")
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                log("Track changed: ${mediaItem?.mediaId}")
                _nowPlayingId.postValue(mediaItem?.mediaId)
                updateNextMediaItem()
                updateFilterDesign()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                super.onTimelineChanged(timeline, reason)
                updateNextMediaItem()
            }

            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                super.onAvailableCommandsChanged(availableCommands)
                log("commands changed$availableCommands")
            }
        })
    }

    private fun updateNextMediaItem() {
        if (!::controller.isInitialized) return
        val nextIndex = controller.nextMediaItemIndex
        if (nextIndex != androidx.media3.common.C.INDEX_UNSET) {
            val timeline = controller.currentTimeline
            if (!timeline.isEmpty && nextIndex < timeline.windowCount) {
                val mediaItem = timeline.getWindow(nextIndex, androidx.media3.common.Timeline.Window()).mediaItem
                _nextMediaItem.postValue(mediaItem)
            } else {
                _nextMediaItem.postValue(null)
            }
        } else {
            _nextMediaItem.postValue(null)
        }
    }

    @OptIn(UnstableApi::class)
    internal fun initializeMediaController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MyMediaService::class.java))

        // MediaController/Browser Listener for session-specific events like extras
        val browserListener = object : MediaBrowser.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                val eqState = extras.getFloatArray("EQ_STATE")
                if (eqState != null && (eqState.size == 8 || eqState.size == 16)) {
                    log("Updating phone UI from session extras")
                    if (eqState.size == 8) {
                        volumenLow.postValue(eqState.toList() + eqState.toList())
                    } else {
                        volumenLow.postValue(eqState.toList())
                    }
                }
                
                val advanced = extras.getBoolean("IS_ADVANCED", false)
                if (_isAdvancedMode.value != advanced) {
                    _isAdvancedMode.postValue(advanced)
                }

                val favs = extras.getStringArray("FAVOURITES")
                if (favs != null) {
                    _favourites.postValue(favs.toSet())
                }

                val designRaw = extras.getFloatArray("FILTER_DESIGN")
                if (designRaw != null) {
                    _filterDesign.postValue(Equalizer.parseFilterDesign(designRaw))
                }
            }
        }

        mediaControllerFuture = MediaBrowser
            .Builder(context, sessionToken)
            .setListener(browserListener)
            .buildAsync()

        mediaControllerFuture?.apply {
            addListener({
                controller = this.get()
                _mediaController.postValue(controller)
                //log(controller.availableCommands.toString())
                log("MediaController connected")
                
                // Initial browse
                browse("music_library_root"/*, context = context*/)
                fetchPlaylists()

                // Sync initial state if available
                val sessionExtras = controller.sessionExtras
                val eqState = sessionExtras.getFloatArray("EQ_STATE")
                if (eqState != null && (eqState.size == 8 || eqState.size == 16)) {
                    if (eqState.size == 8) {
                        volumenLow.postValue(eqState.toList() + eqState.toList())
                    } else {
                        volumenLow.postValue(eqState.toList())
                    }
                }
                
                val advanced = sessionExtras.getBoolean("IS_ADVANCED", false)
                _isAdvancedMode.postValue(advanced)

                val favs = sessionExtras.getStringArray("FAVOURITES")
                if (favs != null) {
                    _favourites.postValue(favs.toSet())
                }

                val designRaw = sessionExtras.getFloatArray("FILTER_DESIGN")
                if (designRaw != null) {
                    _filterDesign.postValue(Equalizer.parseFilterDesign(designRaw))
                }

                updateFilterDesign()
                handlePlaybackBasedOnState()
                
                // Initial browse
                browse("icecast_root")

            }, MoreExecutors.directExecutor())
        }
    }

    @OptIn(UnstableApi::class)
    fun browse(parentId: String, addToStack: Boolean = true /*, context: Context? = null*/) {
        if (!::controller.isInitialized) return
        
        log("Browsing: $parentId")
        val childrenFuture = controller.getChildren(parentId, 0, Int.MAX_VALUE, null)
        childrenFuture.addListener({
            try {
                val result = childrenFuture.get()
                if (result.value != null) {
                    if (parentId == "icecast_root") {
                        _radioMediaList.value = result.value!!
                        _currentPath.value = parentId
                    } else {
                        subItemMediaList.value = result.value!!
                        if (addToStack && parentId != _currentPath.value) {
                            _currentPath.value?.let { navStack.add(it) }
                        }
                        _currentPath.value = parentId
                    }
                }
            } catch (e: Exception) {
                log("Error getting children: ${e.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    fun navigateBack(/*context: Context? = null*/): Boolean {
        if (navStack.isEmpty()) return false
        
        val lastPath = navStack.removeAt(navStack.size - 1)
        browse(lastPath, addToStack = false/*, context = context*/)
        return true
    }

    fun fetchPlaylists() {
        if (!::controller.isInitialized) return
        val childrenFuture = controller.getChildren("playlists_root", 0, Int.MAX_VALUE, null)
        childrenFuture.addListener({
            try {
                val result = childrenFuture.get()
                if (result.value != null) {
                    _playlists.postValue(result.value!!)
                }
            } catch (e: Exception) {
                log("Error getting playlists: ${e.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    fun createPlaylist(name: String) {
        if (!::controller.isInitialized) return
        val extras = Bundle().apply {
            putString("NAME", name)
        }
        controller.sendCustomCommand(SessionCommand("createPlaylist", Bundle()), extras)
        // Refresh playlists after a short delay
        handler.postDelayed({ fetchPlaylists() }, 500)
    }

    fun addToPlaylist(songId: String, playlistId: String) {
        if (!::controller.isInitialized) return
        val extras = Bundle().apply {
            putString("SONG_ID", songId)
            putString("PLAYLIST_ID", playlistId)
        }
        controller.sendCustomCommand(SessionCommand("addToPlaylist", Bundle()), extras)
    }

    fun deletePlaylist(playlistId: String) {
        if (!::controller.isInitialized) return
        val extras = Bundle().apply {
            putString("PLAYLIST_ID", playlistId)
        }
        controller.sendCustomCommand(SessionCommand("deletePlaylist", Bundle()), extras)
        // Refresh playlists after a short delay
        handler.postDelayed({ fetchPlaylists() }, 500)
    }
/*
    internal fun loadMedia(mediaItem: MediaItem) {
        if (!::controller.isInitialized) return
        controller.setMediaItem(mediaItem)
        controller.prepare()
        log("Media loaded and prepared: ${mediaItem.mediaId}")
    }*/
    internal fun loadMedia(mediaItems: List<MediaItem>, startIndex: Int) {
        if (!::controller.isInitialized) return
        // Load the whole folder as a playlist starting at the selected song
        _currentPlaybackContext.value = _currentPath.value
        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
    }
/*
    internal fun playMedia(mediaItem: MediaItem) {
        if (!::controller.isInitialized) return

        log("playbackState is ${controller.playbackState}, playwhenready=${controller.playWhenReady}")

        when (controller.playbackState) {
            Player.STATE_IDLE -> {
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
                log("player is prepared, and playing")
            }
            Player.STATE_BUFFERING -> log("Player is buffering")
            Player.STATE_READY -> {
                controller.play()
                log("player is playing")
            }
            Player.STATE_ENDED -> {
                log("The player is finished")
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }
        }
    }*/
/*
    internal fun stopMedia() {
        controller.pause()
    }*/

    override fun onCleared() {
        //super.onCleared()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controller.release()
    }
}
