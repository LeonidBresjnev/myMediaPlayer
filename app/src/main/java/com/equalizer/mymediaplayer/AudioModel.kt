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
import com.google.common.util.concurrent.MoreExecutors

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

    val volumeRange = 0f..2f

    private var currentSlider=-1

    fun setVolumen(volumeInDb: Float, index: Int) {
        currentSlider=index
        val extras = Bundle().apply {
            putInt("KEY_INDEX", index)
            putFloat("KEY_VOLUME", volumeInDb)
        }
        val customCommand = SessionCommand("setVolOnFreq", Bundle())

        controller.sendCustomCommand(customCommand, extras)
        /*_volumenLow.value = _volumenLow.value!!.mapIndexed { i, v -> if (i==index) volumeInDb else v }
        viewModelScope.launch {
            equalizer?.setVolumenLow(volumeInDb, index)
        }*/
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
                _volumenLow.value = _volumenLow.value!!.mapIndexed { i, v -> if (i==currentSlider) volume else v }
//                log((controller as Equalizer).getVolOnFreqs().joinToString(", "))
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
                log("before get root")
                libResult = controller.getLibraryRoot(/* params= */ null)


                val childrenFuture = controller.getChildren(
                    "root", 0, Int.MAX_VALUE, null)
                val childrenResult = childrenFuture.get()
                log("number of children ${childrenResult.value?.size?:"null"}")
                log(childrenResult.value?.joinToString("\n") { it.mediaMetadata.title  ?:"-"}?:"null")
                _subItemMediaList.value = childrenResult.value?:emptyList()
                libResult.addListener( {

                    log("before call get")
                    val result=libResult.get()


                   // result.sessionError?.let { log(it.message) }
                    if (result == null) log("result is null")
                    if (result.value == null) log("result-value is null")
                      println("result: ${result.value?.mediaId?:"null"}")
                    //controller.getChildren()
                    // Root node MediaItem is available here with rootFuture.get().value
                }, MoreExecutors.directExecutor())
                //updateUIWithMediaController(controller)

                // Ensure media is played appropriately based on state
                log("INITIAL STATE = ${controller.playbackState}")
                handlePlaybackBasedOnState()

            }, MoreExecutors.directExecutor()

            )
        }
// Get the library root to start browsing the library tree.


/*
        val browserFuture = MediaBrowser
            .Builder(context, sessionToken).buildAsync()
        browserFuture.addListener({
            // MediaBrowser is available here with browserFuture.get()
            mediabrowser = browserFuture.get()
        }, MoreExecutors.directExecutor())
        val rootMediaItem = mediabrowser?.currentMediaItem

        // Get the library root to start browsing the library tree.
        val childrenFuture =
            rootMediaItem?.let { mediabrowser?.getChildren(it.mediaId, 0, Int.MAX_VALUE, null) }
        childrenFuture?.addListener({
            // List of children MediaItem nodes is available here with
            // childrenFuture.get().value
        }, MoreExecutors.directExecutor())

        val myRoot = mediabrowser?.getLibraryRoot(
           null
        )?.get()


        log("rootMediaItem is ${myRoot?.value.toString()}")*/
    }

    internal fun playMedia(mediaItem: MediaItem) {

        log("playbackState is ${controller.playbackState}, playwhenready=${controller.playWhenReady}")

        when (controller.playbackState) {
            Player.STATE_IDLE -> {
                controller.addMediaItem(mediaItem)
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
                controller.addMediaItem(mediaItem)
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