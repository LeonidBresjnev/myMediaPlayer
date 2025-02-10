package com.equalizer.mymediaplayer

import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.car.app.connection.CarConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.equalizer.mymediaplayer.ui.theme.MyMediaPlayerTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {


    private val audioModel: AudioModel by viewModels()
    private val wavData: WavMetaData by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        audioModel.initializeMediaController(applicationContext)


        enableEdgeToEdge()
        setContent {
            var selectedFile by remember {
                mutableStateOf<File?>(null)
            }
            var showRationalDialog by rememberSaveable { mutableStateOf(true) }

            MyMediaPlayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(
                                text = "Equalizer",
                                color = Color.White) },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }) { innerPadding ->

                    if (showRationalDialog) {
                        PermissionDemo()
                        showRationalDialog=false
                    }
                    Column(modifier = Modifier.padding(innerPadding)) {

                        val carConnectionType by CarConnection(this@MainActivity).type.observeAsState(initial = -1)

                        Button(
                            modifier = Modifier
                                .height(40.dp)
                                .width(100.dp),
                            onClick = {
                                val audioManager: AudioManager =  this@MainActivity.getSystemService(AUDIO_SERVICE) as (AudioManager)
                                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                                Log.d("audio device","antal device: ${devices.size}")
                                for (device in devices) {
                                        // Set the audio output to the car's audio system

                                    Log.d("audio device"," ${device.id}, ${device.type}, ${device.productName}, ${device.sampleRates.joinToString(";")}")

                                }
                            }
                        ) {
                            Text("devices")
                        }

                        ProjectionState(
                            carConnectionType = carConnectionType,
                            modifier = Modifier.padding(8.dp)
                        )


                        Column(modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()) {
                            ControlPanel(
                                modifier = Modifier
                                    .weight(0.65f),
                                equalizerViewModel = audioModel
                            )
                            FileSelection(modifier=Modifier
                                .weight(0.18f)
                                /* .border(width = 2.dp, color = Color.Black)*/,
                                wavData=wavData,
                                onSelect = { file ->
                                    selectedFile = file
                                    // Log.d("Selected file", selectedFile?.name?:"null")
                                } )
                            PlayControl(modifier = Modifier
                                    .weight(0.07f),
                                equalizerViewModel = audioModel, file=selectedFile)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ProjectionState(carConnectionType: Int, modifier: Modifier = Modifier) {
    val text = when (carConnectionType) {
        CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> "Not projecting"
        CarConnection.CONNECTION_TYPE_NATIVE -> "Running on Android Automotive OS"
        CarConnection.CONNECTION_TYPE_PROJECTION -> "Projecting"
        else -> "Unknown connection type"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun ControlPanel(modifier: Modifier = Modifier,
                 equalizerViewModel: AudioModel) {


    val volumenLow = equalizerViewModel.volumenLow.observeAsState()

    val frequencyLabels = listOf(
        stringResource(R.string.sub_bass_0_125_hz),
        stringResource(R.string.bass_125_250_hz),
        stringResource(R.string.low_250_500_hz),
        stringResource(R.string.low_mids_500_hz_1_khz),
        stringResource(R.string.high_mids_1_khz_2_khz),
        stringResource(R.string.high_2_4_khz),
        stringResource(R.string.upper_highs_4_8_khz),
        stringResource(R.string.air_8_khz_and_above)
    )
    LazyColumn(modifier=modifier
        .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        items(frequencyLabels.size) { index ->
            VolumeControlContent(
                frequencyLabel=frequencyLabels[index],
                modifier = Modifier,
                volumenValueLabel = stringResource(
                    id=R.string.volumen_value, volumenLow.value!![index]),
                onValueChange = { v->equalizerViewModel.setVolumen(volumeInDb =  v,index=index) },
                volume = volumenLow.value!![index],
                volumeRange = equalizerViewModel.volumeRange
            )
        }
    }
}

@Composable
private fun VolumeControlContent(
    frequencyLabel: String,
    modifier: Modifier,
    volume: Float,
    volumeRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    volumenValueLabel: String
) {
    // The volume slider should take around 1/4 of the screen height
    val screenHeight = LocalConfiguration.current.screenWidthDp
    val sliderHeight = screenHeight * 0.8


    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        /*
        modifier=Modifier.border(BorderStroke(0.1.dp, Color.Black))*/
    ) {
        Text(text = frequencyLabel)
        Row(modifier = modifier) {
            Text(text = volumenValueLabel)
            Icon(

                imageVector = if (volume == 0f) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = null
            )


            Slider(
                value = volume,
                onValueChange = onValueChange,
                modifier = modifier
                    .fillMaxWidth(0.6f)
                    .width(sliderHeight.dp),
                valueRange = volumeRange
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null
            )
        }

    }


}


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

    Row(modifier=modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(modifier = Modifier.fillMaxWidth(0.3f)) {
            files?.forEachIndexed { idx,file ->
                Text(
                    modifier = Modifier
                        .clickable {
                            selectedFile = idx
                            if ((selectedFile >= 0) &&
                                (files[selectedFile]?.name?.contains(
                                    other = ".wav",
                                    ignoreCase = true
                                ) == true)
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
                ) }
        }
        Column(modifier=Modifier.fillMaxWidth(0.7f),
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

@Composable
private fun PlayControl( modifier: Modifier,
                        equalizerViewModel: AudioModel,
                        file: File?) {

    val isPlaying by equalizerViewModel.isPlaying.observeAsState()

    val play: () -> Unit = {
        file?.let {
            val myItem = MediaItem
                .Builder()
                .setMediaId("media-1")
                .setUri(Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setArtist("David Bowie")
                        .setTitle(it.name)
                        .build()
                ).build()
            Log.d("main activity", "play called")
            equalizerViewModel.playMedia(myItem)
        }
    }

    val stop: () -> Unit = {
        equalizerViewModel.stopMedia()
    }

        // The label of the play button is now an observable state,
    // an instance of State<Int?>.
    // State<Int?> is used because the label is the id value of the resource string.
    // Thanks to the fact that the composable observes the label,
    // the composable will be recomposed (redrawn) when the observed state changes.
    //val playButtonLabel = equalizerViewModel.playButtonLabel.observeAsState()
//Log.d("PlayButtonLabel", file?.absolutePath?:"nul")
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        PlayControlContent(
            audioModel = equalizerViewModel,
            modifier=Modifier,
            enabled = (file != null)&&file.exists()&&file.name.contains(".wav"),
            // onClick handler now simply notifies the ViewModel that it has been clicked
            onClick = if (isPlaying == AudioModel.Status.PLAYING) stop else play     ,
            // playButtonLabel will never be null;
            // if it is, then we have a serious implementation issue)
        )
    }
}

@Composable
private fun PlayControlContent(audioModel: AudioModel,
                               modifier: Modifier,
                               onClick: () -> Unit,
                               enabled: Boolean = true
) {

    val playIcon =  Icons.Filled.PlayArrow
    val pauseIcon = Icons.Filled.Pause
    //val stopIcon = Icons.Filled.Stop

    val isPlaying by audioModel.isPlaying.observeAsState()

    Row(modifier=modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {

        Button(
            onClick = onClick ,
            enabled=enabled
            /*   enabled = (myController != null),*/
        )
        {
            when (isPlaying) {
                AudioModel.Status.STOPPED -> {Icon(imageVector = playIcon, contentDescription = "Play")}
                AudioModel.Status.PLAYING -> { Icon(imageVector = pauseIcon, contentDescription = "Play/Pause") }
                AudioModel.Status.PAUSED -> { Icon(imageVector = pauseIcon, contentDescription = "Play") }
                null -> Text("null")
            }



        }
        Text(text="isplaying=${isPlaying?:"null"}")
        //val carConnectionType by CarConnection(context).type.observeAsState(initial = -1)
        /*
                Button(
                        modifier = Modifier
                            .height(40.dp)
                            .width(100.dp),
                onClick = {

                    val audioManager: AudioManager=  context.getSystemService(AUDIO_SERVICE) as (AudioManager)
                    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    Log.d("audio device","antal device: ${devices.size}")
                    for (device in devices) {
                        // Set the audio output to the car's audio system

                        Log.d("audio device"," ${device.id}, ${device.type}, ${device.productName}, ${device.sampleRates.joinToString(";")}")

                    }
                }
                ) {
                Text("devices")
                }*/
     /*   ProjectionState(
            carConnectionType = carConnectionType,
            modifier = Modifier.padding(8.dp)
        )*/
    }
}