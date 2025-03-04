package com.equalizer.common

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MyMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    private val setVolOnFreq = SessionCommand("setVolOnFreq" , Bundle.EMPTY)
    private val getVolOnFreq = SessionCommand("getVolOnFreq" , Bundle.EMPTY)


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
/*
        val availableCommands = SessionCommands.Builder()
            .add(SessionCommand.COMMAND_CODE_CUSTOM)
            .build()*/

        mediaSession = MediaSession
            .Builder(this, player)
            .setCallback(object: MediaSession.Callback {
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
                                .build()
                        )
                        .build()

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
                        Log.i("Media Service","setVolOnFreq $index $volume")
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession




}