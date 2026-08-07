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
import org.jetbrains.letsPlot.label.xlab
import org.jetbrains.letsPlot.label.ylab
import org.jetbrains.letsPlot.scale.ylim
import org.jetbrains.letsPlot.compose.PlotPanel
import org.jetbrains.letsPlot.themes.theme
import com.equalizer.common.Equalizer
import org.jetbrains.letsPlot.coord.coordFixed
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max

@UnstableApi
@Composable
fun AnalysisPanel(modifier: Modifier = Modifier,
                  equalizerViewModel: AudioModel) {
    val filterDesign by equalizerViewModel.filterDesign.observeAsState()
    val magnitudeResponse by equalizerViewModel.magnitudeResponse.observeAsState()
    val unoptimizedMagnitude by equalizerViewModel.unoptimizedMagnitudeResponse.observeAsState()
    val phaseResponse by equalizerViewModel.phaseResponse.observeAsState()
    val unoptimizedPhase by equalizerViewModel.unoptimizedPhaseResponse.observeAsState()
    val isDbScale by equalizerViewModel.isDbScale.observeAsState(false)

    LaunchedEffect(Unit) {
        equalizerViewModel.updateFilterDesign()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Magnitude Response",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("dB Scale", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = isDbScale,
                    onCheckedChange = { equalizerViewModel.setDbScale(it) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        magnitudeResponse?.let { response ->
            MagnitudeResponsePlot(response, unoptimizedMagnitude, isDbScale)
        } ?: Box(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading magnitude response...", color = Color.Gray)
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Phase Response",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(Modifier.height(16.dp))

        phaseResponse?.let { response ->
            PhaseResponsePlot(response, unoptimizedPhase)

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Group Delay",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(Modifier.height(16.dp))

            GroupDelayPlot(response, unoptimizedPhase)
        } ?: Box(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading phase response...", color = Color.Gray)
        }

        Spacer(Modifier.height(48.dp))
    }
}

@UnstableApi
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

            val range = if (delayUnit == AudioModel.DelayUnit.MS) 0f..5f else 0f..1000f
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
    }
}

