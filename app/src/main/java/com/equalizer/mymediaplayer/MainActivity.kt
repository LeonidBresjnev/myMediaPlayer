package com.equalizer.mymediaplayer

import android.content.res.Configuration
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.car.app.connection.CarConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DirectionsCarFilled
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.equalizer.mymediaplayer.ui.theme.MyMediaPlayerTheme

data class TabRowItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val screen: @Composable () -> Unit,
)

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val audioModel: AudioModel by viewModels()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
            audioModel.updateConnectedDevice(this@MainActivity)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
            audioModel.updateConnectedDevice(this@MainActivity)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioModel.initializeMediaController(applicationContext)
        enableEdgeToEdge()
        
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        audioModel.updateConnectedDevice(this)
        
        setContent {
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            val carConnectionType by CarConnection(this@MainActivity).type.observeAsState(initial = -1)
            val connectedDevice by audioModel.connectedDevice.observeAsState(ConnectedDeviceInfo(AudioDeviceType.PHONE, "Phone Speaker"))

            val tabRowItems = listOf(
                TabRowItem(
                    title = "Library",
                    screen = {
                        MediaBrowserScreen(
                            modifier = Modifier,
                            audioModel = audioModel,
                            onSelect = { playlist, index ->
                                audioModel.loadMedia(playlist, index)
                            })
                    },
                    selectedIcon = Icons.AutoMirrored.Filled.QueueMusic,
                    unselectedIcon = Icons.AutoMirrored.Outlined.QueueMusic
                ),
                TabRowItem(
                    title = "Playlists",
                    screen = {
                        PlaylistsScreen(
                            audioModel = audioModel,
                            onPlaylistClick = { playlistId ->
                                audioModel.browse(playlistId)
                                selectedTabIndex = 0
                            }
                        )
                    },
                    selectedIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    unselectedIcon = Icons.AutoMirrored.Outlined.PlaylistPlay
                ),
                TabRowItem(
                    title = "Radio",
                    screen = {
                        RadioScreen(
                            audioModel = audioModel,
                            onSelect = { stations, index ->
                                audioModel.loadMedia(stations, index)
                            }
                        )
                    },
                    selectedIcon = Icons.Default.Radio,
                    unselectedIcon = Icons.Outlined.Radio
                ),
                TabRowItem(
                    title = "Sound Setting",
                    screen = {
                        ControlPanel(
                            modifier = Modifier,
                            equalizerViewModel = audioModel
                        )
                    },
                    selectedIcon = Icons.Default.Tune,
                    unselectedIcon = Icons.Outlined.Tune
                ),
                TabRowItem(
                    title = "Analysis",
                    screen = {
                        AnalysisPanel(
                            modifier = Modifier,
                            equalizerViewModel = audioModel
                        )
                    },
                    selectedIcon = Icons.Default.Analytics,
                    unselectedIcon = Icons.Outlined.Analytics
                )
            )
            
            val pagerState = rememberPagerState { tabRowItems.size }

            LaunchedEffect(selectedTabIndex) {
                pagerState.animateScrollToPage(selectedTabIndex)
            }
            LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                if (!pagerState.isScrollInProgress) {
                    selectedTabIndex = pagerState.currentPage
                }
            }

            var permissionsOk by remember { mutableStateOf(false) }

            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            MyMediaPlayerTheme {
                if (!permissionsOk) {
                    MultiPermissionRequest(setPermissionsOk = { permissionsOk = it })
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            if (!isLandscape) {
                                CenterAlignedTopAppBar(
                                    title = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(text = "SoundsGood", color = Color.White)
                                            Text(
                                                text = "Auch im Auto",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    },
                                    actions = {
                                        val isCar = carConnectionType != CarConnection.CONNECTION_TYPE_NOT_CONNECTED || connectedDevice.type == AudioDeviceType.CAR
                                        val icon = when {
                                            isCar -> Icons.Default.DirectionsCarFilled
                                            connectedDevice.type == AudioDeviceType.HEADSET -> Icons.Default.Headset
                                            connectedDevice.type == AudioDeviceType.SPEAKER -> Icons.Default.Speaker
                                            else -> Icons.Default.PhoneAndroid
                                        }
                                        val deviceName = if (isCar) "Car Audio" else connectedDevice.name

                                        IconButton(onClick = {
                                            Toast.makeText(this@MainActivity, "Connected to: $deviceName", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = "Connected Device: $deviceName",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        titleContentColor = Color.White,
                                        actionIconContentColor = Color.White,
                                        navigationIconContentColor = Color.White
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        if (isLandscape) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                // MENU ON LEFT
                                Column(
                                    modifier = Modifier
                                        .width(84.dp)
                                        .fillMaxHeight()
                                ) {
                                    // Branding Box (TopBar substitute for menu)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp) // Match standard top bar height
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "SoundsGood",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    NavigationRail(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        header = null // Moved to the Box above
                                    ) {
                                        // Make the internal item list scrollable if needed
                                        Column(
                                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            tabRowItems.forEachIndexed { index, item ->
                                                NavigationRailItem(
                                                    selected = selectedTabIndex == index,
                                                    onClick = { selectedTabIndex = index },
                                                    icon = {
                                                        Icon(
                                                            imageVector = if (index == selectedTabIndex) item.selectedIcon else item.unselectedIcon,
                                                            contentDescription = item.title
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            item.title,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // CONTENT IN MIDDLE
                                Box(modifier = Modifier.weight(2f)) {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.Top,
                                        userScrollEnabled = false // Disabled in landscape
                                    ) {
                                        tabRowItems[it].screen()
                                    }
                                }

                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // PLAYER ON RIGHT
                                PlayerDisplay(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f),
                                    audioModel = audioModel
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                // ARTWORK PLAYER VIEW (Top 1/3)
                                PlayerDisplay(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    audioModel = audioModel
                                )

                                // TABS & CONTENT (Bottom 2/3)
                                Column(modifier = Modifier.weight(2f)) {
                                    SecondaryTabRow(
                                        selectedTabIndex = selectedTabIndex,
                                        containerColor = TabRowDefaults.primaryContainerColor,
                                        contentColor = TabRowDefaults.primaryContentColor,
                                        indicator = {
                                            TabRowDefaults.SecondaryIndicator(
                                                Modifier.tabIndicatorOffset(selectedTabIndex)
                                            )
                                        },
                                        divider = {}
                                    ) {
                                        tabRowItems.forEachIndexed { index, item ->
                                            Tab(
                                                selected = selectedTabIndex == index,
                                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                                unselectedContentColor = MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.5f
                                                ),
                                                onClick = { selectedTabIndex = index },
                                                icon = {
                                                    ImageVectorIcon(
                                                        imageVector = if (index == selectedTabIndex) item.selectedIcon else item.unselectedIcon,
                                                        contentDescription = item.title
                                                    )
                                                },
                                                text = {
                                                    Text(
                                                        text = item.title,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    Box(modifier = Modifier.fillMaxSize()) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.Top,
                                            userScrollEnabled = true
                                        ) {
                                            tabRowItems[it].screen()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }
}

@Composable
fun ImageVectorIcon(imageVector: ImageVector, contentDescription: String?) {
    Icon(imageVector = imageVector, contentDescription = contentDescription)
}
