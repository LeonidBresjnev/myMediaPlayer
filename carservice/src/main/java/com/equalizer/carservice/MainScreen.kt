package com.equalizer.carservice

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Item
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat.getString
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/*
class MyManager: CarHardwareManager {
    override fun on

}*/
class MainScreen(
    carContext: CarContext
): Screen(carContext) {

    val viewModelStoreOwner = getViewModelStoreOwner() // Voila!

    private val viewModel: MyViewModel by viewModel<MyViewModel>()
    init {
        lifecycleScope.launch {
            viewModel.activeInterval.collect {
                invalidate()
            }
        }
    }
    private val frequencyLabels = listOf(
        getString(carContext,R.string.sub_bass_0_125_hz) ,
        getString(carContext,R.string.bass_125_250_hz),
        getString(carContext,R.string.low_250_500_hz),
        getString(carContext,R.string.low_mids_500_hz_1_khz),
        getString(carContext,R.string.high_mids_1_khz_2_khz),
        getString(carContext,R.string.high_2_4_khz),
        getString(carContext,R.string.upper_highs_4_8_khz),
        getString(carContext,R.string.air_8_khz_and_above)
    )

    private fun log(msg: String) {
        Log.d("Car Main Screen", msg)
    }


private val volPerFreq= MutableList(8) { it*1.0f}

    private val intervalItems =         MutableList<Item>(size=8) {
        //GridItem.Builder().setTitle("Row title $it")
        val nonActive = Action
            .Builder()
            .setIcon(CarIcon.COMPOSE_MESSAGE)
            .setOnClickListener {
                updateIntervalitem(it)
            }
            .build()

        Row.Builder()
            .setTitle(frequencyLabels[it])
            .addText("volume: ${volPerFreq[it]}")
            .addAction(nonActive)
            /* .addAction(actionPlus)*/
            .build()
    }

    private fun updateIntervalitem(activeRow: Int) {
        if (viewModel.activeInterval.value>-1) {
            val k=viewModel.activeInterval.value
            val nonActive = Action
                .Builder()
                .setIcon(CarIcon.COMPOSE_MESSAGE)
                .setOnClickListener {
                    updateIntervalitem(k)
                }
                .build()

            intervalItems[viewModel.activeInterval.value] = Row.Builder()
                .setTitle(frequencyLabels[viewModel.activeInterval.value])

                .addText("volume: ${volPerFreq[k]}")
                .addAction(nonActive)
                /* .addAction(actionPlus)*/
                .build()
        }

        if (viewModel.activeInterval.value != activeRow) {
            val active = Action
                .Builder()
                .setIcon(CarIcon.Builder(
                        IconCompat.createWithResource(
                            carContext,
                            R.mipmap.ic_launcher
                        )
                    )
                    .build()
                )
                .setOnClickListener {
                    updateIntervalitem(activeRow)
                }
                .build()

            intervalItems[activeRow] = Row.Builder()
                .setTitle(frequencyLabels[activeRow])
                .addText("volume: ${volPerFreq[activeRow]}")
                .addAction(active)
                .build()

            viewModel.updateState(activeRow)
        }

        else {
            viewModel.updateState(-1)
        }
    }

    private fun setVolPerFreqText(activeRow: Int) {

        val active = Action
            .Builder()
            .setIcon(CarIcon.Builder(
                IconCompat.createWithResource(
                    carContext,
                    R.mipmap.ic_launcher
                )
            )
                .build()
            )
            .setOnClickListener {
                updateIntervalitem(activeRow)
            }
            .build()

        intervalItems[activeRow] = Row.Builder()
            .setTitle(frequencyLabels[activeRow])
            .addText("volume: ${volPerFreq[activeRow]}")
            .addAction(active)
            .build()

        viewModel.updateState(activeRow)
    }

    private val playPause = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(IconCompat.createWithResource(carContext,R.drawable.play_solid))
            .build())
        .setOnClickListener {
            CarToast
                .makeText(carContext, "play",CarToast.LENGTH_SHORT)
                .show()
        }
        .setBackgroundColor(CarColor.RED)
        .build()

    private val actionPlus = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(IconCompat.createWithResource(carContext,R.mipmap.ic_launcher))
            .build()
        )
        .setEnabled(viewModel.activeInterval.value>-1)
        .setOnClickListener {
            volPerFreq[viewModel.activeInterval.value] = min(2.0f,volPerFreq[viewModel.activeInterval.value]+0.1f)
            setVolPerFreqText(viewModel.activeInterval.value)
            invalidate()
        }
        .build()

    private val actionMinus = Action
        .Builder()
        .setIcon(CarIcon
            .Builder(IconCompat.createWithResource(carContext,R.mipmap.ic_launcher))
            .build()
        )
        .setOnClickListener {
            volPerFreq[viewModel.activeInterval.value] = max(0.0f,volPerFreq[viewModel.activeInterval.value]-0.1f)
            setVolPerFreqText(viewModel.activeInterval.value)
            invalidate()
        }
        .setEnabled(viewModel.activeInterval.value>-1)
        .build()

    private val actionStrip = ActionStrip
        .Builder()
        .addAction(actionPlus)
        .addAction(actionMinus)
        .build()

    override fun onGetTemplate(): Template {

        //val x= CarAppApiLevels.getLatest()

      //  val myCarSensors = CarSensors(carContext)

      //  val rawSpeed = CarValue<Float>(100f,1L, CarValue.STATUS_SUCCESS)
      //  val mySpeed = Speed.Builder().setRawSpeedMetersPerSecond(rawSpeed).build()
       /* val rpm = CarHardwareManager.create(
            carContext,
            HostDispatcher()
        )*/
/*
        val myAction = Action.Builder()
            .setIcon(
                CarIcon.Builder(
                IconCompat
                    .createWithResource(carContext,R.mipmap.ic_launcher_round)
            )
                .setTint(CarColor.RED)
                .build()
            ).setTitle("Snothvalp")
            .setOnClickListener {
                CarToast.makeText(
                    carContext, "you clicked me!", CarToast.LENGTH_LONG
                ).show()
            }.build()
*/

        //val plus = CarText.Builder("+").addVariant("plus").build()
        //val plusIcon = CarIcon.Builder(IconCompat())


/*
        val row = Row.Builder()
            .setTitle("Row title")
            .addText("title text")
            .addAction(myAction)
            .build()*/

        val singleListBuilder = ItemList.Builder()



        intervalItems.forEach { singleListBuilder.addItem(it) }

        //val itemList = ItemList.Builder().addItem(row).addItem(row).addItem(row).build()
        val singleList = singleListBuilder.build()




        return ListTemplate.Builder()
            .setTitle("My title")
            .setActionStrip(actionStrip)
            .setSingleList(singleList)
            /*.addSectionedList(sectionedItemList)*/
            .addAction(playPause)
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

