package com.equalizer.common

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.equalizer.common.metadata.M4aMeta
import com.equalizer.common.metadata.MetaFactory
import com.equalizer.common.metadata.Mp3Meta
import com.equalizer.common.metadata.WavMeta
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class MyMediaService : MediaLibraryService() {
    var mediaSession: MediaLibrarySession? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val setVolOnFreq = SessionCommand("setVolOnFreq", Bundle())
    private val setAllVolOnFreq = SessionCommand("setAllVolOnFreq", Bundle())
    private val createPlaylistCmd = SessionCommand("createPlaylist", Bundle())
    private val addToPlaylistCmd = SessionCommand("addToPlaylist", Bundle())
    private val deletePlaylistCmd = SessionCommand("deletePlaylist", Bundle())

    private val volPerFreq = MutableList(8) { 1.0f }

    private suspend fun createMediaItemFromFile(file: File): MediaItem? {
        if (!file.exists()) return null

        return withContext(Dispatchers.IO) {
            if (file.isDirectory) {
                var firstArtist: String? = null
                var firstTitle: String? = null

                // Try to find a thumbnail and metadata for the folder from its contents
                val firstWithArt = file.walk()
                    .filter {
                        it.isFile && (it.name.endsWith(".mp3", true) || it.name.endsWith(".m4a", true))
                    }
                    .onEach { songFile ->
                        if (firstArtist == null) {
                            when (val meta = MetaFactory.createMeta(songFile, applicationContext)) {
                                is Mp3Meta -> {
                                    firstArtist = meta.artist
                                    firstTitle = meta.name
                                }
                                is M4aMeta -> {
                                    firstArtist = meta.artist
                                    firstTitle = meta.name
                                }
                            }
                        }
                    }
                    .firstOrNull {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(it.absolutePath)
                            retriever.embeddedPicture != null
                        } catch (_: Exception) {
                            false
                        } finally {
                            retriever.release()
                        }
                    }

                val folderMetadataBuilder = MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setTitle(file.name)
                    .setArtist(firstArtist)

                if (firstWithArt != null) {
                    val artworkUri = MediaThumbnailProvider.CONTENT_URI.buildUpon()
                        .appendQueryParameter("path", firstWithArt.absolutePath)
                        .build()
                    folderMetadataBuilder.setArtworkUri(artworkUri)
                } else if (firstArtist != null && firstTitle != null) {
                    // Try online artwork if local is not found
                    val onlineInfo = OnlineMetadataManager.getOnlineInfo(applicationContext,
                        firstArtist, firstTitle
                    )
                    onlineInfo?.artworkUrl?.let {
                        folderMetadataBuilder.setArtworkUri(it.toUri())
                    }
                }

                MediaItem.Builder()
                    .setMediaId(file.absolutePath)
                    .setMediaMetadata(folderMetadataBuilder.build())
                    .build()
            } else if (file.isFile && (file.name.endsWith(".mp3", true) ||
                        file.name.endsWith(".wav", true) ||
                        file.name.endsWith(".m4a", true))
            ) {

                val meta = MetaFactory.createMeta(file, applicationContext)
                val metadataBuilder = MediaMetadata.Builder()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)

                // Set thumbnail for the file if it has embedded artwork
                val hasLocalArt = when (meta) {
                    is Mp3Meta -> meta.imageArray?.isNotEmpty() == true
                    is M4aMeta -> meta.imageArray?.isNotEmpty() == true
                    else -> false
                }

                if (hasLocalArt) {
                    val artworkUri = MediaThumbnailProvider.CONTENT_URI.buildUpon()
                        .appendQueryParameter("path", file.absolutePath)
                        .build()
                    metadataBuilder.setArtworkUri(artworkUri)
                }

                if (meta != null) {
                    val artist = when (meta) {
                        is Mp3Meta -> meta.artist
                        is M4aMeta -> meta.artist
                        else -> null
                    }
                    val title = when (meta) {
                        is Mp3Meta -> meta.name
                        is M4aMeta -> meta.name
                        else -> file.name
                    }

                    if (!hasLocalArt && !artist.isNullOrBlank() && !title.isNullOrBlank()) {
                        val onlineInfo = OnlineMetadataManager.getOnlineInfo(applicationContext, artist, title)
                        onlineInfo?.artworkUrl?.let {
                            metadataBuilder.setArtworkUri(it.toUri())
                        }
                    }

                    when (meta) {
                        is Mp3Meta -> {
                            metadataBuilder.setTitle(meta.name)
                                .setArtist(meta.artist)
                                .setTrackNumber(meta.track)
                                .setTotalDiscCount(meta.sampleRate)
                                .setReleaseMonth(meta.numChannels)
                        }

                        is M4aMeta -> {
                            metadataBuilder.setTitle(meta.name)
                                .setArtist(meta.artist)
                                .setTrackNumber(meta.track)
                                .setTotalDiscCount(meta.sampleRate)
                                .setReleaseMonth(meta.numChannels)
                        }

                        is WavMeta -> {
                            metadataBuilder.setTitle(file.name)
                                .setTotalDiscCount(meta.sampleRate)
                                .setReleaseMonth(meta.numChannels)
                        }
                    }
                } else {
                    metadataBuilder.setTitle(file.name)
                }

                MediaItem.Builder()
                    .setMediaId(file.absolutePath)
                    .setMediaMetadata(metadataBuilder.build())
                    .setUri(Uri.fromFile(file))
                    .build()
            } else null
        }
    }


    override fun onCreate() {
        super.onCreate()
        Log.d("MyMediaService", "onCreate starting")
        val player = Equalizer(context = this)

        mediaSession = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {




            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                Log.d("MyMediaService", "onConnect from: ${controller.packageName}")
                val connectionResult = super.onConnect(session, controller)
                val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                availableSessionCommands.add(setVolOnFreq)
                availableSessionCommands.add(setAllVolOnFreq)
                availableSessionCommands.add(createPlaylistCmd)
                availableSessionCommands.add(addToPlaylistCmd)
                availableSessionCommands.add(deletePlaylistCmd)
                /*
                val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                    .add(COMMAND_PLAY_PAUSE)
                    .add(COMMAND_STOP)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .addAllCommands()
                    .build()*/

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(availableSessionCommands.build())
                    .setAvailablePlayerCommands(connectionResult
                        .availablePlayerCommands.buildUpon()
                        .addAllCommands() // This "unlocks" the skip buttons for the phone UI
                        .build())
                    .build()
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootItem = MediaItem.Builder()
                    .setMediaId("root")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .setTitle("Media Library")
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val settable = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                serviceScope.launch {
                    when {
                        parentId == "root" -> {
                            val items = listOf(
                                MediaItem.Builder()
                                    .setMediaId("music_library_root")
                                    .setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle("Music Library")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .build())
                                    .build(),
                                MediaItem.Builder()
                                    .setMediaId("playlists_root")
                                    .setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle("Playlists")
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .build())
                                    .build()
                            )
                            settable.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        }
                        parentId == "playlists_root" -> {
                            val playlists = PlaylistManager.getPlaylists(applicationContext)
                            val items = playlists.map { playlist ->
                                MediaItem.Builder()
                                    .setMediaId(playlist.id)
                                    .setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle(playlist.name)
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .build())
                                    .build()
                            }
                            settable.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                        }
                        parentId.startsWith("playlist_") -> {
                            val playlists = PlaylistManager.getPlaylists(applicationContext)
                            val playlist = playlists.find { it.id == parentId }
                            if (playlist != null) {
                                val mediaItems = playlist.songIds.map { songId ->
                                    async { createMediaItemFromFile(File(songId)) }
                                }.awaitAll().filterNotNull()
                                settable.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
                            } else {
                                settable.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                            }
                        }
                        else -> {
                            val musicDir = File(Environment.getExternalStorageDirectory(), "Music")
                            val parentDir = if (parentId == "music_library_root") {
                                musicDir
                            } else {
                                File(parentId)
                            }

                            if (!parentDir.exists() || !parentDir.isDirectory) {
                                settable.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                                return@launch
                            }

                            val filesList = parentDir.listFiles()?.sortedBy { it.name } ?: emptyList()

                            val mediaItems = withContext(Dispatchers.IO) {
                                filesList.map { file ->
                                    async {
                                        createMediaItemFromFile(file)
                                    }
                                }.awaitAll().filterNotNull()
                            }

                            settable.set(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
                        }
                    }
                }
                return settable
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val settable = SettableFuture.create<LibraryResult<MediaItem>>()
                serviceScope.launch {
                    val item = createMediaItemFromFile(File(mediaId))
                    if (item != null) {
                        settable.set(LibraryResult.ofItem(item, null))
                    } else {
                        settable.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    }
                }
                return settable
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>
            ): ListenableFuture<List<MediaItem>> {
                return Futures.immediateFuture(mediaItems)
            }
