package com.equalizer.mymediaplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp


@Composable
fun ControlPanel(modifier: Modifier = Modifier,
                 equalizerViewModel: AudioModel) {


    val volumenLow = equalizerViewModel.volumenLow.observeAsState()

    val frequencyLabels = listOf(
        stringResource(R.string.sub_bass_0_125_hz),
        stringResource(R.string.bass_125_250_hz),
        stringResource(R.string.low_250_500_hz),
        stringResource(R.string.low_mids_500_hz_1_khz),
        stringResource(R.string.high_mids_1_khz_2_khz),
        stringResource(R.string.high_2_4_khz),
        stringResource(R.string.upper_highs_4_8_khz),
        stringResource(R.string.air_8_khz_and_above)
    )
    LazyColumn(modifier=modifier
        .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        items(frequencyLabels.size) { index ->
            VolumeControlContent(
                frequencyLabel=frequencyLabels[index],
                modifier = Modifier,
                volumenValueLabel = stringResource(
                    id=R.string.volumen_value, volumenLow.value!![index]),
                onValueChange = { v->equalizerViewModel.setVolumen(volumeInDb =  v,index=index) },
                volume = volumenLow.value!![index],
                volumeRange = equalizerViewModel.volumeRange
            )
        }
    }
}

@Composable
private fun VolumeControlContent(
    frequencyLabel: String,
    modifier: Modifier,
    volume: Float,
    volumeRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    volumenValueLabel: String
) {
    // The volume slider should take around 1/4 of the screen height
    val screenHeight = LocalConfiguration.current.screenWidthDp
    val sliderHeight = screenHeight * 0.8


    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        /*
        modifier=Modifier.border(BorderStroke(0.1.dp, Color.Black))*/
    ) {
        Text(text = frequencyLabel)
        Row(modifier = modifier) {
            Text(text = volumenValueLabel)
            Icon(

                imageVector = if (volume == 0f) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = null
            )


            Slider(
                value = volume,
                onValueChange = onValueChange,
                modifier = modifier
                    .fillMaxWidth(0.6f)
                    .width(sliderHeight.dp),
                valueRange = volumeRange
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null
            )
        }

    }


}