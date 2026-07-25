package com.equalizer.mymediaplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import org.jetbrains.letsPlot.letsPlot
import org.jetbrains.letsPlot.geom.geomPoint
import org.jetbrains.letsPlot.geom.geomPath
import org.jetbrains.letsPlot.scale.scaleColorManual
import org.jetbrains.letsPlot.label.ggtitle
import org.jetbrains.letsPlot.compose.PlotPanel
import com.equalizer.common.Equalizer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

@UnstableApi
@OptIn(UnstableApi::class)
@Composable
fun ControlPanel(modifier: Modifier = Modifier,
                 equalizerViewModel: AudioModel) {

    val volumenLow by equalizerViewModel.volumenLow.observeAsState(List(16) { 1.0f })
    val selectedPreset by equalizerViewModel.selectedPreset.observeAsState("Flat")
    val isAdvancedMode by equalizerViewModel.isAdvancedMode.observeAsState(false)
    val leftDelayRaw by equalizerViewModel.leftDelayRaw.observeAsState(0.0f)
    val rightDelayRaw by equalizerViewModel.rightDelayRaw.observeAsState(0.0f)
    val isPlaying by equalizerViewModel.isPlaying.observeAsState(AudioModel.Status.STOPPED)
    val delayUnit by equalizerViewModel.delayUnit.observeAsState(AudioModel.DelayUnit.MS)
    
    val isDelayEnabled = isPlaying != AudioModel.Status.PLAYING
    
    var selectedChannelTab by remember { mutableIntStateOf(0) } // 0 for Left, 1 for Right

    val frequencyLabels = listOf(
        "Sub Bass", "Bass", "Low", "Low Mids", "High Mids", "High", "Upper", "Air"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- EQUALIZER SECTION ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Advanced",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Switch(
                    checked = isAdvancedMode,
                    onCheckedChange = { equalizerViewModel.setAdvancedMode(it) },
                    modifier = Modifier.scale(0.8f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { equalizerViewModel.resetEqualizer() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // CHANNEL SELECTOR (Only in Advanced Mode)
        if (isAdvancedMode) {
            SecondaryTabRow(
                selectedTabIndex = selectedChannelTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = selectedChannelTab == 0,
                    onClick = { selectedChannelTab = 0 },
                    text = { Text("Left Channel") }
                )
                Tab(
                    selected = selectedChannelTab == 1,
                    onClick = { selectedChannelTab = 1 },
                    text = { Text("Right Channel") }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // PRESETS SECTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Start
            ) {
                equalizerViewModel.presets.keys.forEach { presetName ->
                    PresetRadioButton(
                        name = presetName,
                        isSelected = selectedPreset == presetName,
                        onClick = { equalizerViewModel.applyPreset(presetName) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // SLIDERS SECTION
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val startIndex = if (selectedChannelTab == 1 && isAdvancedMode) 8 else 0
                for (i in 0 until 8) {
                    val index = startIndex + i
                    val volume = volumenLow.getOrElse(index) { 1.0f }
                    EqualizerBand(
                        label = frequencyLabels[i],
                        volume = volume,
                        range = equalizerViewModel.volumeRange,
                        onValueChange = { v -> 
                            equalizerViewModel.setVolumen(v, index)
                        }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = if (isAdvancedMode) "Advanced Mode: Independent L/R" else if (selectedPreset == "Custom") "Manual mode active" else "Profile: $selectedPreset",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
        Spacer(Modifier.height(24.dp))

        // --- TIME DELAY SECTION ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (isDelayEnabled) 1f else 0.5f },
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Time Delay",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isDelayEnabled) "Adjust speaker delay" else "Stop playback to adjust delay",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                // Unit Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    UnitToggleButton("ms", delayUnit == AudioModel.DelayUnit.MS, isDelayEnabled) {
                        equalizerViewModel.setDelayUnit(AudioModel.DelayUnit.MS)
                    }
                    UnitToggleButton("cm", delayUnit == AudioModel.DelayUnit.CM, isDelayEnabled) {
                        equalizerViewModel.setDelayUnit(AudioModel.DelayUnit.CM)
                    }
                }
            }

            val range = if (delayUnit == AudioModel.DelayUnit.MS) 0f..100f else 0f..1000f
            val unitLabel = if (delayUnit == AudioModel.DelayUnit.MS) "ms" else "cm"

            Spacer(Modifier.height(24.dp))

            // Left Delay
            DelayControlRow(
                label = "Left Speaker",
                value = leftDelayRaw,
                range = range,
                unitLabel = unitLabel,
                enabled = isDelayEnabled,
                onValueChange = { equalizerViewModel.setLeftDelay(it) }
            )

            Spacer(Modifier.height(16.dp))

            // Right Delay
            DelayControlRow(
                label = "Right Speaker",
                value = rightDelayRaw,
                range = range,
                unitLabel = unitLabel,
                enabled = isDelayEnabled,
                onValueChange = { equalizerViewModel.setRightDelay(it) }
            )
        }

        Spacer(Modifier.height(48.dp))

        // --- FILTER DESIGN PLOT SECTION ---
        val filterDesign by equalizerViewModel.filterDesign.observeAsState()
        
        LaunchedEffect(isPlaying) {
            equalizerViewModel.updateFilterDesign()
        }

        Text(
            text = "Filter Pole-Zero Map",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        
        Spacer(Modifier.height(16.dp))
        
        filterDesign?.let { design ->
            FilterDesignPlot(design)
        } ?: Box(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading filter design...", color = Color.Gray)
        }

        Spacer(Modifier.height(32.dp))

        val magnitudeResponse by equalizerViewModel.magnitudeResponse.observeAsState()

        Text(
            text = "Magnitude Response",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(Modifier.height(16.dp))

        magnitudeResponse?.let { response ->
            MagnitudeResponsePlot(response)
        } ?: Box(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading magnitude response...", color = Color.Gray)
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun MagnitudeResponsePlot(response: FloatArray) {
    val xList = (0..1000 step 10).map { it.toFloat() }
    val yList = response.toList()

    val data = mapOf(
        "Frequency (Hz)" to xList,
        "Magnitude" to yList
    )

    val plot = letsPlot(data) +
            geomPath {
                this.x = "Frequency (Hz)"
                this.y = "Magnitude"
            } +
            ggtitle("Filter Magnitude Response (0-1000 Hz)")

    PlotPanel(
        figure = plot,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
        computationMessagesHandler = { }
    )
}

@UnstableApi
@OptIn(UnstableApi::class)
@Composable
fun FilterDesignPlot(design: Equalizer.FilterDesignData) {
    val bandColors = listOf(
        "#E41A1C", "#377EB8", "#4DAF4A", "#984EA3",
        "#FF7F00", "#FFFF33", "#A65628", "#F781BF"
    )

    val xList = mutableListOf<Float>()
    val yList = mutableListOf<Float>()
    val bandList = mutableListOf<String>()
    val typeList = mutableListOf<String>()

    design.bands.forEachIndexed { bandIdx, bandDesign ->
        val bandName = "Band ${bandIdx + 1}"
        bandDesign.poles.forEach { p ->
            xList.add(p.re)
            yList.add(p.im)
            bandList.add(bandName)
            typeList.add("Pole")
        }
        bandDesign.zeros.forEach { z ->
            xList.add(z.re)
            yList.add(z.im)
            bandList.add(bandName)
            typeList.add("Zero")
        }
    }

    val data = mapOf(
        "x" to xList,
        "y" to yList,
        "band" to bandList,
        "type" to typeList
    )

    // Unit circle data
    val circleX = (0..100).map { cos(2 * PI * it / 100).toFloat() }
    val circleY = (0..100).map { sin(2 * PI * it / 100).toFloat() }
    val circleData = mapOf("cx" to circleX, "cy" to circleY)

    val plot = letsPlot(data) +
            geomPath(data = circleData) { this.x = "cx"; this.y = "cy" } + // Unit circle
            geomPoint(size = 4.0) {
                this.x = "x"
                this.y = "y"
                this.color = "band"
                this.shape = "type"
            } +
            scaleColorManual(values = bandColors) +
            ggtitle("8-Band Parametric EQ Design")

    PlotPanel(
        figure = plot,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
        computationMessagesHandler = { }
    )
}

@Composable
fun DelayControlRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unitLabel: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = String.format(java.util.Locale.US, "%.1f %s", value, unitLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun UnitToggleButton(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun PresetRadioButton(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(end = 12.dp)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null, // Handled by selectable
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EqualizerBand(
    label: String,
    volume: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(72.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Value indicator at top
        Text(
            text = String.format(locale, "%.1fx", volume),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        // Vertical Slider container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = volume,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxHeight,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(-placeable.width, 0)
                        }
                    }
                    .width(220.dp) // Visual height of the vertical slider
                    .height(48.dp) // Visual width of the vertical slider
            )
        }

        // Frequency Label at bottom
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
