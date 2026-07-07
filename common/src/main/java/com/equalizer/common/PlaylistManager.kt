package com.equalizer.common

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String> = emptyList()
)

object PlaylistManager {
    private const val FILE_NAME = "playlists.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun getPlaylists(context: Context): List<Playlist> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString<List<Playlist>>(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savePlaylists(context: Context, playlists: List<Playlist>) {
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(json.encodeToString(playlists))
    }

    fun createPlaylist(context: Context, name: String): String {
        val playlists = getPlaylists(context).toMutableList()
        val id = "playlist_${System.currentTimeMillis()}"
        playlists.add(Playlist(id, name))
        savePlaylists(context, playlists)
        return id
    }

    fun deletePlaylist(context: Context, playlistId: String) {
        val playlists = getPlaylists(context).filter { it.id != playlistId }
        savePlaylists(context, playlists)
    }

    fun addSongToPlaylist(context: Context, playlistId: String, songId: String) {
        val playlists = getPlaylists(context).map {
            if (it.id == playlistId) {
                if (!it.songIds.contains(songId)) {
                    it.copy(songIds = it.songIds + songId)
                } else it
            } else it
        }
        savePlaylists(context, playlists)
    }

    fun removeSongFromPlaylist(context: Context, playlistId: String, songId: String) {
        val playlists = getPlaylists(context).map {
            if (it.id == playlistId) {
                it.copy(songIds = it.songIds - songId)
            } else it
        }
        savePlaylists(context, playlists)
    }
}
