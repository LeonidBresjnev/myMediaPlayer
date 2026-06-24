package com.equalizer.common

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.equalizer.common.metadata.MetaFactory
import com.equalizer.common.metadata.M4aMeta
import com.equalizer.common.metadata.Mp3Meta
import com.equalizer.common.metadata.WavMeta
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionError
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.net.toUri


class MyMediaService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)


    private val setVolOnFreq = SessionCommand("setVolOnFreq" , Bundle.EMPTY)
    private val getVolOnFreq = SessionCommand("getVolOnFreq" , Bundle.EMPTY)

    var currentlocation: String = Environment.getExternalStorageDirectory().absolutePath+"/Music"

    private var volPerFreq = List(8) { 1f }

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

    // Create your Player and MediaSession in the onCreate lifecycle event
    @OptIn(UnstableApi::class)
    override fun onCreate() {

        super.onCreate()

        val player = Equalizer(context=this)

        mediaSession = MediaLibrarySession
            .Builder(this, player,object: MediaLibrarySession.Callback {

                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val connectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                .add(setVolOnFreq)
                                .add(getVolOnFreq)
                                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)
                                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)
                                .add(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)
                                .build()
                        )
                        .build()
                    return connectionResult
                }

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    val rootItem = MediaItem.Builder()
                        .setMediaId("root")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                .setTitle("Music Library")
                                .setExtras(Bundle().apply {
                                    // Hint to Android Auto to start here instead of Now Playing
                                    putBoolean("androidx.media.utils.MEDIA_BROWSER_SERVICE_HINT_BASE_BROWSE_ROOT", true)
                                })
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
                    params: LibraryParams?,
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    val settable = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        val musicDir = File(Environment.getExternalStorageDirectory(), "Music")
                        val parentDir = if (parentId == "root") {
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
                    return settable
                }

                override fun onGetItem(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    mediaId: String
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    val settable = SettableFuture.create<LibraryResult<MediaItem>>()
                    serviceScope.launch {
                        try {
                            val file = File(mediaId)
                            val item = createMediaItemFromFile(file)
                            if (item != null) {
                                settable.set(LibraryResult.ofItem(item, null))
                            } else {
                                settable.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                            }
                        } catch (e: Exception) {
                            settable.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                            e.message?.let {
                                Log.d("Error in Mediaservice", it)
                            }
                        }
                    }
                    return settable
                }

                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>
                ): ListenableFuture<List<MediaItem>> {
                    Log.d("My Media Service", "onAddMediaItems: ${mediaItems.map { it.mediaId }}")
                    val updatedItems = mediaItems.map { item ->
                        if (item.localConfiguration == null) {
                            // If it's a library item without local config, we need to rebuild it with the URI
                            MediaItem.Builder()
                                .setMediaId(item.mediaId)
                                .setUri(Uri.fromFile(File(item.mediaId)))
                                .setMediaMetadata(item.mediaMetadata)
                                .build()
                        } else {
                            item
                        }
                    }
                    return Futures.immediateFuture(updatedItems)
                }

                override fun onPlaybackResumption(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaItemsWithStartPosition> {
                    val settable = SettableFuture.create<MediaItemsWithStartPosition>()
                    CoroutineScope(Dispatchers.Main).launch {
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
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        mediaSession?.sessionExtras=Bundle().apply {
            putInt("Interval", 123)
        }
    }


    override fun onDestroy() {
        serviceJob.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady
            || player.mediaItemCount == 0
            || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }
}
