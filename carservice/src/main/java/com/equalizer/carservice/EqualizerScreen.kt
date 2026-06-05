package com.equalizer.carservice

import android.os.Bundle
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
//import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Item
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.session.SessionCommand
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
class MyManager: CarHardwareManager {
    override fun on

}*/

class EqualizerScreen(
    carContext: CarContext,
    val playControl: PlayControl
): Screen(carContext) {


    /*     val player0 = ExoPlayer
            .Builder(carContext)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player0.setAudioAttributes(audioAttributes, true)
        val mediaItem = MediaItem.Builder()
            .setUri( Uri.parse("/storage/emulated/0/Music/snothvalp.mp3"))
            .build()
        player0.playWhenReady=true
        player0.setMediaItem(mediaItem)
        player0.prepare()
        player0.play()*/


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

    private val actionPlus = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(IconCompat
                .createWithResource(carContext,R.mipmap.audio_plus)
                .setTint(CarColor.TYPE_RED))
            .setTint(CarColor.RED)
            .build()
        )

        .setEnabled(playControl.volPerFreq[currentInterval]<2.0f)
        .setOnClickListener {
            log("plus clicked")
            val volumeInDb = min(2.0f,playControl.volPerFreq[currentInterval]+0.1f)

            setVolPerFreqText(currentInterval)

            val extras = Bundle().apply {
                putInt("KEY_INDEX", currentInterval)
                putFloat("KEY_VOLUME", volumeInDb)
            }
            val customCommand = SessionCommand("setVolOnFreq", Bundle())

            playControl.controller.sendCustomCommand(customCommand, extras)
        }
        .build()

    private val actionMinus = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(IconCompat.createWithResource(carContext,R.mipmap.audio_minus))
            .setTint(CarColor.RED)
            .build()
        )
        .setOnClickListener {
            val volumeInDb = max(0.0f,playControl.volPerFreq[currentInterval]-0.1f)
            setVolPerFreqText(currentInterval)

            val extras = Bundle().apply {
                putInt("KEY_INDEX", currentInterval)
                putFloat("KEY_VOLUME", volumeInDb)
            }

            val customCommand = SessionCommand("setVolOnFreq", Bundle())

            playControl.controller.sendCustomCommand(customCommand, extras)

            invalidate()
        }
        .setEnabled(playControl.volPerFreq[currentInterval]>0.0f)
        .build()
/*
    private val actionStrip = ActionStrip
        .Builder()
        .addAction(actionMinus)
        .addAction(actionPlus)
        .build()*/

    init {
        playControl.setVolPerFreqSetter0 {
            setVolPerFreqText(it)
        }
    }

    override fun onGetTemplate(): Template {

        playControl.setInvalidate0 {
            invalidate()
        }



        val singleListBuilder = ItemList.Builder()
            .setSelectedIndex(currentInterval)
            .setOnSelectedListener {
                log("item selected: $it")
                currentInterval=it
            }.apply {
                intervalItems.forEach { this.addItem(it) }
            }



        //val itemList = ItemList.Builder().addItem(row).addItem(row).addItem(row).build()
        val singleList = singleListBuilder.build()

        // 1. Create the Header object
        val header = Header.Builder()
            .setStartHeaderAction(Action.BACK) // Replaces setHeaderAction
            .setTitle("Equalizer")             // Replaces setTitle on the Template
            .addEndHeaderAction(actionMinus)
            .addEndHeaderAction(actionPlus)
            .build()



        // 2. Build the template using setHeader
        return ListTemplate.Builder()
            .setHeader(header)                 // Pass the new header object here
            //.setActionStrip(actionStrip)
            .setSingleList(singleList)
            .build()

        /*
        return PaneTemplate
            .Builder(
                Pane
                    .Builder()
                    .addRow(row2)
                    .addRow(row2)
                    .addRow(row2)
                    .build())
            .setTitle("Equalizer")
            .setActionStrip(actionStrip)
            .setActionStrip(actionStrip)

            .build()*/
    }
}

