package com.example.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MusicViewModel
import com.example.ui.ScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerView(
    viewModel: MusicViewModel,
    colors: ColorPalette,
    modifier: Modifier = Modifier
) {
    val eqState by viewModel.equalizerState.collectAsState()
    val activePreset by viewModel.activeEqPreset.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Audio FX",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(ScreenState.NOW_PLAYING) },
                        modifier = Modifier.testTag("eq_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            if (viewModel.activeSong.collectAsState().value != null) {
                Spacer(modifier = Modifier.height(96.dp).navigationBarsPadding())
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Master toggle card (looks exactly like Screenshot 4)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(colors.selectedBackground)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Equalizer",
                    color = colors.accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = { viewModel.toggleEqualizer(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface
                    ),
                    modifier = Modifier.testTag("equalizer_toggle_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Disable overlay if EQ is off
            val alpha = if (eqState.isEnabled) 1.0f else 0.4f
            
            var presetMenuExpanded by remember { mutableStateOf(false) }
            val presets = listOf("Balanced", "Hall Room", "Rock", "Classical", "Pop", "Jazz", "Bass Boost", "Vocal Boost", "Acoustic", "Electronic", "Custom")

            // Presets Dropdown trigger
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).clickable(enabled = eqState.isEnabled) { presetMenuExpanded = true }.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().alpha(alpha) ) {
                    Text("Preset: $activePreset", color = colors.textPrimary, fontSize = 16.sp, modifier = Modifier.padding(start = 2.dp), fontWeight = FontWeight.Medium)
                    Icon(Icons.Default.Tune, contentDescription = "Presets", tint = colors.accent)
                }
            }

            if (presetMenuExpanded) {
                AlertDialog(
                    onDismissRequest = { presetMenuExpanded = false },
                    title = {
                        Text(
                            text = "Audio FX Presets",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    text = {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        ) {
                            items(presets) { preset ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (activePreset == preset) colors.selectedBackground else Color.Transparent)
                                        .clickable {
                                            viewModel.applyEqualizerPreset(preset)
                                            presetMenuExpanded = false
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = preset,
                                        color = if (activePreset == preset) colors.accent else colors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = if (activePreset == preset) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { presetMenuExpanded = false }) {
                            Text("Close", color = colors.accent)
                        }
                    },
                    containerColor = colors.surface,
                    shape = RoundedCornerShape(24.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 60Hz Slider Band
                EqSliderRow(
                    label = "60Hz",
                    valueDb = eqState.band60Hz,
                    isEnabled = eqState.isEnabled,
                    alpha = alpha,
                    colors = colors,
                    onValueChange = { viewModel.updateEqualizerBand("60Hz", it) }
                )

                // 230Hz Slider Band
                EqSliderRow(
                    label = "230Hz",
                    valueDb = eqState.band230Hz,
                    isEnabled = eqState.isEnabled,
                    alpha = alpha,
                    colors = colors,
                    onValueChange = { viewModel.updateEqualizerBand("230Hz", it) }
                )

                // 910Hz Slider Band
                EqSliderRow(
                    label = "910Hz",
                    valueDb = eqState.band910Hz,
                    isEnabled = eqState.isEnabled,
                    alpha = alpha,
                    colors = colors,
                    onValueChange = { viewModel.updateEqualizerBand("910Hz", it) }
                )

                // 3600Hz Slider Band
                EqSliderRow(
                    label = "3600Hz",
                    valueDb = eqState.band3600Hz,
                    isEnabled = eqState.isEnabled,
                    alpha = alpha,
                    colors = colors,
                    onValueChange = { viewModel.updateEqualizerBand("3600Hz", it) }
                )

                // 14000Hz Slider Band
                EqSliderRow(
                    label = "14000Hz",
                    valueDb = eqState.band14000Hz,
                    isEnabled = eqState.isEnabled,
                    alpha = alpha,
                    colors = colors,
                    onValueChange = { viewModel.updateEqualizerBand("14000Hz", it) }
                )

                // Reset button (aligned on the right like screenshot 4)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Button(
                        onClick = { viewModel.resetEqualizer() },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.selectedBackground),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("reset_equalizer_button")
                    ) {
                        Text(
                            text = "Reset",
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bass Boost section at the very bottom
            Divider(color = colors.surface, thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Bass boost",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Boosts lower frequencies",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = eqState.bassBoostEnabled,
                    onCheckedChange = { viewModel.toggleBassBoost(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent
                    ),
                    modifier = Modifier.testTag("bass_boost_switch")
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // External Equalizer Option
            Button(
                onClick = { viewModel.launchExternalEqualizer() },
                colors = ButtonDefaults.buttonColors(containerColor = colors.selectedBackground),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("external_equalizer_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "External Equalizer",
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Launch System Equalizer",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun EqSliderRow(
    label: String,
    valueDb: Float, // value -15f to +15f
    isEnabled: Boolean,
    alpha: Float,
    colors: ColorPalette,
    onValueChange: (Float) -> Unit
) {
    // Math converts -15f/15f db values back-and-forth into Composable Slider's 0..1f ranges
    val sliderPosition = (valueDb + 15f) / 30f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Broad dynamic text label on left side
        Text(
            text = label,
            color = colors.textPrimary.copy(alpha = alpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp)
        )

        // Custom Slider with capsule styling matching Screenshot 4
        Slider(
            value = sliderPosition,
            enabled = isEnabled,
            onValueChange = { pos ->
                val newDb = (pos * 30f) - 15f
                onValueChange(newDb)
            },
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.selectedBackground,
                disabledThumbColor = colors.textSecondary.copy(alpha = 0.3f),
                disabledActiveTrackColor = colors.selectedBackground.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("eq_slider_${label}")
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Dynamic decibel label on right side
        val formattedDb = if (valueDb == 0f) "0dB" else if (valueDb > 0) "+${valueDb.toInt()}dB" else "${valueDb.toInt()}dB"
        Text(
            text = formattedDb,
            color = colors.textSecondary.copy(alpha = alpha),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.End
        )
    }
}
