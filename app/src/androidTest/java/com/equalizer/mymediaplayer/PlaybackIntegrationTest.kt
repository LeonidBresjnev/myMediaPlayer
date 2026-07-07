package com.equalizer.mymediaplayer

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.equalizer.common.MyMediaService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackIntegrationTest {

    private lateinit var context: Context
    private lateinit var testFile: File
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private lateinit var controller: MediaController

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // 1. Copy pladder.mp3 to a temporary file
        testFile = File(context.cacheDir, "pladder_test.mp3")
        context.resources.openRawResource(R.raw.pladder).use { input ->
            FileOutputStream(testFile).use { output ->
                input.copyTo(output)
            }
        }

        // 2. Connect MediaController
        val sessionToken = SessionToken(context, ComponentName(context, MyMediaService::class.java))
        
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture.addListener({
                controller = controllerFuture.get()
                latch.countDown()
            }, MoreExecutors.directExecutor())
        }
        
        assertTrue("Controller connection timed out", latch.await(15, TimeUnit.SECONDS))
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (::controller.isInitialized) {
                controller.release()
            }
        }
        if (testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun testPlayPauseSeekForward() {
        val mediaItem = MediaItem.Builder()
            .setMediaId(testFile.absolutePath)
            .setUri(testFile.toUri())
            .build()

        val stateLatch = CountDownLatch(1)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    stateLatch.countDown()
                }
            }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.addListener(listener)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }

        assertTrue("Player never reached READY state", stateLatch.await(15, TimeUnit.SECONDS))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.pause()
        }
        
        Thread.sleep(1000)
        
        var posAfterPause: Long = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            posAfterPause = controller.currentPosition
            controller.seekForward()
        }

        Thread.sleep(1000)
        
        var posAfterSeek: Long = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            posAfterSeek = controller.currentPosition
        }

        val diffForward = Math.abs(posAfterSeek - (posAfterPause + 15000L))
        assertTrue("Seek forward failed: expected approx ${posAfterPause + 15000}ms, got $posAfterSeek ms (diff: $diffForward ms)", diffForward < 2000L)
    }

    @Test
    fun testSeekBack() {
        val mediaItem = MediaItem.Builder()
            .setMediaId(testFile.absolutePath)
            .setUri(testFile.toUri())
            .build()

        val stateLatch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) stateLatch.countDown()
                }
            })
            controller.setMediaItem(mediaItem)
            controller.prepare()
        }

        assertTrue("Player never reached READY state", stateLatch.await(15, TimeUnit.SECONDS))

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.seekTo(30000L)
        }
        
        Thread.sleep(1000)
        
        var posAfterManualSeek: Long = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            posAfterManualSeek = controller.currentPosition
            controller.seekBack()
        }
        
        val diffManual = Math.abs(posAfterManualSeek - 30000L)
        assertTrue("Manual seek to 30s failed: diff was $diffManual ms", diffManual < 1000L)

        Thread.sleep(1000)
        
        var posAfterSeekBack: Long = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            posAfterSeekBack = controller.currentPosition
        }
        
        val diffBack = Math.abs(posAfterSeekBack - 15000L)
        assertTrue("Seek back failed: expected approx 15000ms, got $posAfterSeekBack ms (diff: $diffBack ms)", diffBack < 2000L)
    }
}
