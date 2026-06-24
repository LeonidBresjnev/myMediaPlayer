package com.equalizer.mymediaplayer

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.equalizer.common.MyMediaService
import com.google.common.util.concurrent.ListenableFuture
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.viewModelScope
import com.equalizer.common.OnlineInfo
import com.equalizer.common.OnlineMetadataManager
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioModel: ViewModel() {
    companion object {
        private const val MEDIA_ITEM_ID_KEY = "MEDIA_ITEM_ID_KEY"
/*
        fun createIntent(context: Context, mediaItemID: String): Intent {
            val intent = Intent(context, PlayableFolderActivity::class.java)
            intent.putExtra(MEDIA_ITEM_ID_KEY, mediaItemID)
            return intent
        }*/
    }

    private fun log(message: String) {
        Log.i("AudioModel", message)
    }

    private val _volumenLow = MutableLiveData(List(8){1f})

    val volumenLow: LiveData<List<Float>>
        get() {
            return _volumenLow
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
        _volumenLow.value = values
        
        if (::controller.isInitialized) {
            values.forEachIndexed { i, v ->
                val extras = Bundle().apply {
                    putInt("KEY_INDEX", i)
                    putFloat("KEY_VOLUME", v)
                }
                controller.sendCustomCommand(SessionCommand("setVolOnFreq", Bundle()), extras)
            }
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
        val currentList = _volumenLow.value?.toMutableList() ?: MutableList(8) { 1f }
        if (index in 0 until 8) {
            currentList[index] = volumeInDb
            _volumenLow.value = currentList
        }

        currentSlider = index
        val extras = Bundle().apply {
            putInt("KEY_INDEX", index)
            putFloat("KEY_VOLUME", volumeInDb)
        }
        val customCommand = SessionCommand("setVolOnFreq", Bundle())

        if (::controller.isInitialized) {
            controller.sendCustomCommand(customCommand, extras)
        }
    }

    fun resetEqualizer() {
        Log.d("AudioModel", "resetEqualizer called")
        applyPreset("Flat")
    }


    private val _isPlaying = MutableLiveData(Status.STOPPED)
    val isPlaying: LiveData<Status>
        get() {
            return _isPlaying
        }

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

    private val _currentPath = MutableLiveData("root")
    val currentPath: LiveData<String> = _currentPath

    private val navStack = mutableListOf<String>()

    private val _mediaController = MutableLiveData<Player?>(null)
    val mediaController: LiveData<Player?> = _mediaController

    private var mediaControllerFuture: ListenableFuture<MediaBrowser>? = null

    private lateinit var controller: MediaBrowser

    private lateinit var libResult : ListenableFuture<LibraryResult<MediaItem>>

/*
    val browserFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
    browserFuture.addListener({
        // MediaBrowser is available here with browserFuture.get()
    }, MoreExecutors.directExecutor())


    // Get the library root to start browsing the library tree.
    val rootFuture = mediaBrowser.getLibraryRoot(/* params= */ null)
    rootFuture.addListener({
        // Root node MediaItem is available here with rootFuture.get().value
    }, MoreExecutors.directExecutor())*/


    private fun handlePlaybackBasedOnState() {
        /*if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
            playMedia()
        } else */
     //   libResult.addListener(object: )


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
            override fun onVolumeChanged(volume: Float) {
                // Remove this to prevent master volume changes from clobbering equalizer bands
                // _volumenLow.value = _volumenLow.value!!.mapIndexed { i, v -> if (i==currentSlider) volume else v }
                super.onVolumeChanged(volume)
            }

            @OptIn(UnstableApi::class)
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _volumenLow.value = _volumenLow.value!!.mapIndexed { i, v -> if (i==videoSize.width) videoSize.pixelWidthHeightRatio else v }
//                log((controller as Equalizer).getVolOnFreqs().joinToString(", "))
                super.onVideoSizeChanged(videoSize)
            }



/*
            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
            }*/


            override fun onPlaybackStateChanged(playbackState: Int) {

                when (playbackState) {
                    Player.STATE_IDLE -> {
                        log("Player is idle")
                    }

                    Player.STATE_BUFFERING -> {
                        log("Player is buffering")
                    }

                    Player.STATE_ENDED -> {
                        log("The player is finished")
                    }

                    Player.STATE_READY -> {
                        log("Player is ready")
                    }
                }
            }


        }
        )

    }

    @OptIn(UnstableApi::class)
    internal fun initializeMediaController(context: Context) {
        val sessionToken = SessionToken(context, ComponentName(context, MyMediaService::class.java))

        mediaControllerFuture = MediaBrowser
            .Builder(context, sessionToken)
            .buildAsync()

        mediaControllerFuture?.apply {
            addListener({
                controller = get()
                _mediaController.postValue(controller)
                log("MediaController connected")
                
                // Initial browse
                browse("root", context = context)

                // Ensure media is played appropriately based on state
                log("INITIAL STATE = ${controller.playbackState}")
                handlePlaybackBasedOnState()

            }, MoreExecutors.directExecutor())
        }
    }

    @OptIn(UnstableApi::class)
    fun browse(parentId: String, addToStack: Boolean = true, context: Context? = null) {
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
                    
                    // Trigger online data fetching - REMOVED, now handled by service
                }
            } catch (e: Exception) {
                log("Error getting children: ${e.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    fun navigateBack(context: Context? = null): Boolean {
        if (navStack.isEmpty()) return false
        
        val lastPath = navStack.removeAt(navStack.size - 1)
        browse(lastPath, addToStack = false, context = context)
        return true
    }

    internal fun loadMedia(mediaItem: MediaItem) {
        if (!::controller.isInitialized) return
        controller.setMediaItem(mediaItem)
        controller.prepare()
        log("Media loaded and prepared: ${mediaItem.mediaId}")
    }

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


            Player.STATE_BUFFERING -> {
                log("Player is buffering")
            }


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
    }




    internal fun stopMedia() {
        controller.pause()
       // controller.stop()

    }


    override fun onCleared() {
        super.onCleared()
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controller.release()
    }
/*
    private fun displayFolder() {
        val browser = this.controller ?: return
        val id: String = intent.getStringExtra(MEDIA_ITEM_ID_KEY)!!
        val mediaItemFuture = browser.getItem(id)
        val childrenFuture =
            browser.getChildren(id, /* page= */ 0, /* pageSize= */ Int.MAX_VALUE, /* params= */ null)
        mediaItemFuture.addListener(
            {
                val result = mediaItemFuture.get()!!
                val text = result.value!!.mediaMetadata.title
            },
            MoreExecutors.directExecutor()
        )
        childrenFuture.addListener(
            {
                val result = childrenFuture.get()!!
                val subItemMediaList = result.value!!.toList()
            },
            MoreExecutors.directExecutor()
        )
    }*/
}