@Composable
fun MagnitudeResponsePlot(response: FloatArray, unoptimizedResponse: FloatArray?, isDbScale: Boolean) {
    val xList = (0..4000 step 10).map { it.toFloat() }

    val combinedX = mutableListOf<Float>()
    val combinedY = mutableListOf<Float>()
    val typeList = mutableListOf<String>()

    fun transform(v: Float): Float {
        return if (isDbScale) {
            (20.0 * log10(max(v.toDouble(), 1e-12))).toFloat()
        } else {
            v
        }
    }

    // Unoptimized data first (so it's drawn behind)
    unoptimizedResponse?.forEachIndexed { idx, value ->
        if (idx < xList.size) {
            combinedX.add(xList[idx])
            combinedY.add(transform(value))
            typeList.add("Unoptimized")
        }
    }

    // Optimized data second (so it's drawn on top)
    response.forEachIndexed { idx, value ->
        if (idx < xList.size) {
            combinedX.add(xList[idx])
            combinedY.add(transform(value))
            typeList.add("Optimized")
        }
    }

    val data = mapOf(
        "Freq" to combinedX,
        "Mag" to combinedY,
        "Type" to typeList
    )

    val yLabel = if (isDbScale) "Gain (dB)" else "Magnitude"
    val plotTitle = if (isDbScale) "Magnitude Response (dB)" else "Magnitude Response (Raw)"

    val yLimits = if (isDbScale) -30.0 to 10.0 else 0.0 to 2.0

    val plot = letsPlot(data) +
            geomPath {
                this.x = "Freq"
                this.y = "Mag"
                this.color = "Type"
            } +
            scaleColorManual(values = mapOf("Optimized" to "#377EB8", "Unoptimized" to "#E41A1C")) + 
            ggtitle(plotTitle) +
            xlab("Frequency (Hz)") +
            ylab(yLabel) +
            ylim(listOf(yLimits.first, yLimits.second)) +
            theme().legendPositionBottom()

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

fun calculateGroupDelay(phaseResponse: FloatArray): List<Float> {
    if (phaseResponse.size < 2) return emptyList()
    
    val deltaF = 10.0 // Hz
    val deltaOmega = 2.0 * PI * deltaF
    
    val delay = mutableListOf<Float>()
    for (i in 0 until phaseResponse.size - 1) {
        val p1 = phaseResponse[i]
        val p2 = phaseResponse[i + 1]
        
        // Phase unwrapping logic: "if the left value is negative and right value is positive, then add 2*pi to the left value"
        val unwrappedP1 = if (p1 < 0f && p2 > 0f) p1 + (2.0 * PI).toFloat() else p1
        
        val dPhi = p2 - unwrappedP1
        // tau_g = - dPhi / dOmega (flipped sign as requested)
        val groupDelayS = -dPhi / deltaOmega
        delay.add((groupDelayS * 1000.0).toFloat()) // Convert to ms
    }
    // Duplicate last point to maintain list size matching xList
    if (delay.isNotEmpty()) delay.add(delay.last())
    
    return delay
}

@Composable
fun GroupDelayPlot(response: FloatArray, unoptimizedResponse: FloatArray?) {
    val xList = (0..4000 step 10).map { it.toFloat() }

    val combinedX = mutableListOf<Float>()
    val combinedY = mutableListOf<Float>()
    val typeList = mutableListOf<String>()

    // Unoptimized data first
    unoptimizedResponse?.let {
        val unoptDelay = calculateGroupDelay(it)
        unoptDelay.forEachIndexed { idx, value ->
            if (idx < xList.size) {
                combinedX.add(xList[idx])
                combinedY.add(value)
                typeList.add("Unoptimized")
            }
        }
    }

    // Optimized data second
    val optDelay = calculateGroupDelay(response)
    optDelay.forEachIndexed { idx, value ->
        if (idx < xList.size) {
            combinedX.add(xList[idx])
            combinedY.add(value)
            typeList.add("Optimized")
        }
    }

    val data = mapOf(
        "Freq" to combinedX,
        "Delay" to combinedY,
        "Type" to typeList
    )

    val plot = letsPlot(data) +
            geomPath {
                this.x = "Freq"
                this.y = "Delay"
                this.color = "Type"
            } +
            scaleColorManual(values = mapOf("Optimized" to "#377EB8", "Unoptimized" to "#E41A1C")) +
            ggtitle("Group Delay (ms)") +
            xlab("Frequency (Hz)") +
            ylab("Delay (ms)") +
            theme().legendPositionBottom()

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

@Composable
fun PhaseResponsePlot(response: FloatArray, unoptimizedResponse: FloatArray?) {
    val xList = (0..4000 step 10).map { it.toFloat() }

    val combinedX = mutableListOf<Float>()
    val combinedY = mutableListOf<Float>()
    val typeList = mutableListOf<String>()

    // Unoptimized data first
    unoptimizedResponse?.forEachIndexed { idx, value ->
        if (idx < xList.size) {
            combinedX.add(xList[idx])
            combinedY.add(value)
            typeList.add("Unoptimized")
        }
    }

    // Optimized data second
    response.forEachIndexed { idx, value ->
        if (idx < xList.size) {
            combinedX.add(xList[idx])
            combinedY.add(value)
            typeList.add("Optimized")
        }
    }

    val data = mapOf(
        "Freq" to combinedX,
        "Phase" to combinedY,
        "Type" to typeList
    )

    val plot = letsPlot(data) +
            geomPath {
                this.x = "Freq"
                this.y = "Phase"
                this.color = "Type"
            } +
            scaleColorManual(values = mapOf("Optimized" to "#377EB8", "Unoptimized" to "#E41A1C")) +
            ggtitle("Phase Response (Radians)") +
            xlab("Frequency (Hz)") +
            ylab("Phase (rad)") +
            ylim(listOf(-PI, PI)) +
            theme().legendPositionBottom()

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
            coordFixed() +
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
