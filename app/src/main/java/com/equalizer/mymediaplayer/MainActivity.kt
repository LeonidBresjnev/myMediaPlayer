package com.equalizer.mymediaplayer

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.car.app.connection.CarConnection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.DirectionsCarFilled
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.equalizer.mymediaplayer.ui.theme.MyMediaPlayerTheme
import java.io.File

data class TabRowItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val screen: @Composable () -> Unit,
)

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


            var selectedTabIndex by remember {
                mutableIntStateOf(value=0)
            }

            val tabRowItems = listOf(
                TabRowItem(
                    title = "Media browser",
                    screen = {
                        FileSelection(modifier=Modifier,
                            wavData=wavData,
                            onSelect = { file ->
                                selectedFile = file
                            } )
                    },
                    selectedIcon = Icons.AutoMirrored.Filled.List,
                    unselectedIcon = Icons.AutoMirrored.Outlined.List
                ),
                TabRowItem(
                    title = "Equalizer",
                    screen = { ControlPanel(
                        modifier = Modifier,
                        equalizerViewModel = audioModel
                    )},
                    selectedIcon = Icons.AutoMirrored.Filled.QueueMusic,
                    unselectedIcon = Icons.AutoMirrored.Outlined.QueueMusic
                ),
                TabRowItem(
                    title = "test browser",
                    screen = {
                        MediaBrowserScreen(modifier=Modifier,
                            audioModel=audioModel,
                            onSelect = { file ->
                                selectedFile = file
                            } )
                    },
                    selectedIcon = Icons.AutoMirrored.Filled.List,
                    unselectedIcon = Icons.AutoMirrored.Outlined.List
                )
            )
            val pagerState = rememberPagerState {
                tabRowItems.size
            }

            LaunchedEffect(selectedTabIndex) {
                pagerState.animateScrollToPage(selectedTabIndex)
            }
            LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                if (!pagerState.isScrollInProgress) {
                    selectedTabIndex = pagerState.currentPage
                }
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

                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        TabRow(
                            modifier = Modifier.weight(0.1f),
                            selectedTabIndex = selectedTabIndex
                        ) {
                            tabRowItems.forEachIndexed { index, item ->
                                Tab(
                                    selected = /*pagerState.currentPage*/ selectedTabIndex == index,
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.5f
                                    ),
                                    onClick = {
                                        selectedTabIndex = index
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (index == selectedTabIndex) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.title
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = item.title,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                )
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.8f),
                            verticalAlignment = Alignment.Top,
                            userScrollEnabled = true
                        ) {
                            tabRowItems[it].screen()
                        }

                        val carConnectionType by CarConnection(this@MainActivity).type.observeAsState(
                            initial = -1
                        )
                        /*
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
                        }*/
                        Column(modifier = Modifier.weight(0.1f)) {
                            ProjectionState(

                                carConnectionType = carConnectionType,
                                modifier = Modifier.padding(8.dp)
                            )




                            PlayControl(
                                modifier = Modifier
                                    .weight(0.07f),
                                equalizerViewModel = audioModel, file = selectedFile
                            )
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
Row {

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
    if (carConnectionType == CarConnection.CONNECTION_TYPE_PROJECTION || true) {
        Icon(
            imageVector = Icons.Default.DirectionsCarFilled,
            contentDescription = "car-icon",
            tint= Color.Red
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
            enabled = (file != null)&&file.exists()&& (file.name.endsWith(".wav")
                    || file.name.endsWith(".mp3") ) || (isPlaying == AudioModel.Status.PLAYING),
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
       // Text(text="isplaying=${isPlaying?:"null"}")
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