package com.equalizer.carservice

import android.os.Bundle
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@UnstableApi
class BandDetailScreen(
    carContext: CarContext,
    private val playControl: PlayControl,
    private val bandIndex: Int
) : Screen(carContext) {

    init {
        playControl.setVolPerFreqSetter0 {
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val bandName = playControl.frequencyLabels[bandIndex]
        val currentVol = playControl.volPerFreq[bandIndex]

        val paneBuilder = Pane.Builder()
        
        val statusRow = Row.Builder()
            .setTitle(bandName)
            .addText("Current Volume: ${String.format(Locale.GERMAN, "%.1f", currentVol)}x")
            .build()
        
        paneBuilder.addRow(statusRow)

        val actionPlus = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.audio_plus)).build())
            .setEnabled(currentVol < 2.0f)
            .setOnClickListener {
                val newVal = min(2.0f, playControl.volPerFreq[bandIndex] + 0.1f)
                updateFrequency(newVal)
            }
            .build()

        val actionMinus = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.audio_minus)).build())
            .setEnabled(currentVol > 0.0f)
            .setOnClickListener {
                val newVal = max(0.0f, playControl.volPerFreq[bandIndex] - 0.1f)
                updateFrequency(newVal)
            }
            .build()

        paneBuilder.addAction(actionMinus)
        paneBuilder.addAction(actionPlus)

        val builder = PaneTemplate.Builder(paneBuilder.build())
        
        if (carContext.carAppApiLevel >= 5) {
            builder.setHeader(
                Header.Builder()
                    .setTitle("Adjust Band")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        } else {
            try {
                builder.setTitle("Adjust Band")
                builder.setHeaderAction(Action.BACK)
            } catch (_: Exception) {}
        }

        return builder.build()
    }

    private fun updateFrequency(volume: Float) {
        playControl.volPerFreq[bandIndex] = volume
        invalidate()

        val extras = Bundle().apply {
            putInt("KEY_INDEX", bandIndex)
            putFloat("KEY_VOLUME", volume)
        }
        val customCommand = SessionCommand("setVolOnFreq", Bundle())
        if (playControl.mediaControllerFuture.isDone) {
            playControl.controller.sendCustomCommand(customCommand, extras)
        }
    }
}
