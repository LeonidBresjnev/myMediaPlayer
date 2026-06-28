package com.equalizer.carservice

import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Item
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import java.util.Locale
import kotlin.math.max
import kotlin.math.min


class EqualizerScreen
@OptIn(UnstableApi::class) constructor
    (
    carContext: CarContext,
    val playControl: PlayControl
): Screen(carContext) {

    private var currentInterval = 0

    private fun log(msg: String) {
        Log.d("Car Main Screen", msg)
    }

    private val intervalItems = MutableList<Item>(size = 8) {
        Row.Builder()
            .setTitle(playControl.frequencyLabels[it])
            .addText("volume: ${String.format(Locale.GERMAN, "%.1f", playControl.volPerFreq[it])}")
            .build()
    }

    private fun setVolPerFreqText(activeRow: Int) {
        log("setVolPerFreqText: $activeRow")
        intervalItems[activeRow] = Row.Builder()
            .setTitle(playControl.frequencyLabels[activeRow])
            .addText("volume: ${String.format(Locale.GERMAN,"%.1f",playControl.volPerFreq[activeRow])}")
            .build()
        invalidate()
    }

    init {
        playControl.setVolPerFreqSetter0 {
            setVolPerFreqText(it)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onGetTemplate(): Template {
        playControl.setInvalidate0 {
            invalidate()
        }

        val actionPlus = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.audio_plus)).build())
            .setEnabled(playControl.volPerFreq[currentInterval] < 2.0f)
            .setOnClickListener {
                log("plus clicked")
                val volumeInDb = min(2.0f, playControl.volPerFreq[currentInterval] + 0.1f)
                
                // Update local state and UI
                playControl.volPerFreq[currentInterval] = volumeInDb
                setVolPerFreqText(currentInterval)

                val extras = Bundle().apply {
                    putInt("KEY_INDEX", currentInterval)
                    putFloat("KEY_VOLUME", volumeInDb)
                }
                val customCommand = SessionCommand("setVolOnFreq", Bundle())
                playControl.controller.sendCustomCommand(customCommand, extras)
            }
            .build()

        val actionMinus = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.mipmap.audio_minus)).build())
            .setEnabled(playControl.volPerFreq[currentInterval] > 0.0f)
            .setOnClickListener {
                log("minus clicked")
                val volumeInDb = max(0.0f, playControl.volPerFreq[currentInterval] - 0.1f)
                
                // Update local state and UI
                playControl.volPerFreq[currentInterval] = volumeInDb
                setVolPerFreqText(currentInterval)

                val extras = Bundle().apply {
                    putInt("KEY_INDEX", currentInterval)
                    putFloat("KEY_VOLUME", volumeInDb)
                }
                val customCommand = SessionCommand("setVolOnFreq", Bundle())
                playControl.controller.sendCustomCommand(customCommand, extras)
            }
            .build()

        val singleList = ItemList.Builder()
            .setSelectedIndex(currentInterval)
            .setOnSelectedListener {
                log("item selected: $it")
                currentInterval = it
                invalidate() // Invalidate to update action button enabled states
            }.apply {
                intervalItems.forEach { addItem(it) }
            }
            .build()

        val header = Header.Builder()
            .setStartHeaderAction(Action.BACK)
            .setTitle("Equalizer")
            .addEndHeaderAction(actionMinus)
            .addEndHeaderAction(actionPlus)
            .build()

        return ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(singleList)
            .build()
    }
}
