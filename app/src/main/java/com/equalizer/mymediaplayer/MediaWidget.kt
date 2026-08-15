package com.equalizer.mymediaplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.equalizer.common.MyMediaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class MediaWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val title = prefs[TitleKey] ?: "Not Playing"
            val artist = prefs[ArtistKey] ?: ""
            val isPlaying = prefs[IsPlayingKey] ?: false
            val artworkUri = prefs[ArtworkUriKey]

            Log.d("MediaWidget", "Recomposing: title=$title, artist=$artist, isPlaying=$isPlaying, artworkUri=$artworkUri")
            WidgetContent(title, artist, isPlaying, artworkUri)
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun WidgetContent(
        title: String,
        artist: String,
        isPlaying: Boolean,
        artworkUri: String?
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(Color(0xFF212121))
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top
        ) {
            // App Branding (Tiny Header)
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.mipmap.lever),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "SoundsGood",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.8f)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Player Content
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork
                Box(
                    modifier = GlanceModifier
                        .size(44.dp)
                        .background(Color.DarkGray)
                ) {
                    if (!artworkUri.isNullOrBlank()) {
                        Image(
                            provider = ImageProvider(Icon.createWithContentUri(artworkUri.toUri())),
                            contentDescription = "Artwork",
                            modifier = GlanceModifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        PlaceholderArt()
                    }
                }

                Spacer(modifier = GlanceModifier.width(10.dp))

                // Metadata
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = title,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    if (artist.isNotEmpty()) {
                        Text(
                            text = artist,
                            style = TextStyle(
                                color = ColorProvider(Color.LightGray),
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_media_previous),
                        contentDescription = "Previous",
                        modifier = GlanceModifier
                            .size(28.dp)
                            .clickable(actionRunCallback<ControlActionCallback>(
                                actionParametersOf(ActionKey to ActionPrev)
                            ))
                    )
                    
                    Spacer(modifier = GlanceModifier.width(8.dp))

                    val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    
                    Image(
                        provider = ImageProvider(playPauseIcon),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = GlanceModifier
                            .size(36.dp)
                            .clickable(actionRunCallback<ControlActionCallback>(
                                actionParametersOf(ActionKey to ActionPlayPause)
                            ))
                    )

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    Image(
                        provider = ImageProvider(android.R.drawable.ic_media_next),
                        contentDescription = "Next",
                        modifier = GlanceModifier
                            .size(28.dp)
                            .clickable(actionRunCallback<ControlActionCallback>(
                                actionParametersOf(ActionKey to ActionNext)
                            ))
                    )
                }
            }
        }
    }

    @Composable
    private fun PlaceholderArt() {
        Image(
            provider = ImageProvider(android.R.drawable.ic_menu_report_image),
            contentDescription = "Placeholder",
            modifier = GlanceModifier.fillMaxSize().padding(8.dp)
        )
    }

    companion object {
        val TitleKey = stringPreferencesKey("title")
        val ArtistKey = stringPreferencesKey("artist")
        val IsPlayingKey = booleanPreferencesKey("is_playing")
        val ArtworkUriKey = stringPreferencesKey("artwork_uri")

        val ActionKey = ActionParameters.Key<String>("action")
        const val ActionPlayPause = "play_pause"
        const val ActionNext = "next"
        const val ActionPrev = "prev"
    }
}

class ControlActionCallback : ActionCallback {
    @OptIn(UnstableApi::class)
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[MediaWidget.ActionKey] ?: return
        val intent = Intent(context, MyMediaService::class.java).apply {
            when (action) {
                MediaWidget.ActionPlayPause -> setAction("ACTION_PLAY_PAUSE")
                MediaWidget.ActionNext -> setAction("ACTION_NEXT")
                MediaWidget.ActionPrev -> setAction("ACTION_PREV")
            }
        }
        context.startService(intent)
    }
}

class MediaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediaWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.equalizer.mymediaplayer.UPDATE_WIDGET" || 
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED ||
            intent.action == "android.appwidget.action.APPWIDGET_UPDATE") {
            
            val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "widget_update",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}

class WidgetUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @OptIn(UnstableApi::class)
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.Main) {
            val sessionToken = SessionToken(context, ComponentName(context, MyMediaService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            
            try {
                val controller = controllerFuture.await()
                val mediaItem = controller.currentMediaItem
                val title = mediaItem?.mediaMetadata?.title?.toString() ?: "Not Playing"
                val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
                val isPlaying = controller.playWhenReady
                val artworkUri = mediaItem?.mediaMetadata?.artworkUri?.toString()
                
                Log.d("MediaWidget", "Worker state: title=$title, artist=$artist, isPlaying=$isPlaying, art=$artworkUri")

                val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(MediaWidget::class.java)
                glanceIds.forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[MediaWidget.TitleKey] = title
                        prefs[MediaWidget.ArtistKey] = artist
                        prefs[MediaWidget.IsPlayingKey] = isPlaying
                        if (!artworkUri.isNullOrBlank()) {
                            prefs[MediaWidget.ArtworkUriKey] = artworkUri
                        } else {
                            prefs.remove(MediaWidget.ArtworkUriKey)
                        }
                    }
                    MediaWidget().update(context, glanceId)
                }
                
                MediaController.releaseFuture(controllerFuture)
                Result.success()
            } catch (e: Exception) {
                Log.e("MediaWidget", "Worker failed: ${e.message}", e)
                MediaController.releaseFuture(controllerFuture)
                Result.failure()
            }
        }
    }
}
