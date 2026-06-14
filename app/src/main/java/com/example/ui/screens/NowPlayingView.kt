package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Song
import com.example.ui.MusicViewModel
import com.example.ui.ScreenState
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun NowPlayingView(
    viewModel: MusicViewModel,
    colors: ColorPalette,
    modifier: Modifier = Modifier
) {
    val activeSong by viewModel.activeSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPositionSeconds by viewModel.currentPositionSeconds.collectAsState()
    val trackDurationSeconds by viewModel.trackDurationSeconds.collectAsState()
    val isLyricsExpanded by viewModel.isLyricsExpanded.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val sleepTimeRemaining by viewModel.sleepTimeRemaining.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    var showMoreOptionsDialog by remember { mutableStateOf(false) }

    if (activeSong == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            Text("No song active. Go select a note!", color = colors.textSecondary)
        }
        return
    }

    val song = activeSong!!
    val progress = if (trackDurationSeconds > 0) currentPositionSeconds.toFloat() / trackDurationSeconds else 0f

    // Helper to format seconds as mm:ss
    val formatTime = { secs: Int ->
        val m = secs / 60
        val s = secs % 60
        String.format("%02d:%02d", m, s)
    }

    // Lyrics calculation (highlight active line)
    val lyricLines = remember(song.lyrics) {
        song.lyrics.split("\n\n").filter { it.isNotBlank() }
    }
    
    val activeLineIndex = remember(lyricLines, currentPositionSeconds, trackDurationSeconds) {
        if (lyricLines.isEmpty() || trackDurationSeconds == 0) 0
        else {
            val step = trackDurationSeconds.toFloat() / lyricLines.size
            val calcIdx = (currentPositionSeconds / step).toInt()
            calcIdx.coerceIn(0, lyricLines.size - 1)
        }
    }

    val lyricsScrollState = rememberScrollState()

    // Smooth lyrics auto-scroll sync based on active index
    LaunchedEffect(activeLineIndex) {
        if (lyricLines.isNotEmpty() && isLyricsExpanded) {
            val approxHeight = lyricsScrollState.maxValue.toFloat() / lyricLines.size
            lyricsScrollState.animateScrollTo((activeLineIndex * approxHeight).toInt())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        safeParseColor(song.artworkColorHex).copy(alpha = 0.5f),
                        colors.background
                    )
                )
            )
            .testTag("now_playing_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(ScreenState.HOME) },
                    modifier = Modifier.testTag("collapse_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Now Playing",
                    color = colors.textPrimary.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    // Lyrics visibility toggle
                    IconButton(
                        onClick = { viewModel.toggleLyricsExpanded() },
                        modifier = Modifier.testTag("lyrics_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = "Lyrics Display",
                            tint = if (isLyricsExpanded) colors.accent else colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // More play settings (3-dot options menu)
                    IconButton(
                        onClick = { showMoreOptionsDialog = true },
                        modifier = Modifier.testTag("more_options_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Playback Options",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (!isLyricsExpanded) {
                // Large Rounded Artwork View
                Spacer(modifier = Modifier.height(20.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .scale(if (isPlaying) 1.02f else 0.98f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(safeParseColor(song.artworkColorHex))
                        .testTag("artwork_card"),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = getFeaturedImageSource(song),
                        contentDescription = "Active song featured image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Song Title & Artists descriptors
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = song.title,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("track_title_text")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = song.artist,
                        color = colors.textSecondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("track_artist_text")
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // High-fidelity Progress Slider
                Slider(
                    value = progress,
                    onValueChange = { newValue ->
                        val targetSecs = (newValue * trackDurationSeconds).toInt()
                        viewModel.seekTo(targetSecs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.textSecondary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playback_progress_slider")
                )

                // Timestamps display
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPositionSeconds),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatTime(trackDurationSeconds),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.weight(0.4f))

                // Media Playback Transport Controllers Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Repeat mode toggle
                    IconButton(
                        onClick = { viewModel.toggleRepeatMode() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("repeat_toggle_button")
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                com.example.ui.RepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Toggle Repeat Options",
                            tint = when (repeatMode) {
                                com.example.ui.RepeatMode.NONE -> colors.textSecondary.copy(alpha = 0.4f)
                                else -> colors.accent
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Skip Back
                    IconButton(
                        onClick = { viewModel.skipPrevious() },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("skip_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Skip Previous",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Big play/pause circle
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(colors.selectedBackground, CircleShape)
                            .testTag("play_pause_toggle")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause Toggle",
                            tint = colors.accent,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Skip Next
                    IconButton(
                        onClick = { viewModel.skipNext() },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("skip_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip Next",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Heart toggle
                    IconButton(
                        onClick = { viewModel.toggleFavorite(song) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("favorite_button")
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite Toggle",
                            tint = if (song.isFavorite) Color.Red else colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.4f))
            } else {
                // Expanded High-Fidelity Lyrics Panel
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = song.title,
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = song.artist,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surface.copy(alpha = 0.5f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (lyricLines.isEmpty()) {
                        Text("No lyrics found.", color = colors.textSecondary)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(lyricsScrollState),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(100.dp))
                            lyricLines.forEachIndexed { idx, line ->
                                val isActive = (idx == activeLineIndex)
                                Text(
                                    text = line,
                                    color = if (isActive) colors.accent else colors.textPrimary.copy(alpha = 0.5f),
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = if (isActive) 20.sp else 16.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.seekTo((idx * (trackDurationSeconds / lyricLines.size.toFloat())).toInt()) }
                                        .padding(vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mini progress controls when viewing lyrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { viewModel.toggleLyricsExpanded() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close lyrics", tint = colors.textPrimary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(onClick = { viewModel.skipPrevious() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = colors.textPrimary)
                        }
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(colors.selectedBackground, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                tint = colors.accent,
                                contentDescription = null
                            )
                        }
                        IconButton(onClick = { viewModel.skipNext() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = colors.textPrimary)
                        }
                    }

                    Text(
                        text = formatTime(currentPositionSeconds),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showMoreOptionsDialog) {
        MorePlaybackOptionsDialog(
            song = song,
            playbackSpeed = playbackSpeed,
            sleepTimeRemaining = sleepTimeRemaining,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            colors = colors,
            onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onSleepTimerChange = { viewModel.setSleepTimer(it) },
            onRepeatToggle = { viewModel.toggleRepeatMode() },
            onShuffleToggle = { viewModel.toggleShuffle() },
            onAudioEffectsClick = {
                showMoreOptionsDialog = false
                viewModel.navigateTo(ScreenState.EQUALIZER)
            },
            onDismissRequest = { showMoreOptionsDialog = false }
        )
    }
}

@Composable
fun MorePlaybackOptionsDialog(
    song: Song,
    playbackSpeed: Float,
    sleepTimeRemaining: Int?,
    repeatMode: com.example.ui.RepeatMode,
    shuffleEnabled: Boolean,
    colors: ColorPalette,
    onSpeedChange: (Float) -> Unit,
    onSleepTimerChange: (Int?) -> Unit,
    onRepeatToggle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onAudioEffectsClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("dialog_close_button")
            ) {
                Text("Close", color = colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                "Playback Settings",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("playback_settings_dialog"),
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Section 2: Shuffle & Repeat toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Shuffle card block
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onShuffleToggle() }
                            .testTag("dialog_shuffle_block"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (shuffleEnabled) colors.selectedBackground else colors.background
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle mode",
                                tint = if (shuffleEnabled) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Shuffle",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (shuffleEnabled) "On" else "Off",
                                color = if (shuffleEnabled) colors.accent else colors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Repeat card block
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onRepeatToggle() }
                            .testTag("dialog_repeat_block"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (repeatMode != com.example.ui.RepeatMode.NONE) colors.selectedBackground else colors.background
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    com.example.ui.RepeatMode.ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat mode",
                                tint = if (repeatMode != com.example.ui.RepeatMode.NONE) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Repeat",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when (repeatMode) {
                                    com.example.ui.RepeatMode.ALL -> "All"
                                    com.example.ui.RepeatMode.ONE -> "One"
                                    else -> "Off"
                                },
                                color = if (repeatMode != com.example.ui.RepeatMode.NONE) colors.accent else colors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Section 3: Playback speed chips selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Speed Selector", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val isSpeedSelected = (playbackSpeed == speed)
                            val text = if (speed == 1.0f) "1.0x" else "${speed}x"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSpeedSelected) colors.selectedBackground else colors.background)
                                    .clickable { onSpeedChange(speed) }
                                    .padding(vertical = 8.dp)
                                    .testTag("dialog_speed_chip_$text"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = text,
                                    color = if (isSpeedSelected) colors.accent else colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSpeedSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Section 4: Sleep Timer Customizer
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (sleepTimeRemaining != null) {
                                val m = sleepTimeRemaining / 60
                                val s = sleepTimeRemaining % 60
                                "Sleep Timer: Stop in ${String.format("%02d:%02d", m, s)}"
                            } else {
                                "Sleep Timer"
                            },
                            color = if (sleepTimeRemaining != null) colors.accent else colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(null, 5, 15, 30, 60).forEach { mins ->
                            val text = if (mins == null) "Off" else "${mins}m"
                            val isSelected = if (mins == null) sleepTimeRemaining == null else {
                                val currentMin = sleepTimeRemaining?.div(60)
                                currentMin == mins
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) colors.selectedBackground else colors.background)
                                    .clickable { onSleepTimerChange(mins) }
                                    .padding(vertical = 8.dp)
                                    .testTag("dialog_timer_chip_$text"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = text,
                                    color = if (isSelected) colors.accent else colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Section 5: Audio Effects Button Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAudioEffectsClick() }
                        .testTag("dialog_eq_block"),
                    colors = CardDefaults.cardColors(containerColor = colors.background),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Audio effects",
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Audio Effects (Equalizer)",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Fine-tune sound frequencies and presets",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    )
}
