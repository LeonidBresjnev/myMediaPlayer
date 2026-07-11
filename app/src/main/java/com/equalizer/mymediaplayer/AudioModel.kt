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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
//import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.equalizer.common.MyMediaService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class AudioModel: ViewModel() {
    /*companion object {
        private const val MEDIA_ITEM_ID_KEY = "MEDIA_ITEM_ID_KEY"
    }*/

    private fun log(message: String) {
        Log.i("AudioModel", message)
    }

    private val _volumenLow = MutableLiveData(List(16){1f})

    val volumenLow: LiveData<List<Float>>
        get() {
            return _volumenLow
        }

    private val _isAdvancedMode = MutableLiveData(false)
    val isAdvancedMode: LiveData<Boolean> = _isAdvancedMode

    private val _favourites = MutableLiveData<Set<String>>(emptySet())
    val favourites: LiveData<Set<String>> = _favourites

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
            val current = _volumenLow.value?.toMutableList() ?: MutableList(16) { 1f }
            for (i in 0 until 8) {
                current[i + 8] = current[i]
            }
            _volumenLow.value = current
            syncWithController(current)
        }

        // Notify service
        if (::controller.isInitialized) {
            val extras = Bundle().apply { putBoolean("IS_ADVANCED", enabled) }
            controller.sendCustomCommand(SessionCommand("setEqMode", Bundle()), extras)
        }
    }

    private val _selectedPreset = MutableLiveData("Flat")
    val selectedPreset: LiveData<String> = _selectedPreset

    val presets = mapOf(
        "Flat" to List(8) { 1.0f },
        "Bass Boost" to listOf(1.5f, 1.4f, 1.2f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f),
        "Treble Boost" to listOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.2f, 1.4f, 1.6f),
        "Vocal" to listOf(0.8f, 0.9f, 1.0f, 1.3f, 1.4f, 1.2f, 1.0f, 0.9f),
        "Rock" to listOf(1.3f, 1.2f, 1.1f, 1.0f, 0.9f, 1.1f, 1.2f, 1.3f),
        "Custom" to emptyList() // Handled specially
    )

    fun applyPreset(name: String) {
        if (name == "Custom") {
            _selectedPreset.value = "Custom"
            return
        }
        
        val values = presets[name] ?: return
        _selectedPreset.value = name
        
        // Apply preset to both L and R channels (0-7 and 8-15)
        val fullValues = values + values
        _volumenLow.value = fullValues
        
        syncWithController(fullValues)
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
        if (_selectedPreset.value != "Custom") {
            _selectedPreset.value = "Custom"
        }

        // Update local state immediately for better responsiveness
        val currentList = _volumenLow.value?.toMutableList() ?: MutableList(16) { 1f }
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
            
            _volumenLow.value = currentList
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
    }

    fun resetEqualizer() {
        Log.d("AudioModel", "resetEqualizer called")
        applyPreset("Flat")
    }


    private val _isPlaying = MutableLiveData(Status.STOPPED)
    
    private val _nowPlayingId = MutableLiveData<String?>(null)
    val nowPlayingId: LiveData<String?> = _nowPlayingId

    private val _nextMediaItem = MutableLiveData<MediaItem?>(null)
    val nextMediaItem: LiveData<MediaItem?> = _nextMediaItem

    enum class Status {
        PLAYING ,
        PAUSED,
        STOPPED
    }


    private val _subItemMediaList = MutableLiveData<List<MediaItem>>(emptyList())
    val subItemMediaList : LiveData<List<MediaItem>>
        get() {
            return _subItemMediaList
    }

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
                _volumenLow.value = _volumenLow.value!!.mapIndexed { i, v -> if (i==videoSize.width) videoSize.pixelWidthHeightRatio else v }
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
                        _volumenLow.postValue(eqState.toList() + eqState.toList())
                    } else {
                        _volumenLow.postValue(eqState.toList())
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
                        _volumenLow.postValue(eqState.toList() + eqState.toList())
                    } else {
                        _volumenLow.postValue(eqState.toList())
                    }
                }
                
                val advanced = sessionExtras.getBoolean("IS_ADVANCED", false)
                _isAdvancedMode.postValue(advanced)

                val favs = sessionExtras.getStringArray("FAVOURITES")
                if (favs != null) {
                    _favourites.postValue(favs.toSet())
                }

                handlePlaybackBasedOnState()

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
                    _subItemMediaList.value = result.value!!
                    if (addToStack && parentId != _currentPath.value) {
                        _currentPath.value?.let { navStack.add(it) }
                    }
                    _currentPath.value = parentId
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
