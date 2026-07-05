package com.equalizer.common

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlaylistManagerTest {

    private lateinit var mockContext: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        mockContext = mockk()
        tempDir = Files.createTempDirectory("playlist_test").toFile()
        every { mockContext.filesDir } returns tempDir
    }

    @Test
    fun testCreateAndGetPlaylists() {
        val name = "Test Playlist"
        val id = PlaylistManager.createPlaylist(mockContext, name)
        
        val playlists = PlaylistManager.getPlaylists(mockContext)
        assertEquals(1, playlists.size)
        assertEquals(name, playlists[0].name)
        assertEquals(id, playlists[0].id)
    }

    @Test
    fun testAddSongToPlaylist() {
        val playlistId = PlaylistManager.createPlaylist(mockContext, "My Songs")
        val songId = "/path/to/song.mp3"
        
        PlaylistManager.addSongToPlaylist(mockContext, playlistId, songId)
        
        val playlists = PlaylistManager.getPlaylists(mockContext)
        assertEquals(1, playlists[0].songIds.size)
        assertEquals(songId, playlists[0].songIds[0])
    }

    @Test
    fun testDeletePlaylist() {
        val id1 = PlaylistManager.createPlaylist(mockContext, "P1")
        val id2 = PlaylistManager.createPlaylist(mockContext, "P2")
        
        PlaylistManager.deletePlaylist(mockContext, id1)
        
        val playlists = PlaylistManager.getPlaylists(mockContext)
        assertEquals(1, playlists.size)
        assertEquals(id2, playlists[0].id)
    }
}
