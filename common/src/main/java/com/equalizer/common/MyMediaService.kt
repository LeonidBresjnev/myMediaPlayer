package com.equalizer.common

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.DefaultMediaNotificationProvider
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
import androidx.core.net.toUri

@UnstableApi
class MyMediaService : MediaLibraryService() {
    companion object {
        private var instance: MyMediaService? = null
        
        fun getSession(): MediaLibrarySession? {
            return instance?.mediaSession
        }
    }

    var mediaSession: MediaLibrarySession? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val setVolOnFreq = SessionCommand("setVolOnFreq", Bundle())
    private val setAllVolOnFreq = SessionCommand("setAllVolOnFreq", Bundle())
    private val setDelayCmd = SessionCommand("setDelay", Bundle())
    private val setEqModeCmd = SessionCommand("setEqMode", Bundle())
    private val toggleFavouriteCmd = SessionCommand("toggleFavourite", Bundle())
    private val createPlaylistCmd = SessionCommand("createPlaylist", Bundle())
    private val addToPlaylistCmd = SessionCommand("addToPlaylist", Bundle())
    private val deletePlaylistCmd = SessionCommand("deletePlaylist", Bundle())
    private val getFilterDesignCmd = SessionCommand("getFilterDesign", Bundle())
    private val getAnalysisCmd = SessionCommand("getAnalysis", Bundle())
    private val getUnoptimizedAnalysisCmd = SessionCommand("getUnoptimizedAnalysis", Bundle())

    private val volPerFreq = MutableList(16) { 1.0f }
    private var isAdvancedMode = false

    private fun getMusicLibraryRoot(): File {
        val standardRoot = Environment.getExternalStorageDirectory()
        Environment.getExternalStorageDirectory().path
        val musicPaths = listOf(
            File(standardRoot, "Music"),
            File(Environment.getExternalStorageDirectory().path + "/Music"),
            File("/storage/emulated/0/Music"),  // Explicit User 0
            File("/storage/emulated/10/Music"), // Explicit User 10
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory().path + "/Download"),
            standardRoot // Last resort: root of SD card
        )

        Log.d("MyMediaService", "--- STARTING OMNI-SEARCH ---")
        musicPaths.forEach { dir ->
            try {
                val exists = dir.exists()
                val canRead = dir.canRead()
                val list = if (exists && canRead) dir.listFiles() else null
                val totalCount = list?.size ?: 0
                val playableCount = list?.count { 
                    it.isFile && (it.name.endsWith(".mp3", true) || 
                                 it.name.endsWith(".wav", true) || 
                                 it.name.endsWith(".m4a", true)) 
                } ?: 0
                
                Log.d("MyMediaService", "SCAN: path=${dir.absolutePath} exists=$exists canRead=$canRead total=$totalCount playable=$playableCount")
                if (playableCount > 0) {
                    val sample = list?.firstOrNull { it.isFile }?.name
                    Log.d("MyMediaService", "SCAN: FOUND MUSIC in ${dir.absolutePath}! (Sample: $sample)")
                }
            } catch (e: Exception) {
                Log.w("MyMediaService", "SCAN: Error checking ${dir.absolutePath}: ${e.message}")
            }
        }

        // Return the first path that actually has music files
        val bestPath = musicPaths.firstOrNull { 
            it.exists() && it.canRead() && (it.listFiles()?.any { f -> 
                f.isFile && (f.name.endsWith(".mp3", true) || f.name.endsWith(".wav", true) || f.name.endsWith(".m4a", true)) 
            } == true) 
        } ?: File(standardRoot, "Music")

