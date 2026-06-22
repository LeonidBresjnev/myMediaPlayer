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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import java.io.File

@Composable
fun MediaBrowserScreen(modifier: Modifier = Modifier,
                       audioModel: AudioModel,
                       onSelect: (File?) -> Unit = {}
) {
    val context = LocalContext.current
    val media = audioModel.subItemMediaList.observeAsState(emptyList())
    val currentPath by audioModel.currentPath.observeAsState("root")
    
    val onlineArtwork = audioModel.onlineArtworkMap
    val onlineInfo = audioModel.onlineInfoMap

    var selectedIdx by rememberSaveable {
        mutableIntStateOf(-1)
    }

    var infoItem by remember {
        mutableStateOf<MediaItem?>(null)
    }

    val folders = media.value.filter { it.mediaMetadata.isBrowsable == true }
    val files = media.value.filter { it.mediaMetadata.isBrowsable != true }

    Column(modifier = modifier
        .fillMaxSize()
        .padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)) {
            if (currentPath != "root") {
                Text(
                    text = "< Back",
                    modifier = Modifier
                        .clickable {
                            audioModel.navigateBack(context = context)
                            selectedIdx = -1
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // FILES SECTION (List at top)
            if (files.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Files",
                         style = MaterialTheme.typography.labelLarge,
                         modifier = Modifier.padding(vertical = 8.dp))
                }

                itemsIndexed(
                    items = files,
                    key = { _, item -> item.mediaId },
                    span = { _, _ -> GridItemSpan(maxLineSpan) }
                ) { idx, mediaItem ->
                    FileRow(
                        mediaItem = mediaItem,
                        isSelected = selectedIdx == (folders.size + idx),
                        onlineArtworkUrl = onlineArtwork[mediaItem.mediaId],
                        onClick = {
                            selectedIdx = folders.size + idx
                            onSelect(File(mediaItem.mediaId))
                        },
                        onInfoClick = {
                            infoItem = mediaItem
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
                        onlineArtworkUrl = onlineArtwork[mediaItem.mediaId],
                        onClick = {
                            audioModel.browse(mediaItem.mediaId, context = context)
                            selectedIdx = -1
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
            onlineInfo = onlineInfo[infoItem!!.mediaId],
            onDismiss = { infoItem = null }
        )
    }
}

@Composable
fun FileRow(mediaItem: MediaItem, 
            isSelected: Boolean, 
            onlineArtworkUrl: String?,
            onClick: () -> Unit,
            onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val musicPlaceholder = rememberVectorPainter(Icons.Default.MusicNote)
        AsyncImage(
            model = mediaItem.mediaMetadata.artworkUri ?: onlineArtworkUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
            placeholder = musicPlaceholder,
            error = musicPlaceholder,
            contentScale = ContentScale.Crop
        )
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
                   onlineArtworkUrl: String?,
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
            val artworkModel = mediaItem.mediaMetadata.artworkUri ?: onlineArtworkUrl
            val musicPlaceholder = rememberVectorPainter(Icons.Default.MusicNote)
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                placeholder = musicPlaceholder,
                error = musicPlaceholder,
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
fun MediaInfoDialog(mediaItem: MediaItem, onlineInfo: com.equalizer.common.OnlineInfo?, onDismiss: () -> Unit) {
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
                    val artworkModel = mediaItem.mediaMetadata.artworkUri ?: onlineInfo?.artworkUrl
                    val musicPlaceholder = rememberVectorPainter(Icons.Default.MusicNote)
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = musicPlaceholder,
                        error = musicPlaceholder,
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                InfoField("Title", mediaItem.mediaMetadata.title?.toString())
                InfoField("Artist", mediaItem.mediaMetadata.artist?.toString())
                
                if (onlineInfo != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Online Information", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    InfoField("Release Date", onlineInfo.releaseDate)
                    InfoField("Label", onlineInfo.label)
                    InfoField("Genres", onlineInfo.genres?.joinToString(", "))
                } else {
                    val artist = mediaItem.mediaMetadata.artist?.toString()
                    val title = mediaItem.mediaMetadata.title?.toString()
                    if (!artist.isNullOrBlank() && !title.isNullOrBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Online data loading...", 
                             style = MaterialTheme.typography.bodySmall, 
                             color = Color.Gray)
                    }
                }

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
