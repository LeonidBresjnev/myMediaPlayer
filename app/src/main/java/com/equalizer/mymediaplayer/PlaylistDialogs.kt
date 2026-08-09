package com.equalizer.mymediaplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun PlaylistSelectionDialog(
    audioModel: AudioModel,
    mediaItem: MediaItem,
    onDismiss: () -> Unit
) {
    val playlists by audioModel.playlists.observeAsState(emptyList())
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreatingNew) "Create New Playlist" else "Add to Playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isCreatingNew) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    if (playlists.isEmpty()) {
                        Text("No playlists found.", modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            playlists.forEach { playlist ->
                                Text(
                                    text = playlist.mediaMetadata.title?.toString() ?: "Unnamed",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            audioModel.addToPlaylist(mediaItem.mediaId, playlist.mediaId)
                                            onDismiss()
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                    TextButton(
                        onClick = { isCreatingNew = true },
                        modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                    ) {
                        Text("New Playlist")
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingNew) {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            audioModel.createPlaylist(newPlaylistName)
                            isCreatingNew = false
                            newPlaylistName = ""
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isCreatingNew) isCreatingNew = false else onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}
