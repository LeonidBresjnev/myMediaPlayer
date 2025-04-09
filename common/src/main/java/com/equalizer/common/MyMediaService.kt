package com.equalizer.common

import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class MyMediaService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null


    private val setVolOnFreq = SessionCommand("setVolOnFreq" , Bundle.EMPTY)
    private val getVolOnFreq = SessionCommand("getVolOnFreq" , Bundle.EMPTY)

    private fun log(message: String) {
        Log.d("My Media Service", message)
    }

    var currentlocation: String = Environment.getExternalStorageDirectory().absolutePath+"/Music"

    private var volPerFreq = List(8) { 1f }
    // Create your Player and MediaSession in the onCreate lifecycle event
    @OptIn(UnstableApi::class)
    override fun onCreate() {

        super.onCreate()
/*
        val player0 = ExoPlayer
            .Builder(this)
            .setName("ExoPlayer")
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri( Uri.parse("/storage/emulated/0/Music/snothvalp.mp3"))
            .build()
        player0.setMediaItem(mediaItem)
        player0.prepare()
        player0.play()*/
        val player = Equalizer(context=this)
      /*  val audioManager = this.getSystemService(AUDIO_SERVICE) as AudioManager
        // Get available communication devices
        val devices = audioManager.availableCommunicationDevices

        log("devices:\n " + devices.joinToString("\n") { it.id.toString() + "  " + it.productName +" " + it.type})
// Find Android Auto device (example: check for type or name)
        val androidAutoDevice = devices.find {
            it.type == AudioDeviceInfo.TYPE_REMOTE_SUBMIX
        }
        // Set the communication device
        androidAutoDevice?.let { it->
            val success = audioManager.setCommunicationDevice(it)
            if (success) {
                log("Audio routed to Android Auto")
            } else {
                log("Failed to route audio")
            }
        }?: log("No Android Auto device found")
*/
            /*
                    val availableCommands = SessionCommands.Builder()
                        .add(SessionCommand.COMMAND_CODE_CUSTOM)
                        .build()*/

        mediaSession = MediaLibrarySession
            .Builder(this, player,object: MediaLibrarySession.Callback {
/*
                override fun onSetMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long
                ): ListenableFuture<MediaItemsWithStartPosition> {
                    Log.d("My Media Service",
                        "onSetMediaItems: ${mediaItems.map { it.mediaId}}")
                    return super.onSetMediaItems(
                        mediaSession,
                        controller,
                        mediaItems,
                        startIndex,
                        startPositionMs
                    )
                }*/



                override fun onSetMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>,
                    startIndex: Int,
                    startPositionMs: Long
                ): ListenableFuture<MediaItemsWithStartPosition> {
                    return super.onSetMediaItems(
                        mediaSession,
                        controller,
                        mediaItems,
                        startIndex,
                        startPositionMs
                    )
                }


                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    /*val result = super.onConnect(session, controller)
                    result.availablePlayerCommands.buildUpon()*/
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                .add(setVolOnFreq)
                                .add(getVolOnFreq)
                                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
                                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
                                .build()
                        )
                        .build()

                }

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<MediaItem>> {
                   // log("onGetLibraryRoot")
                    return Futures.immediateFuture(
                        LibraryResult.ofItem(MediaItem
                            .Builder()
                            .setUri(Uri.fromFile(
                                Environment.getExternalStorageDirectory()))
                            .setMediaId("root")
                            .setMediaMetadata(MediaMetadata
                                .Builder()
                                .setTitle("Root")
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build())
                            .build(), params))
                }

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    return super.onGetItem(session, browser, mediaId)
                }

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    log("onGetChildren")
                    val currentDir = File(currentlocation)
                    val children : List<MediaItem> = currentDir.listFiles()
                        ?.mapIndexed { idx,it ->
                            val musicMeta = if (it.isFile &&
                                (it.name.endsWith(suffix = "mp3", ignoreCase = true) ||
                                        it.name.endsWith(suffix = "wav", ignoreCase = true)    )) {
                                MusicMeta(it)
                            } else null


                            val isPlayable = it.isFile &&
                                    (it.name.endsWith(suffix = "mp3", ignoreCase = true)
                                            || it.name.endsWith(suffix = "wav", ignoreCase = true))
                            MediaItem
                                .Builder()
                                .setUri(Uri.fromFile(it))
                                .setMediaId(idx.toString())
                                .setMediaMetadata(MediaMetadata
                                    .Builder()
                                    .setArtist(musicMeta?._artist    )

                                    .setTrackNumber(musicMeta?.track)
                                    .setTitle(musicMeta?._name?:it.name)
                                    .setIsBrowsable(it.isDirectory)
                                    .setTotalDiscCount(musicMeta?._sampleRate)
                                    .setReleaseMonth(musicMeta?._numChannels?.toInt())
                                    .setIsPlayable(isPlayable)
                                    .build())
                                .build()
                        } ?: emptyList()

                    return Futures.immediateFuture(LibraryResult.ofItemList(children, params) )

                }
/*
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    Log.d("My Media Service","onAddMediaItems: ${mediaItems.map { it.mediaId}}")
                    return super.onAddMediaItems(mediaSession, controller, mediaItems)
                }*/

                override fun onPlaybackResumption(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaItemsWithStartPosition> {
                    val settable = SettableFuture.create<MediaItemsWithStartPosition>()
                    CoroutineScope(Dispatchers.Main).launch {
                        // Your app is responsible for storing the playlist and the start position
                        // to use here
                        val mylist = MediaItemsWithStartPosition(player.mediaItems,0,0)
                        Log.d("My Media Service",
                            "onPlaybackResumption: ${player.mediaItems.map { it.mediaId}}")
                        settable.set(mylist)
                    }
                    return settable
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    command: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {

                    if (command.customAction == "setVolOnFreq") {
                        val index = args.getInt("KEY_INDEX")
                        val volume = args.getFloat("KEY_VOLUME")
                        player.setVolOnFreq(volume,index)
                        //Log.i("Media Service","setVolOnFreq $index $volume")
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        mediaSession?.sessionExtras=Bundle().apply {
            putInt("Interval", 123)
        }


/*
        player.addListener(object : Player.Listener {

            override fun onVolumeChanged(volume: Float) {
                volPerFreq=player.getVolOnFreqs()
                Log.d("My Media Service",volPerFreq.joinToString(", "))
                super.onVolumeChanged(volume)

            }
        })*/


    }


    // Remember to release the player and media session in onDestroy
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }

        super.onDestroy()
    }

    // The user dismissed the app from the recent tasks
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player!!
        if (!player.playWhenReady
            || player.mediaItemCount == 0
            || player.playbackState == Player.STATE_ENDED) {
            // Stop the service if not playing, continue playing in the background
            // otherwise.
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {

        return mediaSession
    }




}