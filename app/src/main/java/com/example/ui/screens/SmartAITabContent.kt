package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.MusicViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SmartAITabContent(
    viewModel: MusicViewModel,
    colors: ColorPalette,
    modifier: Modifier = Modifier
) {
    val smartQueue by viewModel.smartQueueEnabled.collectAsState()
    val smartShuffle by viewModel.smartShuffleEnabled.collectAsState()
    val autoLyrics by viewModel.autoLyricsDownloadEnabled.collectAsState()
    val volumeBoost by viewModel.volumeBoost.collectAsState()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsState()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsState()
    val silenceSkip by viewModel.silenceSkipEnabled.collectAsState()
    val sleepRemaining by viewModel.sleepTimeRemaining.collectAsState()
    val aiStatus by viewModel.aiGenerationStatus.collectAsState()
    val lyricsStatus by viewModel.lyricsDownloadStatus.collectAsState()

    var customMoodText by remember { mutableStateOf("") }
    // User requested categories: Chill, Workout, Study, Late Night, Sad, Travel, and Walking
    val presetMoods = listOf("Chill", "Workout", "Study", "Late Night", "Sad", "Travel", "Walking")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("smart_ai_tab_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- NEW HERO SECTION: FEATURED AI BANNER ---
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .testTag("ai_featured_header_card"),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.img_ai_featured_banner_1782655556426),
                        contentDescription = "AI Featured Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Elegant dark gradient scrim overlay for text contrast
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                    // Content overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accent.copy(alpha = 0.2f))
                                .border(1.dp, colors.accent.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Acoustic Intelligence",
                                color = colors.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "Next-Gen Intelligent Audio",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Acoustic modeling & vector mix synthesizers active",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
            }
        }

        // --- SECTION 1: AI MOOD ENGINE ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp) // Optimized gap inside the card
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "AI Mood Engine",
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Acoustic AI Mood Engine",
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Describe your vibe or choose a preset category below. AI will analyze tracks and instantly synthesize a matching play queue.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Presets Wrap FlowRow
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "PRESET CATEGORIES",
                            color = colors.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetMoods.forEach { mood ->
                                val isSelected = customMoodText.equals(mood, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { 
                                        customMoodText = mood
                                        viewModel.generateMoodPlaylist(mood)
                                    },
                                    label = { Text(mood, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = if (isSelected) colors.background else colors.textPrimary,
                                        selectedContainerColor = colors.accent,
                                        selectedLabelColor = colors.background,
                                        containerColor = colors.background
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = colors.textSecondary.copy(alpha = 0.2f),
                                        selectedBorderColor = colors.accent,
                                        enabled = true,
                                        selected = isSelected
                                    )
                                )
                            }
                        }
                    }

                    // Unified input and search button group
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "CUSTOM MOOD DESCRIBER",
                            color = colors.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        
                        // Text Field for typing custom vibe
                        OutlinedTextField(
                            value = customMoodText,
                            onValueChange = { customMoodText = it },
                            placeholder = { Text("E.g., Bollywood Hits or Rainy Evening", fontSize = 13.sp, color = colors.textSecondary) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_mood_text_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.3f),
                                focusedContainerColor = colors.background,
                                unfocusedContainerColor = colors.background
                             ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (customMoodText.isNotBlank()) {
                                    viewModel.generateMoodPlaylist(customMoodText)
                                }
                            }),
                            trailingIcon = {
                                if (customMoodText.isNotBlank()) {
                                    IconButton(onClick = { customMoodText = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear text", tint = colors.textSecondary)
                                    }
                                }
                            }
                        )

                        Button(
                            onClick = { 
                                if (customMoodText.isNotBlank()) {
                                    viewModel.generateMoodPlaylist(customMoodText)
                                }
                            },
                            enabled = customMoodText.isNotBlank() && aiStatus == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.background,
                                disabledContainerColor = colors.accent.copy(alpha = 0.3f),
                                disabledContentColor = colors.background.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("ai_mood_generate_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Smart Mood Mix", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    // Progress Status indicator
                    if (aiStatus != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.accent.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colors.accent,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = aiStatus ?: "",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 1.5: AI Personalized recommendations ---
        item {
            val recSongs by viewModel.aiRecommendedSongs.collectAsState()
            val tasteAnalysis by viewModel.aiTasteAnalysis.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("ai_recommendations_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Personalized AI recommendations",
                                tint = colors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Personalized AI recommendations",
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { viewModel.refreshAIRecommendations() },
                            modifier = Modifier.size(32.dp).testTag("refresh_ai_recs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Acoustic Taste Analysis Badge
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.background),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Acoustic Taste Analysis",
                                color = colors.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = tasteAnalysis,
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (recSongs.isNotEmpty()) {
                        Text(
                            text = "Based on your library & play history, you might like:",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            recSongs.forEach { song ->
                                val isSongActive = viewModel.activeSong.collectAsState().value?.id == song.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSongActive) colors.selectedBackground else colors.background)
                                        .clickable {
                                            viewModel.setSong(song, autoplay = true)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // High-Fidelity Artwork using Coil AsyncImage
                                    val artworkSource = getFeaturedImageSource(song)
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(safeParseColor(song.artworkColorHex).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = artworkSource,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        
                                        // Semitransparent play/pause overlay for active/inactive state visual guidance
                                        val isPlayingFlowState by viewModel.isPlaying.collectAsState()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(if (isSongActive) Color.Black.copy(alpha = 0.4f) else Color.Transparent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSongActive) {
                                                Icon(
                                                    imageVector = if (isPlayingFlowState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else {
                                                // Subtle hover play indicator
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = if (isSongActive) colors.accent else colors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            color = colors.textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Signature badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.surface)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = song.audioPreset.uppercase(),
                                            color = colors.accent,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 2: INTELLIGENT PLAYBACK TOGGLES ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Smart Playback Controls",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // Smart Queue
                    SmartFeatureSwitchCard(
                        title = "Smart Queue Booster",
                        description = "Fills your track stream automatically using acoustic vector recommendations from your library.",
                        icon = Icons.Default.QueueMusic,
                        checked = smartQueue,
                        onChange = { viewModel.toggleSmartQueue() },
                        colors = colors
                    )

                    // Smart Shuffle
                    SmartFeatureSwitchCard(
                        title = "Anti-Repeat Shuffle",
                        description = "Analyzes your track history and skips recently played items to keep playback fresh and unexpected.",
                        icon = Icons.Default.ShuffleOn,
                        checked = smartShuffle,
                        onChange = { viewModel.toggleSmartShuffle() },
                        colors = colors
                    )

                    // Auto Lyrics
                    SmartFeatureSwitchCard(
                        title = "Lyrics Auto-Bridge",
                        description = "Automatically synthesizes and syncs synchronized lyrics when local songs lack metadata.",
                        icon = Icons.Default.DocumentScanner,
                        checked = autoLyrics,
                        onChange = { viewModel.toggleAutoLyricsDownload() },
                        colors = colors
                    )

                    if (lyricsStatus != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.accent.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync, 
                                    contentDescription = null, 
                                    tint = colors.accent, 
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = lyricsStatus ?: "", 
                                    color = colors.textPrimary, 
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 3: DSP & REAMPLIFICATION & EFFECTS ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Hardware Reamplification & DSP",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    // Volume Boost up to 200%
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp, 
                                    contentDescription = null, 
                                    tint = colors.accent, 
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Loudness Enhancer (Up to 200%)", 
                                    color = colors.textPrimary, 
                                    fontSize = 14.sp, 
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "${(volumeBoost * 100).toInt()}%",
                                color = if (volumeBoost > 1.0f) colors.accent else colors.textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        Slider(
                            value = volumeBoost,
                            onValueChange = { viewModel.setVolumeBoost(it) },
                            valueRange = 1.0f..2.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.accent,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.background
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("volume_boost_slider")
                        )
                        Text(
                            text = "Propagates physical decibel amplification directly via Android hardware layer codecs.",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    HorizontalDivider(color = colors.background, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(2.dp))

                    // Silence Skip
                    SmartFeatureSwitchCard(
                        title = "Gapless Silence Skip",
                        description = "Bypasses leading gaps and silent fades to deliver an uninterrupted listening experience.",
                        icon = Icons.Default.MusicVideo,
                        checked = silenceSkip,
                        onChange = { viewModel.toggleSilenceSkip() },
                        colors = colors
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    HorizontalDivider(color = colors.background, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(2.dp))

                    // Crossfade
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CompareArrows, 
                                    contentDescription = null, 
                                    tint = colors.accent, 
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Crossfade Between Songs", 
                                    color = colors.textPrimary, 
                                    fontSize = 14.sp, 
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Switch(
                                checked = crossfadeEnabled,
                                onCheckedChange = { viewModel.toggleCrossfade() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.background,
                                    checkedTrackColor = colors.accent,
                                    uncheckedThumbColor = colors.textSecondary,
                                    uncheckedTrackColor = colors.background
                                ),
                                modifier = Modifier.testTag("crossfade_toggle")
                            )
                        }

                        if (crossfadeEnabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Transition Duration", color = colors.textSecondary, fontSize = 12.sp)
                                    Text("${crossfadeDuration} seconds", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Slider(
                                    value = crossfadeDuration.toFloat(),
                                    onValueChange = { viewModel.setCrossfadeDuration(it.toInt()) },
                                    valueRange = 1f..10f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.accent,
                                        activeTrackColor = colors.accent,
                                        inactiveTrackColor = colors.background
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 4: SLEEP TIMER ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Snooze, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            Text("Automatic Sleep Timer", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        if (sleepRemaining != null) {
                            val mins = sleepRemaining!! / 60
                            val secs = sleepRemaining!! % 60
                            Text(
                                text = String.format("%02d:%02d", mins, secs),
                                color = colors.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        } else {
                            Text("Inactive", color = colors.textSecondary, fontSize = 13.sp)
                        }
                    }

                    Text(
                        text = "Gracefully dims and halts audio playback after a specified time interval.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val intervals = listOf(5, 15, 30, 60)
                        intervals.forEach { duration ->
                            OutlinedButton(
                                onClick = { viewModel.setSleepTimer(duration) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.textPrimary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy()
                            ) {
                                Text("${duration}m", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (sleepRemaining != null) {
                            Button(
                                onClick = { viewModel.setSleepTimer(null) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartFeatureSwitchCard(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    colors: ColorPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .background(colors.background.copy(alpha = 0.5f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) colors.accent else colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )

        Column(
            modifier = Modifier.weight(1f), 
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title, 
                color = colors.textPrimary, 
                fontSize = 14.sp, 
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description, 
                color = colors.textSecondary, 
                fontSize = 12.sp, 
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Switch(
            checked = checked,
            onCheckedChange = null, // Row is clickable, this ensures perfect unified touch target & no conflicts
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.background
            )
        )
    }
}
