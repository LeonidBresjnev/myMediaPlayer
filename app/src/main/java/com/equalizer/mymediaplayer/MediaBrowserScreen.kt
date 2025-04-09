package com.equalizer.mymediaplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun MediaBrowserScreen(modifier: Modifier=Modifier,
                       audioModel: AudioModel,
                       onSelect: (File?) -> Unit = {}

) {

    val media = audioModel.subItemMediaList.observeAsState()

    var selectedIdx by rememberSaveable {
        mutableIntStateOf(-1)
    }

    Column(modifier=modifier
        .fillMaxSize()
        .padding(16.dp)
        .fillMaxSize()) {
        Text(text="size=${media.value!!.size}")
        LazyColumn (modifier = Modifier.weight(0.6f)) {
            itemsIndexed(
                items=media.value!!,
                key = { _, media -> media.mediaId }
            ) { idx, media ->
                Text(color = if (media.mediaMetadata.isBrowsable == true) MaterialTheme.colorScheme.tertiary else Color.Black,
                    text=(media.mediaMetadata.trackNumber?.let { "$it " }?:"") + media.mediaMetadata.title,
                modifier=Modifier.clickable(
                    enabled = true,
                    onClick = { selectedIdx = idx }
                )
                )
            }
            /*  files?.forEachIndexed { idx,file ->
                  Text(
                      modifier = Modifier
                          .clickable {
                              selectedFile = idx
                              if ((selectedFile >= 0) &&
                                  (files[selectedFile]?.name?.endsWith(".wav",ignoreCase = true) == true||
                                          files[selectedFile]?.name?.endsWith(".mp3",ignoreCase = true) == true )
                              ) {
                                  wavData.readingAudioFile(File("/storage/emulated/0/Music/" + files[selectedFile].name))
                                  onSelect(File("/storage/emulated/0/Music/" + files[selectedFile].name))
                              } else {
                                  wavData.reset()
                                  onSelect(null)

                              }

                          }
                          .background(if (idx == selectedFile) Color.Blue else Color.Transparent),

                      text = file.name
                  ) }*/
        }
        HorizontalDivider(color= MaterialTheme.colorScheme.primary)
        Column(modifier=Modifier.weight(0.4f),
            verticalArrangement = Arrangement.Center) {
            Row(Modifier.weight(0.2f)) {
                if (selectedIdx>0) {
                Text(
                    modifier = Modifier.weight(0.6f),
                    text = """File info
Name: ${media.value?.get(selectedIdx)?.mediaMetadata?.title?:""}
Artist: ${media.value?.get(selectedIdx)?.mediaMetadata?.artist?:""}
Sample Rate: ${media.value?.get(selectedIdx)?.mediaMetadata?.totalDiscCount?:""}
Channels: ${media.value?.get(selectedIdx)?.mediaMetadata?.releaseMonth?:""}
                """.trimMargin()
                )
/*
                if (imageArray.value?.isNotEmpty() == true) {
                    // Decode the ByteArray into a Bitmap on a background thread
                    val bitmap =
                        BitmapFactory.decodeByteArray(imageArray.value, 0, imageArray.value!!.size)

                    // Convert the Bitmap to an ImageBitmap
                    val imageBitmap: ImageBitmap = bitmap.asImageBitmap()

                    // Display the ImageBitmap using the Image composable

                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Image from ByteArray",
                        modifier = Modifier.weight(0.4f).size(100.dp) // Adjust size as needed
                    )*/
                }
                }
            }
        }
    }
