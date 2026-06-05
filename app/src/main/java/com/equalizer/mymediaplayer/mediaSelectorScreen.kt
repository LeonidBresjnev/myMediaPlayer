package com.equalizer.mymediaplayer

import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.equalizer.common.metadata.M4aMeta
import com.equalizer.common.metadata.Mp3Meta
import com.equalizer.common.metadata.WavMeta
import com.mpatric.mp3agic.Mp3File
import java.io.File

/*
class Mp3File(file: File) : Mp3File(file), Parcelable {
    override fun describeContents(): Int {
        TODO("Not yet implemented")
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        TODO("Not yet implemented")
    }
    companion object CREATOR : Parcelable.Creator<Mp3File> {
        override fun createFromParcel(parcel: Parcel): Mp3File {
            return Mp3File(parcel)
        }

        override fun newArray(size: Int): Array<Mp3File?> {
            return arrayOfNulls(size)
        }
    }
}*/
@Composable
fun FileSelection(modifier: Modifier=Modifier,
                  wavData: WavMetaData,
                  onSelect: (File?) -> Unit = {},
                  context: Context) {


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
        mutableStateOf<List<File>>(/*folder.listFiles()?.toList()?:*/ emptyList())
    }


    var currentDepth by rememberSaveable {
        mutableIntStateOf(0)
    }

    var mp3Infos by remember {
        mutableStateOf<List<Mp3File?>>(files.map {
            if (it.name.endsWith("mp3",ignoreCase = true)) {
                return@map Mp3File(it)
            } else
                return@map null
        })
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
        Log.d("mp3info", "mp3 info called")

        mp3Infos = files.map {
            if (it.name.endsWith("mp3",ignoreCase = true)) {
                return@map Mp3File(it)
            } else
                return@map null
        }
    }

    var selectedFile by rememberSaveable {
        mutableIntStateOf(-1)
    }


    val imageBitmap = wavData.imageBitmap.observeAsState()

    val myMeta = wavData.myMeta.observeAsState()

    Column(modifier=modifier
        .fillMaxSize()
        .padding(16.dp)
        .fillMaxSize()) {
        LazyColumn (modifier = Modifier.weight(0.6f)) {
            itemsIndexed(items=files.filter { it.isDirectory ||
                    it.name.endsWith(".wav",ignoreCase = true) ||
                    it.name.endsWith(".mp3",ignoreCase = true) ||
                    it.name.endsWith(".m4a",ignoreCase = true) },
                key = { _, file -> file.absolutePath }
           ) { idx, file ->
                /*val mp3Info = if (file.name.endsWith(
                        ".mp3",
                        ignoreCase = true
                    ) == true) {
                    Mp3File(file)
                } else null*/
                Text(color = if (file.isDirectory) MaterialTheme.colorScheme.tertiary else Color.Black,
                    modifier = Modifier
                        .clickable {

                            if ((currentDepth > 0) && idx == 0) {
                                currentDepth--
                                currentDir =
                                    currentDir.split("/").dropLast(1).joinToString(separator = "/")
                            } else if (file.isDirectory) {
                                currentDepth++
                                currentDir = currentDir + "/" + file.name
                                selectedFile = -1
                            } else {
                                selectedFile = idx
                                if ((selectedFile >= 0) &&
                                    (files[selectedFile].name.endsWith(".wav", ignoreCase = true) == true ||
                                            files[selectedFile].name.endsWith(".mp3",ignoreCase = true) == true ||
                                            files[selectedFile].name.endsWith(".m4a",ignoreCase = true) == true)
                                ) {

                                    //coroutineScope.launch {
                                        wavData.readingAudioFile(File(files[selectedFile].absolutePath), context)
                                    //}
                                    onSelect(File(files[selectedFile].absolutePath))
                                } else {
                                    wavData.reset()
                                    onSelect(null)
                                }
                            }
                        }
                        .background(if (idx == selectedFile) Color.Blue else Color.Transparent),

                    text = mp3Infos[idx]?.let { it->
                        (it.id3v2Tag?.track ?: it.id3v1Tag?.track ?: "") + " " + (it.id3v2Tag?.title
                        ?: it.id3v1Tag?.title ?: file.name)
                    }?:
                    (file.name + if (file.isDirectory) "/" else "")

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
        if (wavData.isLoading) {
            CircularProgressIndicator(
                strokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap,
                strokeWidth = 10.dp,
                modifier = Modifier.width(64.dp).align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier.weight(0.4f),
                verticalArrangement = Arrangement.Center
            ) {
                //if (myMeta.value != null) Text(text= myMeta.value!!::class.simpleName.toString())
                if (myMeta.value is Mp3Meta ) {
                    (myMeta.value as Mp3Meta).also {
                        Row {
                            Text(
                                modifier = Modifier.weight(0.6f),
                                text = """File info                        
Name: ${it.name}      
Artist: ${it.artist}
Sample Rate: ${it.sampleRate}
Channels: ${it.numChannels}
                """.trimMargin()
                            )

                            if (imageBitmap.value != null) {
                                Image(
                                    bitmap = imageBitmap.value!!,
                                    contentDescription = "Image from ByteArray",
                                    modifier = Modifier.weight(0.4f)
                                        .size(100.dp) // Adjust size as needed
                                )

                            }
                        }
                    }

                }
                else if (myMeta.value is M4aMeta ) {
                    (myMeta.value as M4aMeta).also {
                        Row {
                            Text(
                                modifier = Modifier.weight(0.6f),
                                text = """File info                        
Name: ${it.name}      
Artist: ${it.artist}
Sample Rate: ${it.sampleRate}
Channels: ${it.numChannels}
                """.trimMargin()
                            )

                            if (imageBitmap.value != null) {
                                Image(
                                    bitmap = imageBitmap.value!!,
                                    contentDescription = "Image from ByteArray",
                                    modifier = Modifier.weight(0.4f)
                                        .size(100.dp) // Adjust size as needed
                                )

                            }
                        }
                    }

                } else if (myMeta.value is WavMeta) {
                    (myMeta.value as WavMeta).also {
                        Text(
                            modifier = Modifier.weight(0.6f),
                            text = """File info     
Sample Rate: ${it.sampleRate}
Channels: ${it.numChannels}
Byterate: ${it.byteRate}
                """.trimMargin()
                        )
                    }
                }
            }
        }
    }
}