        Log.d("MyMediaService", "SELECTED ROOT: ${bestPath.absolutePath}")
        return bestPath
    }

    private suspend fun createMediaItemFromFile(file: File): MediaItem? {
        if (!file.exists()) return null

        return withContext(Dispatchers.IO) {
            if (file.isDirectory) {
                // Simplified folder creation: Avoid scanning contents upfront for performance.
                // The MediaThumbnailProvider will handle finding the artwork asynchronously.
                val folderMetadataBuilder = MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setTitle(file.name)
                    .setArtworkUri(MediaThumbnailProvider.getArtworkUri(applicationContext, file.absolutePath))

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
                    val artworkUri = MediaThumbnailProvider.getArtworkUri(applicationContext, file.absolutePath)
                    metadataBuilder.setArtworkUri(artworkUri)
                }

                if (meta != null) {
                 /*   val artist = when (meta) {
                        is Mp3Meta -> meta.artist
                        is M4aMeta -> meta.artist
                        else -> null
                    }
                    val title = when (meta) {
                        is Mp3Meta -> meta.name
                        is M4aMeta -> meta.name
                        else -> file.name
                    }*/

                    // Always set the artwork URI so the thumbnail provider can decide whether to look locally or online
                    metadataBuilder.setArtworkUri(MediaThumbnailProvider.getArtworkUri(applicationContext, file.absolutePath))

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
        instance = this
        Log.d("MyMediaService", "onCreate starting")
        MediaThumbnailProvider.init(this)
        val player = Equalizer(context = this)

        val notificationProvider = DefaultMediaNotificationProvider(this)
        notificationProvider.setSmallIcon(R.drawable.ic_lever)
        setMediaNotificationProvider(notificationProvider)

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent!!, PendingIntent.FLAG_IMMUTABLE)

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
                availableSessionCommands.add(setDelayCmd)
                availableSessionCommands.add(setEqModeCmd)
                availableSessionCommands.add(toggleFavouriteCmd)
                availableSessionCommands.add(createPlaylistCmd)
                availableSessionCommands.add(addToPlaylistCmd)
                availableSessionCommands.add(deletePlaylistCmd)
                availableSessionCommands.add(getFilterDesignCmd)
                availableSessionCommands.add(getAnalysisCmd)
                availableSessionCommands.add(getUnoptimizedAnalysisCmd)

                pushEqualizerState(session)

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
                                    .setMediaId("icecast_root")
                                    .setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle("IceCast Radio")
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
                        parentId == "icecast_root" -> {
                            serviceScope.launch {
                                val stations = IceCastManager.fetchStations()
                                val items = stations.filter { it.url.isNotBlank() }.map { station ->
                                    MediaItem.Builder()
                                        .setMediaId(station.url)
                                        .setUri(station.url.toUri())
                                        .setMediaMetadata(MediaMetadata.Builder()
                                            .setTitle(station.name)
                                            .setSubtitle(station.genre)
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                            .setTotalDiscCount(station.samplerate)
                                            .setReleaseMonth(station.channels)
                                            .setExtras(Bundle().apply {
                                                putInt("BITRATE", station.bitrate)
                                                putString("CODEC", "MP3")
                                                putBoolean("IS_RADIO", true)
                                            })
                                            .build())
                                        .build()
                                }
                                settable.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                            }
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
                            val parentDir = if (parentId == "music_library_root") {
                                getMusicLibraryRoot()
                            } else {
                                File(parentId)
                            }
                            
                            Log.d("MyMediaService", "onGetChildren: parentId=$parentId, parentDir=${parentDir.absolutePath}")
                            Log.d("MyMediaService", "onGetChildren: exists=${parentDir.exists()}, isDir=${parentDir.isDirectory}, canRead=${parentDir.canRead()}")

                            if (!parentDir.exists() || !parentDir.isDirectory) {
                                Log.e("MyMediaService", "onGetChildren: Parent directory does not exist or is not a directory: ${parentDir.absolutePath}")
                                settable.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                                return@launch
                            }

                            val filesList = parentDir.listFiles()?.filter { !it.name.startsWith(".") }?.sortedBy { it.name } ?: emptyList()
                            Log.d("MyMediaService", "onGetChildren: Found ${filesList.size} files in ${parentDir.absolutePath}")

                            val mediaItems = withContext(Dispatchers.IO) {
                                filesList.map { file ->
                                    async {
                                        val item = createMediaItemFromFile(file)
                                        if (item == null) {
                                            Log.w("MyMediaService", "onGetChildren: createMediaItemFromFile failed for ${file.absolutePath} (isFile=${file.isFile}, ext=${file.extension})")
                                        }
                                        item
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
                val settable = SettableFuture.create<List<MediaItem>>()
                serviceScope.launch {
                    val resolvedItems = mediaItems.map { item ->
                        // If the item already has a URI, we trust it. 
                        // Otherwise, we try to resolve it from our local file system using the mediaId as path.
                        if (item.localConfiguration?.uri != null) {
                            item
                        } else {
                            createMediaItemFromFile(File(item.mediaId)) ?: item
                        }
                    }
                    settable.set(resolvedItems)
                }
                return settable
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
                    
                    val player = session.player
                    if (player is Equalizer) {
                        volPerFreq[index] = volume
                        player.setVolOnFreq(volume, index)
                        
                        // Sync channels if in Basic mode
                        if (!isAdvancedMode) {
                            val otherIndex = if (index < 8) index + 8 else index - 8
                            volPerFreq[otherIndex] = volume
                            player.setVolOnFreq(volume, otherIndex)
                        }
                    }
                    pushEqualizerState(session)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } else if (customCommand.customAction == "setAllVolOnFreq") {
                    val volumes = args.getFloatArray("KEY_VOLUMES")
                    if (volumes != null) {
                        val player = session.player
                        if (player is Equalizer) {
                            if (volumes.size == 8) {
                                // Apply same 8 bands to both L and R
                                for (i in 0 until 8) {
                                    volPerFreq[i] = volumes[i]
                                    volPerFreq[i + 8] = volumes[i]
                                }
                                player.setAllVolOnFreq(volPerFreq.toFloatArray())
                            } else if (volumes.size == 16) {
                                for (i in 0 until 16) volPerFreq[i] = volumes[i]
                                player.setAllVolOnFreq(volumes)
                            }
                        }
                        pushEqualizerState(session)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                } else if (customCommand.customAction == "setDelay") {
                    val left = args.getFloat("KEY_LEFT_DELAY")
                    val right = args.getFloat("KEY_RIGHT_DELAY")
                    val player = session.player
                    if (player is Equalizer) {
                        player.setDelay(left, right)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } else if (customCommand.customAction == "setEqMode") {
                    isAdvancedMode = args.getBoolean("IS_ADVANCED")
                    
                    // If switching to Basic mode, sync Right to Left immediately
                    if (!isAdvancedMode) {
                        for (i in 0 until 8) {
                            volPerFreq[i + 8] = volPerFreq[i]
                        }
                        val player = session.player
                        if (player is Equalizer) {
                            player.setAllVolOnFreq(volPerFreq.toFloatArray())
                        }
                    }
                    
                    pushEqualizerState(session)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                } else if (customCommand.customAction == "toggleFavourite") {
                    val songId = args.getString("SONG_ID")
                    if (songId != null) {
                        PlaylistManager.toggleFavourite(applicationContext, songId)
                        mediaSession?.notifyChildrenChanged(PlaylistManager.FAVOURITES_ID, 0, null)
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
                } else if (customCommand.customAction == "getFilterDesign") {
                    val player = session.player
                    val settable = SettableFuture.create<SessionResult>()
                    if (player is Equalizer) {
                        serviceScope.launch(Dispatchers.Default) {
                            val raw = player.getRawFilterDesign()
                            if (raw != null) {
                                val extras = Bundle().apply {
                                    putFloatArray("DESIGN_DATA", raw)
                                }
                                settable.set(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                            } else {
                                settable.set(SessionResult(SessionError.ERROR_BAD_VALUE))
                            }
                        }
                        return settable
                    }
                } else if (customCommand.customAction == "getAnalysis") {
                    val player = session.player
                    val settable = SettableFuture.create<SessionResult>()
                    if (player is Equalizer) {
                        serviceScope.launch(Dispatchers.Default) {
                            val raw = player.getAnalysisResponse(0.0, 4000.0, 10.0)
                            if (raw != null) {
                                val extras = Bundle().apply {
                                    putFloatArray("ANALYSIS_DATA", raw)
                                }
                                settable.set(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                            } else {
                                settable.set(SessionResult(SessionError.ERROR_BAD_VALUE))
                            }
                        }
                        return settable
                    }
                } else if (customCommand.customAction == "getUnoptimizedAnalysis") {
                    val player = session.player
                    val settable = SettableFuture.create<SessionResult>()
                    if (player is Equalizer) {
                        serviceScope.launch(Dispatchers.Default) {
                            val raw = player.getUnoptimizedAnalysisResponse(0.0, 4000.0, 10.0)
                            if (raw != null) {
                                val extras = Bundle().apply {
                                    putFloatArray("ANALYSIS_DATA", raw)
                                }
                                settable.set(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                            } else {
                                settable.set(SessionResult(SessionError.ERROR_BAD_VALUE))
                            }
                        }
                        return settable
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }

        }).setId("MyMediaPlayerSession-$packageName").setSessionActivity(pendingIntent).build()
    }

    private fun pushEqualizerState(session: MediaSession) {
        val favourites = PlaylistManager.getPlaylists(applicationContext)
            .find { it.id == PlaylistManager.FAVOURITES_ID }?.songIds ?: emptyList()

        val extras = Bundle().apply {
            putFloatArray("EQ_STATE", volPerFreq.toFloatArray())
            putBoolean("IS_ADVANCED", isAdvancedMode)
            putStringArray("FAVOURITES", favourites.toTypedArray())
            
            // We'll skip pushing FILTER_DESIGN here to avoid blocking the main thread.
            // The AudioModel proactively requests it via custom command anyway.
        }
        session.sessionExtras = extras
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        instance = null
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
