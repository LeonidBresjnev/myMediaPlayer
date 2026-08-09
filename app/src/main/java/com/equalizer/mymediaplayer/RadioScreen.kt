package com.equalizer.mymediaplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

@UnstableApi
@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    audioModel: AudioModel,
    onSelect: (List<MediaItem>, Int) -> Unit
) {
    val stations by audioModel.radioMediaList.observeAsState(emptyList())
    val nowPlayingId by audioModel.nowPlayingId.observeAsState()
    val favourites by audioModel.favourites.observeAsState(emptySet())
    var isLoading by remember { mutableStateOf(true) }
    var showPlaylistDialog by remember { mutableStateOf<MediaItem?>(null) }

    LaunchedEffect(stations) {
        if (stations.isNotEmpty()) {
            isLoading = false
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Radio Channels",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { 
                isLoading = true
                audioModel.browse("icecast_root") 
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        if (isLoading && stations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.size(16.dp))
                    Text("Loading stations...", color = Color.Gray)
                }
            }
        } else if (!isLoading && stations.isEmpty()) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No stations found. Try refreshing.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(stations) { index, station ->
                    StationRow(
                        station = station,
                        isSelected = station.mediaId == nowPlayingId,
                        isFavourite = favourites.contains(station.mediaId),
                        onClick = { onSelect(stations, index) },
                        onFavouriteClick = { audioModel.toggleFavourite(station.mediaId) },
                        onAddClick = { showPlaylistDialog = station }
                    )
                }
            }
        }
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
fun StationRow(
    station: MediaItem,
    isSelected: Boolean,
    isFavourite: Boolean,
    onClick: () -> Unit,
    onFavouriteClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (station.mediaMetadata.artworkUri != null) Color.White else MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            val artworkUri = station.mediaMetadata.artworkUri
            if (artworkUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artworkUri)
                            .crossfade(true)
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    contentScale = ContentScale.Fit
                )
            } else if (isSelected) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            } else {
                Icon(Icons.Default.Radio, contentDescription = null, tint = Color.White)
            }
        }

        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                text = station.mediaMetadata.title?.toString() ?: "Unknown Station",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = station.mediaMetadata.subtitle?.toString() ?: "No Genre",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                val bitrate = station.mediaMetadata.extras?.getInt("BITRATE") ?: 0
                if (bitrate > 0) {
                    Text(
                        text = "${bitrate}kbps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                station.mediaMetadata.totalDiscCount?.let { sampleRate ->
                    Text(
                        text = "${sampleRate/1000}kHz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
