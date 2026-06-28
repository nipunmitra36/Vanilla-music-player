package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
    val isPreparing by viewModel.isPreparing.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val currentPositionSeconds by viewModel.currentPositionSeconds.collectAsState()
    val trackDurationSeconds by viewModel.trackDurationSeconds.collectAsState()
    val isLyricsExpanded by viewModel.isLyricsExpanded.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val sleepTimeRemaining by viewModel.sleepTimeRemaining.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val lyricsStatus by viewModel.lyricsDownloadStatus.collectAsState()
    var showMoreOptionsDialog by remember { mutableStateOf(false) }
    var showLyricsEditor by remember { mutableStateOf(false) }

    var dragProgress by remember { mutableStateOf<Float?>(null) }

    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.04f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ArtworkScale"
    )

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

    // LRC Lyrics Line definition
    data class NowPlayingLRCLine(val timeSecs: Int, val text: String)

    val lrcLines = remember(song.lyrics) {
        val lines = song.lyrics.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val timeRegex = """\[(\d+):(\d+)(?:\.(\d+))?]""".toRegex()
        val list = ArrayList<NowPlayingLRCLine>()
        for (line in lines) {
            val match = timeRegex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toIntOrNull() ?: 0
                val sec = match.groupValues[2].toIntOrNull() ?: 0
                val totalSec = min * 60 + sec
                val text = line.replace(timeRegex, "").trim()
                list.add(NowPlayingLRCLine(totalSec, text))
            } else {
                list.add(NowPlayingLRCLine(-1, line))
            }
        }
        
        var lastTime = 0
        val finalList = list.mapIndexed { idx, item ->
            if (item.timeSecs == -1) {
                val generatedTime = lastTime + 5
                lastTime = generatedTime
                NowPlayingLRCLine(generatedTime, item.text)
            } else {
                lastTime = item.timeSecs
                item
            }
        }
        finalList.sortedBy { it.timeSecs }
    }

    val activeLineIndex = remember(lrcLines, currentPositionSeconds) {
        if (lrcLines.isEmpty()) 0
        else {
            var index = 0
            for (i in lrcLines.indices) {
                if (lrcLines[i].timeSecs <= currentPositionSeconds) {
                    index = i
                } else {
                    break
                }
            }
            index.coerceIn(0, lrcLines.size - 1)
        }
    }

    val lyricsScrollState = rememberScrollState()

    // Smooth lyrics auto-scroll sync based on active index
    LaunchedEffect(activeLineIndex) {
        if (lrcLines.isNotEmpty() && isLyricsExpanded) {
            val approxHeight = lyricsScrollState.maxValue.toFloat() / lrcLines.size
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
                        .scale(artworkScale)
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            BorderStroke(
                                3.dp,
                                Brush.linearGradient(
                                    colors = listOf(colors.accent, colors.background.copy(alpha = 0.5f), colors.accent)
                                )
                            ),
                            RoundedCornerShape(24.dp)
                        )
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

                    if (isPreparing || isBuffering) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = colors.accent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Song Title & Artists descriptors Row with Favorite on the right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = song.title,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("track_title_text")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.artist,
                            color = colors.textSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("track_artist_text")
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Properly positioned and sized heart toggle icon
                    IconButton(
                        onClick = { viewModel.toggleFavorite(song) },
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("favorite_button")
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite Toggle",
                            tint = if (song.isFavorite) androidx.compose.ui.graphics.Color(0xFFFF1744) else colors.textPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // High-fidelity Progress Squiggle Slider
                SquiggleSlider(
                    value = dragProgress ?: progress,
                    onValueChange = { newValue ->
                        dragProgress = newValue
                    },
                    onValueChangeFinished = {
                        val targetSeconds = ((dragProgress ?: progress) * trackDurationSeconds).toInt()
                        viewModel.seekTo(targetSeconds)
                        dragProgress = null
                    },
                    isPlaying = isPlaying,
                    colors = colors,
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
                        text = formatTime(
                            if (dragProgress != null) {
                                (dragProgress!! * trackDurationSeconds).toInt()
                            } else {
                                currentPositionSeconds
                            }
                        ),
                        color = colors.textPrimary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
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
                    // Shuffle mode toggle
                    IconButton(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("shuffle_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Toggle Shuffle mode",
                            tint = if (shuffleEnabled) colors.accent else colors.textPrimary.copy(alpha = 0.4f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Skip Back 15s
                    IconButton(
                        onClick = { viewModel.skipBackward15() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("skip_backward_15_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateLeft,
                            contentDescription = "Rewind 15s",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Skip Back
                    IconButton(
                        onClick = { viewModel.skipPrevious() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("skip_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Skip Previous",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Big play/pause circle
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(64.dp)
                            .background(colors.selectedBackground, CircleShape)
                            .testTag("play_pause_toggle")
                    ) {
                        if (isPreparing || isBuffering) {
                            CircularProgressIndicator(
                                color = colors.accent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause Toggle",
                                tint = colors.accent,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    // Skip Next
                    IconButton(
                        onClick = { viewModel.skipNext() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("skip_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip Next",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Skip Forward 15s
                    IconButton(
                        onClick = { viewModel.skipForward15() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("skip_forward_15_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Forward 15s",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Repeat mode toggle
                    IconButton(
                        onClick = { viewModel.toggleRepeatMode() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("repeat_toggle_button")
                    ) {
                        Icon(
                            imageVector = when (repeatMode) {
                                com.example.ui.RepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Toggle Repeat mode",
                            tint = when (repeatMode) {
                                com.example.ui.RepeatMode.NONE -> colors.textPrimary.copy(alpha = 0.4f)
                                else -> colors.accent
                            },
                            modifier = Modifier.size(22.dp)
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
                    if (lyricsStatus != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(
                                color = colors.accent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "MUSICLY AI ENGINE",
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = lyricsStatus ?: "",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (lrcLines.isEmpty() || song.lyrics.isBlank() || song.lyrics == "No lyrics found") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(colors.accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = "AI Lyrics Synchronizer",
                                color = colors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "No synchronized lyrics metadata was found in this file. Let our local AI core synthesize perfectly synced LRC scrolling lyrics.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.downloadLyricsForSong(song) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Synthesize AI Lyrics",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(lyricsScrollState),
                            verticalArrangement = Arrangement.spacedBy(24.dp), // Generous spacing for modern typography
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(120.dp))
                            lrcLines.forEachIndexed { idx, item ->
                                val isActive = (idx == activeLineIndex)
                                
                                // Animated text colors
                                val lyricColor by animateColorAsState(
                                    targetValue = if (isActive) colors.accent else colors.textPrimary.copy(alpha = 0.45f),
                                    animationSpec = tween(400, easing = EaseInOutCubic),
                                    label = "LyricColor"
                                )
                                
                                // Animated scale & opacity for dynamic perspective depth
                                val scale by animateFloatAsState(
                                    targetValue = if (isActive) 1.06f else 0.94f,
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
                                    label = "LyricScale"
                                )
                                
                                val opacity by animateFloatAsState(
                                    targetValue = if (isActive) 1f else 0.45f,
                                    animationSpec = tween(400),
                                    label = "LyricOpacity"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = opacity
                                        }
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null // remove distracting default ripple
                                        ) {
                                            viewModel.seekTo(item.timeSecs)
                                        }
                                        .padding(horizontal = 24.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.text,
                                        color = lyricColor,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                        fontSize = if (isActive) 23.sp else 17.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = if (isActive) 32.sp else 26.sp,
                                        letterSpacing = if (isActive) 0.5.sp else 0.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(120.dp))
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { viewModel.toggleLyricsExpanded() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Close lyrics", tint = colors.textPrimary)
                        }
                        IconButton(
                            onClick = { showLyricsEditor = true },
                            modifier = Modifier.testTag("edit_lyrics_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit lyrics", tint = colors.accent)
                        }
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

    if (showLyricsEditor) {
        var editingText by remember { mutableStateOf(song.lyrics) }
        AlertDialog(
            onDismissRequest = { showLyricsEditor = false },
            title = { Text("Edit LRC Lyrics", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            containerColor = colors.surface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Tag rows with [mm:ss] (e.g. [00:15] My lyric line) to link and sync highlights to seconds during playing.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .testTag("lyrics_editor_input"),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = colors.textPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.4f),
                            focusedContainerColor = colors.background,
                            unfocusedContainerColor = colors.background
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateLyrics(song, editingText)
                        showLyricsEditor = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.accent)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLyricsEditor = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)
                ) {
                    Text("Cancel")
                }
            }
        )
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
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 8.dp)
                    .testTag("dialog_close_button")
            ) {
                Text(
                    text = "Close",
                    color = colors.accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        },
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Playback Settings",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personalize response, timers, and acoustics",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("playback_settings_dialog"),
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Section 1: Shuffle & Repeat state controls (Horizontal symmetrical cards)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Modernized Shuffle card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onShuffleToggle() }
                            .testTag("dialog_shuffle_block"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (shuffleEnabled) colors.accent.copy(alpha = 0.12f) else colors.background
                        ),
                        border = BorderStroke(
                            width = if (shuffleEnabled) 1.5.dp else 1.dp,
                            color = if (shuffleEnabled) colors.accent else colors.textSecondary.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle mode",
                                tint = if (shuffleEnabled) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Shuffle Queue",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (shuffleEnabled) "Enabled" else "Standard",
                                color = if (shuffleEnabled) colors.accent else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Modernized Repeat card
                    val isRepeatActive = (repeatMode != com.example.ui.RepeatMode.NONE)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onRepeatToggle() }
                            .testTag("dialog_repeat_block"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRepeatActive) colors.accent.copy(alpha = 0.12f) else colors.background
                        ),
                        border = BorderStroke(
                            width = if (isRepeatActive) 1.5.dp else 1.dp,
                            color = if (isRepeatActive) colors.accent else colors.textSecondary.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    com.example.ui.RepeatMode.ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat mode",
                                tint = if (isRepeatActive) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Repeat Mode",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                when (repeatMode) {
                                    com.example.ui.RepeatMode.ALL -> "Repeat All"
                                    com.example.ui.RepeatMode.ONE -> "Repeat One"
                                    else -> "Off"
                                },
                                color = if (isRepeatActive) colors.accent else colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Section 2: High-Fi Playback Speed Selector (Beautiful glass pills container)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.background)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.selectedBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Playback Speed",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${playbackSpeed}x",
                            color = colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val isSpeedSelected = (playbackSpeed == speed)
                            val text = if (speed == 1.0f) "1.0x" else "${speed}x"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSpeedSelected) colors.accent else colors.selectedBackground)
                                    .clickable { onSpeedChange(speed) }
                                    .padding(vertical = 8.dp)
                                    .testTag("dialog_speed_chip_$text"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = text,
                                    color = if (isSpeedSelected) colors.background else colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Section 3: Sleep Timer Customizer (Beautiful glass pills container)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.background)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.selectedBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sleep Timer",
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (sleepTimeRemaining != null) {
                            val m = sleepTimeRemaining / 60
                            val s = sleepTimeRemaining % 60
                            Text(
                                text = String.format("%02d:%02d left", m, s),
                                color = colors.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(null, 5, 15, 30, 60).forEach { mins ->
                            val text = if (mins == null) "Off" else "${mins}m"
                            val isSelected = if (mins == null) {
                                sleepTimeRemaining == null
                            } else {
                                val currentMin = sleepTimeRemaining?.div(60)
                                currentMin == mins
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) colors.accent else colors.selectedBackground)
                                    .clickable { onSleepTimerChange(mins) }
                                    .padding(vertical = 8.dp)
                                    .testTag("dialog_timer_chip_$text"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = text,
                                    color = if (isSelected) colors.background else colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Section 4: Audio Effects Equalizer Button card list tile
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAudioEffectsClick() }
                        .testTag("dialog_eq_block"),
                    colors = CardDefaults.cardColors(containerColor = colors.background),
                    border = BorderStroke(1.dp, colors.textSecondary.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Audio effects",
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Acoustic Equalizer & FX",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Fine-tune frequencies, presets & bass",
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

@Composable
fun SquiggleSlider(
    value: Float, // 0f to 1f
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    isPlaying: Boolean,
    colors: ColorPalette,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var width by remember { mutableStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    // Phase animation for the squiggle wave
    val infiniteTransition = rememberInfiniteTransition(label = "SquigglePhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase"
    )

    val activePhase = if (isPlaying) phase else 0f

    // Smoothly animate the thumb radius on touch/drag
    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 9.dp else 6.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ThumbRadius"
    )

    val heightDp = 32.dp

    Box(
        modifier = modifier
            .height(heightDp)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        val newValue = (offset.x / width).coerceIn(0f, 1f)
                        onValueChange(newValue)
                        tryAwaitRelease()
                        isDragging = false
                        onValueChangeFinished()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val newValue = (offset.x / width).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newValue = (change.position.x / width).coerceIn(0f, 1f)
                        onValueChange(newValue)
                    }
                )
            }
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { layoutCoordinates ->
                    width = layoutCoordinates.size.width.toFloat()
                }
        ) {
            val centerY = size.height / 2f
            val endX = value * width
            
            val waveLengthPx = with(density) { 24.dp.toPx() }
            val amplitudePx = with(density) { 4.dp.toPx() }
            val strokeWidthPx = with(density) { 3.5.dp.toPx() }
            val fadeDistancePx = with(density) { 24.dp.toPx() }

            // 1. Draw Active Wave Track
            if (endX > 0f) {
                val path = Path()
                path.moveTo(0f, centerY)

                var x = 0f
                // We'll increment x to plot the wave
                while (x < endX) {
                    val distanceToThumb = endX - x
                    val fadeFactor = if (distanceToThumb < fadeDistancePx) {
                        distanceToThumb / fadeDistancePx
                    } else {
                        1f
                    }

                    val radians = (x / waveLengthPx) * (2 * Math.PI) - activePhase
                    val y = centerY + kotlin.math.sin(radians).toFloat() * amplitudePx * fadeFactor
                    path.lineTo(x, y)
                    x += 2f
                }

                // Smooth final point to avoid hard corner at the end of the step
                val endRadians = (endX / waveLengthPx) * (2 * Math.PI) - activePhase
                val endY = centerY + kotlin.math.sin(endRadians).toFloat() * amplitudePx * 0f // exactly 0 fade at endX
                path.lineTo(endX, endY)

                drawPath(
                    path = path,
                    color = colors.accent,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                )
            }

            // 2. Draw Inactive Track (sleek, straight line)
            if (endX < width) {
                drawLine(
                    color = colors.textSecondary.copy(alpha = 0.24f),
                    start = Offset(endX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = with(density) { 3.dp.toPx() },
                    cap = StrokeCap.Round
                )
            }

            // 3. Draw Thumb
            drawCircle(
                color = colors.accent,
                radius = with(density) { thumbRadius.toPx() },
                center = Offset(endX, centerY)
            )
            
            // Subtle pulse ring around the thumb if dragging
            if (isDragging) {
                drawCircle(
                    color = colors.accent.copy(alpha = 0.2f),
                    radius = with(density) { (thumbRadius + 6.dp).toPx() },
                    center = Offset(endX, centerY)
                )
            }
        }
    }
}
