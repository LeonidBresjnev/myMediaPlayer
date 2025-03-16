package com.equalizer.mymediaplayer

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.io.File

@Composable
fun FileSelection(modifier: Modifier=Modifier,
                  wavData: WavMetaData,
                  onSelect: (File?) -> Unit = {}) {
    val folder = remember {
        File(
            Environment.getExternalStorageDirectory(),
            "/Music")
    }

    val files = remember { folder.listFiles() }

    var selectedFile by remember {
        mutableIntStateOf(-1)
    }


    val sampleRate = wavData.sampleRate.observeAsState()
    val numChannels = wavData.numChannels.observeAsState()
    val bitsPerSample = wavData.bitsPerSample.observeAsState()

    Column(modifier=modifier.fillMaxSize()) {
        LazyColumn (modifier = Modifier.weight(0.6f).padding()) {
            itemsIndexed(files!!) { idx, file ->
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
            Text(
                modifier = Modifier.weight(0.2f),
                text = """File info
File name: ${if (selectedFile >= 0 ) files?.get(selectedFile)?.name else ""}  
Sample Rate: ${sampleRate.value!!}
Channels: ${numChannels.value!!}
Bit depth: ${bitsPerSample.value!!}
                """.trimMargin()
            )


        }
    }
}