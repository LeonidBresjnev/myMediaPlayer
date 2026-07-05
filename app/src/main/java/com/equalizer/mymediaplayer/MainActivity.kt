package com.equalizer.mymediaplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.car.app.connection.CarConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.DirectionsCarFilled
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val audioModel: AudioModel by viewModels()

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioModel.initializeMediaController(applicationContext)
        enableEdgeToEdge()
        
        setContent {
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            val carConnectionType by CarConnection(this@MainActivity).type.observeAsState(initial = -1)

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
                    title = "Equalizer",
                    screen = {
                        ControlPanel(
                            modifier = Modifier,
                            equalizerViewModel = audioModel
                        )
                    },
                    selectedIcon = Icons.Default.Tune,
                    unselectedIcon = Icons.Outlined.Tune
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

            MyMediaPlayerTheme {
                if (!permissionsOk) {
                    MultiPermissionRequest(setPermissionsOk = { permissionsOk = it })
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = { Text(text = "Equalizer", color = Color.White) },
                                actions = {
                                    if (carConnectionType != CarConnection.CONNECTION_TYPE_NOT_CONNECTED) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCarFilled,
                                            contentDescription = "Car Connected",
                                            tint = Color.White,
                                            modifier = Modifier.padding(end = 16.dp).size(24.dp)
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
                        }/*,
                        bottomBar = {
                            PlayControl(
                                modifier = Modifier.fillMaxWidth(),
                                audioModel = audioModel
                            )
                        }*/
                    ) { innerPadding ->
                        Column(modifier = Modifier
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
                                            unselectedContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
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

@Composable
fun ImageVectorIcon(imageVector: ImageVector, contentDescription: String?) {
    Icon(imageVector = imageVector, contentDescription = contentDescription)
}
