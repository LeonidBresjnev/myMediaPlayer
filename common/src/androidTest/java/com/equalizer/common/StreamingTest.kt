package com.equalizer.common
/*
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith*/
/*
@UnstableApi
@RunWith(AndroidJUnit4::class)
class StreamingTest {

    @Test
    fun testSWR3StreamingSamples() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var equalizer: Equalizer? = null
        
        instrumentation.runOnMainSync {
            equalizer = Equalizer(context = context)
        }
        
        // Deutschlandfunk stream
        val url = "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3"
        val mediaItem = MediaItem.Builder()
            .setMediaId(url)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle("DLF Test")
                .setTotalDiscCount(44100)
                .setReleaseMonth(2)
                .build())
            .build()
            
        instrumentation.runOnMainSync {
            equalizer?.setMediaItems(listOf(mediaItem), 0, 0L)
            equalizer?.playWhenReady = true
        }
        
        var totalNonZeroFound = 0
        var attempts = 0
        val maxAttempts = 60
        
        println("StreamingTest: Monitoring for audio activity...")
        
        while (totalNonZeroFound < 10 && attempts < maxAttempts) {
            Thread.sleep(1000)
            instrumentation.runOnMainSync {
                // Peek at samples being generated
                val samples = equalizer?.getDiagnosticSamples(2048)
                if (samples != null) {
                    var chunkNonZero = 0
                    for (s in samples) {
                        if (Math.abs(s) > 1e-5f) {
                            chunkNonZero++
                        }
                    }
                    if (chunkNonZero > 0) {
                        totalNonZeroFound += chunkNonZero
                        println("Wait attempt $attempts: Found $chunkNonZero non-zero samples in this peek. Total: $totalNonZeroFound")
                    }
                }
            }
            attempts++
        }
        
        println("Final Diagnostic Result: $totalNonZeroFound non-zero samples found.")
        assertTrue("No non-zero samples detected from stream after $maxAttempts seconds. Decoder might be stalled.", totalNonZeroFound > 0)
    }
}
*/