package com.equalizer.mymediaplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage

@UnstableApi
@Composable
fun MediaBrowserScreen(modifier: Modifier = Modifier,
                       audioModel: AudioModel,
                       onSelect: (List<MediaItem>, Int) -> Unit = { _, _ -> }
) {
    //val context = LocalContext.current
    val media = audioModel.subItemMediaList.observeAsState(emptyList())
    val currentPath by audioModel.currentPath.observeAsState("root")
    val currentPlaybackContext by audioModel.currentPlaybackContext.observeAsState()
    val nowPlayingId by audioModel.nowPlayingId.observeAsState()
    val favourites by audioModel.favourites.observeAsState(emptySet())

    val gridState = rememberLazyGridState()

    LaunchedEffect(currentPath) {
        gridState.scrollToItem(0)
    }

    var infoItem by remember {
        mutableStateOf<MediaItem?>(null)
    }

    var showPlaylistDialog by remember { mutableStateOf<MediaItem?>(null) }

    val folders = media.value.filter { it.mediaMetadata.isBrowsable == true }
    val files = media.value.filter { it.mediaMetadata.isBrowsable != true }

    Column(modifier = modifier
        .fillMaxSize()
        .padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)) {
            if (currentPath != "music_library_root" && currentPath != "root") {
                Text(
                    text = "< Back",
                    modifier = Modifier
                        .clickable {
                            audioModel.navigateBack(/*context = context*/)
                        }
                        .padding(end = 16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(text = "Path: $currentPath",
                 style = MaterialTheme.typography.titleMedium,
                 maxLines = 1,
                 overflow = TextOverflow.Ellipsis,
                 modifier = Modifier.weight(1f))
        }

        if (currentPlaybackContext == currentPath && nowPlayingId != null) {
            Text(
                text = "Playing from this folder",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // FILES SECTION (List at top)
            if (files.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Files", style = MaterialTheme.typography.labelLarge)
                        
                        Button(
                            onClick = { onSelect(files, 0) },
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play All", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                itemsIndexed(
                    items = files,
                    key = { _, item -> item.mediaId },
                    span = { _, _ -> GridItemSpan(maxLineSpan) }
                ) { idx, mediaItem ->
                    FileRow(
                        mediaItem = mediaItem,
                        isSelected = mediaItem.mediaId == nowPlayingId,
                        isFavourite = favourites.contains(mediaItem.mediaId),
                        onClick = {
                            onSelect(files, idx)
                        },
                        onInfoClick = {
                            infoItem = mediaItem
                        },
                        onAddClick = {
                            showPlaylistDialog = mediaItem
                        },
                        onFavouriteClick = {
                            audioModel.toggleFavourite(mediaItem.mediaId)
                        }
                    )
                }
            }

            // FOLDERS SECTION (Grid of Albums)
            if (folders.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Albums / Folders",
                         style = MaterialTheme.typography.labelLarge,
                         modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                }

                itemsIndexed(
                    items = folders,
                    key = { _, item -> item.mediaId }
                ) { _, mediaItem ->
                    FolderGridItem(
                        mediaItem = mediaItem,
                        onClick = {
                            audioModel.browse(mediaItem.mediaId /*, context = context*/)
                        },
                        onInfoClick = {
                            infoItem = mediaItem
                        }
                    )
                }
            }
        }
    }

    if (infoItem != null) {
        MediaInfoDialog(
            mediaItem = infoItem!!,
            onDismiss = { infoItem = null }
        )
    }

    if (showPlaylistDialog != null) {
        PlaylistSelectionDialog(
            audioModel = audioModel,
            mediaItem = showPlaylistDialog!!,
            onDismiss = { showPlaylistDialog = null }
        )
    }
}

@Composable
fun FileRow(mediaItem: MediaItem, 
            isSelected: Boolean,
            isFavourite: Boolean,
            onClick: () -> Unit,
            onInfoClick: () -> Unit,
            onAddClick: () -> Unit,
            onFavouriteClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val placeholder = painterResource(androidx.media3.session.R.drawable.media3_icon_artist)
        
        Box(modifier = Modifier.size(40.dp)) {
            AsyncImage(
                model = mediaItem.mediaMetadata.artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                placeholder = placeholder,
                error = placeholder,
                contentScale = ContentScale.Crop
            )
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onFavouriteClick) {
            Icon(
                imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favourite",
                tint = if (isFavourite) Color.Red else Color.Gray
            )
        }
        IconButton(onClick = onAddClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add to Playlist",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onInfoClick) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun FolderGridItem(mediaItem: MediaItem, 
                   onClick: () -> Unit,
                   onInfoClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val artworkModel = mediaItem.mediaMetadata.artworkUri
            val placeholder = painterResource(androidx.media3.session.R.drawable.media3_icon_album)
            
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                placeholder = placeholder,
                error = placeholder,
                contentScale = ContentScale.Crop
            )

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(bottomStart = 8.dp))
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MediaInfoDialog(mediaItem: MediaItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = {
            Text(text = "Media Information")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val artworkModel = mediaItem.mediaMetadata.artworkUri
                    val placeholder = if (mediaItem.mediaMetadata.isBrowsable == true) {
                        painterResource(androidx.media3.session.R.drawable.media3_icon_album)
                    } else {
                        painterResource(androidx.media3.session.R.drawable.media3_icon_artist)
                    }
                    
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = placeholder,
                        error = placeholder,
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                InfoField("Title", mediaItem.mediaMetadata.title?.toString())
                InfoField("Artist", mediaItem.mediaMetadata.artist?.toString())
                
                // Note: Extended online info (labels, genres) is currently not stored in MediaMetadata 
                // but artwork is shared. If needed, we could pack more info into extras.

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                InfoField("Sample Rate", mediaItem.mediaMetadata.totalDiscCount?.toString() ?: "N/A")
                InfoField("Channels", mediaItem.mediaMetadata.releaseMonth?.toString() ?: "N/A")
                InfoField("Type", if (mediaItem.mediaMetadata.isBrowsable == true) "Folder" else "Audio File")
                InfoField("Path", mediaItem.mediaId)
            }
        }
    )
}

@Composable
fun InfoField(label: String, value: String?) {
    if (!value.isNullOrBlank()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