/*
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo, isForPlayback: Boolean
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                // This would normally restore previous queue
                return super.onPlaybackResumption(mediaSession, controller, isForPlayback)
            }*/

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == "setVolOnFreq") {
                    val index = args.getInt("KEY_INDEX")
                    val volume = args.getFloat("KEY_VOLUME")
                    volPerFreq[index] = volume
                    
                    val player = session.player
                    if (player is Equalizer) {
                        player.setVolOnFreq(volume, index)
                    }
                    pushEqualizerState(session)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } else if (customCommand.customAction == "setAllVolOnFreq") {
                    val volumes = args.getFloatArray("KEY_VOLUMES")
                    if (volumes != null && volumes.size == 8) {
                        for (i in 0 until 8) volPerFreq[i] = volumes[i]
                        val player = session.player
                        if (player is Equalizer) {
                            player.setAllVolOnFreq(volumes)
                        }
                        pushEqualizerState(session)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                } else if (customCommand.customAction == "createPlaylist") {
                    val name = args.getString("NAME") ?: "New Playlist"
                    PlaylistManager.createPlaylist(applicationContext, name)
                    mediaSession?.notifyChildrenChanged("playlists_root", 0, null)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } else if (customCommand.customAction == "addToPlaylist") {
                    val playlistId = args.getString("PLAYLIST_ID")
                    val songId = args.getString("SONG_ID")
                    if (playlistId != null && songId != null) {
                        PlaylistManager.addSongToPlaylist(applicationContext, playlistId, songId)
                        mediaSession?.notifyChildrenChanged(playlistId, 0, null)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                } else if (customCommand.customAction == "deletePlaylist") {
                    val playlistId = args.getString("PLAYLIST_ID")
                    if (playlistId != null) {
                        PlaylistManager.deletePlaylist(applicationContext, playlistId)
                        mediaSession?.notifyChildrenChanged("playlists_root", 0, null)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }

        }).build()
    }

    private fun pushEqualizerState(session: MediaSession) {
        val extras = Bundle().apply {
            putFloatArray("EQ_STATE", volPerFreq.toFloatArray())
        }
        session.sessionExtras = extras
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(intent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }
}
