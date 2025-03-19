package com.equalizer.mymediaplayer

import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FileSelection(modifier: Modifier=Modifier,
                  wavData: WavMetaData,
                  onSelect: (File?) -> Unit = {}) {


    var currentDir by rememberSaveable {
        mutableStateOf("")
    }

    var folder by rememberSaveable {
        mutableStateOf(File(
            Environment.getExternalStorageDirectory(),
            "/Music")
        )
    }

    var files by rememberSaveable {
        mutableStateOf(folder.listFiles()?.toList()?:emptyList())
    }

    var currentDepth by rememberSaveable {
        mutableIntStateOf(0)
    }


    LaunchedEffect(currentDir) {
        Log.d("file selection", Environment.getExternalStorageDirectory().absolutePath +"/Music$currentDir")
        folder = File(Environment.getExternalStorageDirectory().absolutePath + "/Music$currentDir")
        files=folder.listFiles()?.toList()?:emptyList()
        if (currentDepth>0) {
            val parent = currentDir.split("/").dropLast(1).joinToString(separator = "/")
            val parentFile=File(Environment.getExternalStorageDirectory().absolutePath + "/Music" + parent)
            files=listOf(parentFile) + files
        }

        Log.d("file selection", "folder changed")
    }

    var selectedFile by rememberSaveable {
        mutableIntStateOf(-1)
    }



    val title = wavData.name.observeAsState()
    val artist = wavData.artist.observeAsState()
    val sampleRate = wavData.sampleRate.observeAsState()
    val numChannels = wavData.numChannels.observeAsState()
    val bitsPerSample = wavData.bitsPerSample.observeAsState()
    val imageArray = wavData.imageArray.observeAsState()

    Column(modifier=modifier
        .fillMaxSize()
        .padding(16.dp)
        .fillMaxSize()) {
        LazyColumn (modifier = Modifier.weight(0.6f)) {
            itemsIndexed(files.filter { it.isDirectory ||
                    it.name.endsWith(".wav",ignoreCase = true) ||
                    it.name.endsWith(".mp3",ignoreCase = true) }) { idx, file ->
                Text(color = if (file.isDirectory) MaterialTheme.colorScheme.tertiary else Color.Black,
                    modifier = Modifier
                        .clickable {

                            if ((currentDepth > 0) && idx == 0) {
                                currentDepth--
                                currentDir =
                                    currentDir.split("/").dropLast(1).joinToString(separator = "/")
                                Log.d("file selection", "parent clicked")
                            } else if (file.isDirectory) {
                                currentDepth++
                                Log.d("file selection", "folder clicked")
                                currentDir = currentDir + "/" + file.name
                                selectedFile = -1
                            } else {
                                selectedFile = idx
                                if ((selectedFile >= 0) &&
                                    (files[selectedFile]?.name?.endsWith(
                                        ".wav",
                                        ignoreCase = true
                                    ) == true ||
                                            files[selectedFile]?.name?.endsWith(
                                                ".mp3",
                                                ignoreCase = true
                                            ) == true)
                                ) {
                                    Log.d("file selection", "currentDir: $currentDir")
                                    wavData.readingAudioFile(File(files[selectedFile].absolutePath))
                                    onSelect(File(files[selectedFile].absolutePath))
                                } else {
                                    wavData.reset()
                                    onSelect(null)
                                }
                            }
                        }
                        .background(if (idx == selectedFile) Color.Blue else Color.Transparent),

                    text = file.name + if (file.isDirectory) "/" else ""
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
                Text(
                    modifier = Modifier.weight(0.6f),
                    text = """File info
Name: ${title.value ?: ""}
Artist: ${artist.value ?: ""}
Sample Rate: ${sampleRate.value!!}
Channels: ${numChannels.value!!}
Bit depth: ${bitsPerSample.value!!}
                """.trimMargin()
                )

                if (imageArray.value?.isNotEmpty() == true) {
                    // Decode the ByteArray into a Bitmap on a background thread
                    val bitmap =
                        BitmapFactory.decodeByteArray(imageArray.value, 0, imageArray.value!!.size)

                    // Convert the Bitmap to an ImageBitmap
                    val imageBitmap = bitmap.asImageBitmap()

                    // Display the ImageBitmap using the Image composable

                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Image from ByteArray",
                        modifier = Modifier.weight(0.4f).size(100.dp) // Adjust size as needed
                    )

                }
            }
        }
    }
}