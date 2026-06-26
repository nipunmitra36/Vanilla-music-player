package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MusicViewModel
import com.example.ui.ScreenState
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueueMusic
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    private var mainViewModel: MusicViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (granted) {
            mainViewModel?.scanMedia()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("OPEN_NOW_PLAYING", false)) {
            mainViewModel?.navigateTo(ScreenState.NOW_PLAYING)
        }
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MusicViewModel = viewModel()
                mainViewModel = viewModel

                LaunchedEffect(intent) {
                    if (intent?.getBooleanExtra("OPEN_NOW_PLAYING", false) == true) {
                        viewModel.navigateTo(ScreenState.NOW_PLAYING)
                    }
                    handleIntent(intent)
                }

                val selectedTab by viewModel.selectedTab.collectAsState()
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler(enabled = true) {
                    when (viewModel.currentScreen.value) {
                        ScreenState.PLAYLIST_DETAILS -> viewModel.navigateTo(ScreenState.HOME)
                        ScreenState.ALBUM_DETAILS -> viewModel.navigateTo(ScreenState.HOME)
                        ScreenState.SETTINGS -> viewModel.navigateTo(ScreenState.HOME)
                        ScreenState.EQUALIZER -> viewModel.navigateTo(ScreenState.NOW_PLAYING)
                        ScreenState.NOW_PLAYING -> viewModel.navigateTo(ScreenState.HOME)
                        ScreenState.HOME -> {
                            if (selectedTab != "Songs") {
                                viewModel.selectTab("Songs")
                            } else {
                                showExitDialog = true
                            }
                        }
                    }
                }

                // Safe permission request when UI starts composition and activity is STARTED/RESUMED
                LaunchedEffect(Unit) {
                    try {
                        // Tiny delay to ensure lifecycle reaches STARTED/RESUMED before launching permission Launcher
                        kotlinx.coroutines.delay(600)
                        
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }

                        val toRequest = permissions.filter {
                            ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                        }

                        if (toRequest.isNotEmpty()) {
                            permissionLauncher.launch(toRequest.toTypedArray())
                        } else {
                            viewModel.scanMedia()
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }

                val currentScreen by viewModel.currentScreen.collectAsState()
                val themeMode by viewModel.themeMode.collectAsState()
                val setupGuideCompleted by viewModel.setupGuideCompleted.collectAsState()

                // Dynamically update status bar color: in light theme (Serene Alabaster), the status bar will use light background with black text/icons
                val context = androidx.compose.ui.platform.LocalContext.current
                val window = (context as? android.app.Activity)?.window
                if (window != null) {
                    val isLight = themeMode == "Serene Alabaster"
                    val statusBarColor = android.graphics.Color.TRANSPARENT
                    SideEffect {
                        window.statusBarColor = statusBarColor
                        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = isLight // Black icons/text in light theme, white in dark theme
                        }
                    }
                }

                // Resolve active design colors
                val colors = when (themeMode) {
                    "Serene Alabaster" -> ColorPalette(
                        background = Color(0xFFF8FAFC),
                        surface = Color(0xFFFFFFFF),
                        selectedBackground = Color(0xFFF1F5F9),
                        accent = Color(0xFF0F766E),
                        textPrimary = Color(0xFF0F172A),
                        textSecondary = Color(0xFF475569)
                    )
                    "Slate Gray" -> ColorPalette(
                        background = Color(0xFF121214),
                        surface = Color(0xFF1E1E22),
                        selectedBackground = Color(0xFF2D323E),
                        accent = Color(0xFF8A9FB4),
                        textPrimary = Color(0xFFE2E2E6),
                        textSecondary = Color(0xFF8E9099)
                    )
                    "Midnight Blue" -> ColorPalette(
                        background = Color(0xFF0A0E17),
                        surface = Color(0xFF111827),
                        selectedBackground = Color(0xFF1E293B),
                        accent = Color(0xFF3B82F6),
                        textPrimary = Color(0xFFF3F4F6),
                        textSecondary = Color(0xFF9CA3AF)
                    )
                    "Obsidian Crimson" -> ColorPalette(
                        background = Color(0xFF0F0F0F),
                        surface = Color(0xFF181818),
                        selectedBackground = Color(0xFF282828),
                        accent = Color(0xFFFF0033), // Obsidian Crimson Red accent
                        textPrimary = Color(0xFFFAFAFA),
                        textSecondary = Color(0xFFA3A3A3)
                    )
                    else -> ColorPalette( // Dark Forest (default matching Screenshots)
                        background = Color(0xFF0C100D),
                        surface = Color(0xFF131914),
                        selectedBackground = Color(0xFF1D3F23),
                        accent = Color(0xFF5FA169),
                        textPrimary = Color(0xFFE1ECE2),
                        textSecondary = Color(0xFF90A292)
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Modern In-App Notification Banner
                        val inAppNotification by viewModel.inAppNotification.collectAsState()
                        AnimatedVisibility(
                            visible = inAppNotification != null,
                            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(16.dp)
                                .zIndex(99f)
                        ) {
                            inAppNotification?.let { msg ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .shadow(12.dp, RoundedCornerShape(20.dp)),
                                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.5.dp, colors.accent)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.accent.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QueueMusic,
                                                contentDescription = null,
                                                tint = colors.accent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Library Updated",
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = msg,
                                                color = colors.textSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        IconButton(onClick = { viewModel.dismissInAppNotification() }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Modern screen crossfade animated transition
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                            },
                            label = "MainViewTransitions"
                        ) { state ->
                            when (state) {
                                ScreenState.HOME -> {
                                    HomeView(viewModel = viewModel, colors = colors)
                                }
                                ScreenState.NOW_PLAYING -> {
                                    NowPlayingView(viewModel = viewModel, colors = colors)
                                }
                                ScreenState.EQUALIZER -> {
                                    EqualizerView(viewModel = viewModel, colors = colors)
                                }
                                ScreenState.SETTINGS -> {
                                    SettingsView(viewModel = viewModel, colors = colors)
                                }
                                ScreenState.PLAYLIST_DETAILS -> {
                                    com.example.ui.screens.PlaylistDetailsView(viewModel = viewModel, colors = colors)
                                }
                                ScreenState.ALBUM_DETAILS -> {
                                    com.example.ui.screens.AlbumDetailsView(viewModel = viewModel, colors = colors)
                                }
                            }
                        }

                        // Mini Player
                        if (currentScreen != ScreenState.NOW_PLAYING) {
                            val activeSong by viewModel.activeSong.collectAsState()
                            val isPlaying by viewModel.isPlaying.collectAsState()
                            val hasPlayedAny by viewModel.hasPlayedAny.collectAsState()
                            
                            if (activeSong != null && hasPlayedAny) {
                                MiniPlayerView(
                                    song = activeSong!!,
                                    isPlaying = isPlaying,
                                    onPlayPause = { viewModel.togglePlayPause() },
                                    onNext = { viewModel.skipNext() },
                                    onPrevious = { viewModel.skipPrevious() },
                                    onClick = { viewModel.navigateTo(ScreenState.NOW_PLAYING) },
                                    colors = colors,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding()
                                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                                )
                            }
                        }

                        // YouTube Download Dialog
                        val sharedYouTubeUrl by viewModel.sharedYouTubeUrl.collectAsState()
                        if (sharedYouTubeUrl != null) {
                            val fetchedMeta by viewModel.fetchedYouTubeMetadata.collectAsState()
                            val isFetchingMetadata by viewModel.isFetchingMetadata.collectAsState()
                            val isDownloading by viewModel.isDownloading.collectAsState()
                            val downloadProgress by viewModel.downloadProgress.collectAsState()
                            val downloadPhase by viewModel.downloadPhase.collectAsState()
                            val downloadSongTitle by viewModel.downloadSongTitle.collectAsState()

                            AlertDialog(
                                onDismissRequest = { 
                                    if (!isDownloading && !isFetchingMetadata) {
                                        viewModel.setSharedYouTubeUrl(null)
                                    }
                                },
                                title = { 
                                    Text(
                                        text = "Musicly Downloader",
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                },
                                containerColor = colors.surface,
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (isFetchingMetadata) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(36.dp))
                                                Text(
                                                    text = "Fetching YouTube video metadata...",
                                                    color = colors.textSecondary,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        } else {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                fetchedMeta?.artworkUrl?.let { uri ->
                                                    AsyncImage(
                                                        model = uri,
                                                        contentDescription = "Video Thumbnail Preview",
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(140.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                }
                                                
                                                Text(
                                                    text = downloadSongTitle.ifEmpty { "Extracting audio stream..." },
                                                    color = colors.textPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2
                                                )
                                                
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                CircularProgressIndicator(
                                                    color = colors.accent,
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                Text(
                                                    text = downloadPhase,
                                                    color = colors.textPrimary,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 15.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                                
                                                LinearProgressIndicator(
                                                    progress = { downloadProgress },
                                                    color = colors.accent,
                                                    trackColor = colors.accent.copy(alpha = 0.2f),
                                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                                )
                                                
                                                Text(
                                                    text = "${(downloadProgress * 100).toInt()}%",
                                                    color = colors.textSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    if (!isDownloading && !isFetchingMetadata) {
                                        TextButton(
                                            onClick = { viewModel.setSharedYouTubeUrl(null) }
                                        ) {
                                            Text("Cancel", color = colors.accent)
                                        }
                                    }
                                }
                            )
                        }

                        // Exit confirmation Dialog
                        if (showExitDialog) {
                            AlertDialog(
                                onDismissRequest = { showExitDialog = false },
                                title = { Text("Exit", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                                text = { Text("Are you sure you want to exit?", color = colors.textPrimary) },
                                containerColor = colors.surface,
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showExitDialog = false
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                                    ) {
                                        Text("Yes", color = Color.White)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showExitDialog = false }) {
                                        Text("No", color = colors.accent)
                                    }
                                }
                            )
                        }
                    }

                    // Setup Welcome Guide Dialog overlay
                    if (!setupGuideCompleted) {
                        SetupGuideDialog(
                            colors = colors,
                            onComplete = { viewModel.setSetupGuideCompleted(true) }
                        )
                    }
                }
            }
        }
    }

    private fun extractYouTubeLink(text: String?): String? {
        if (text == null) return null
        val patterns = listOf(
            "https://(?:www\\.)?youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+)",
            "https://youtu\\.be/([a-zA-Z0-9_-]+)",
            "https://(?:www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]+)"
        )
        for (p in patterns) {
            val regex = Regex(p)
            val match = regex.find(text)
            if (match != null) {
                return match.value
            }
        }
        if (text.contains("youtube.com") || text.contains("youtu.be")) {
            val urlRegex = Regex("https?://[^\\s]+")
            return urlRegex.find(text)?.value ?: text
        }
        return null
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type
        if (android.content.Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                if (sharedText != null) {
                    val ytLink = extractYouTubeLink(sharedText)
                    if (ytLink != null) {
                        mainViewModel?.setSharedYouTubeUrl(ytLink)
                    }
                }
            }
        }
    }
}

// Keep Greeting for GreetingScreenshotTest matching
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Composable
fun SetupGuideDialog(
    colors: ColorPalette,
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(1) }
    val totalSteps = 4

    AlertDialog(
        onDismissRequest = {}, // Enforce walkthrough completion
        containerColor = colors.surface,
        title = {
            Text(
                text = when(step) {
                    1 -> "Welcome to Musicly"
                    2 -> "Precision Equalizer Tuning"
                    3 -> "Smart Crossfade & Transitions"
                    else -> "Offline Playlists & Library"
                },
                color = colors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Elegant themed icon holder
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.accent.copy(alpha = 0.12f))
                        .border(1.5.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(step) {
                            1 -> Icons.Default.MusicNote
                            2 -> Icons.Default.Tune
                            3 -> Icons.Default.AutoAwesome
                            else -> Icons.Default.QueueMusic
                        },
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Premium Material 3 Carousel Dot Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..totalSteps) {
                        val isActive = i == step
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isActive) 18.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isActive) colors.accent else colors.textSecondary.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                Text(
                    text = when(step) {
                        1 -> "Your ultimate local offline music companion. Experience a warm, natural design aesthetic combined with lightning-fast local performance, seamless transitions, and real-time scrolling lyrics."
                        2 -> "Sculpt your sound with our built-in 5-band soft-equalizer and hardware bass booster. Customize your bass thumps (60Hz), warm acoustics (230Hz), mid vocals (910Hz), presence (3.6kHz), and high brilliance (14kHz)."
                        3 -> "Eliminate silent gaps between songs automatically! Customize dynamic crossfade timings, toggle silence skipping to bypass empty outros, and view real-time synchronized karaoke-style lyrics."
                        else -> "Build local playlists and organize your audio files in real-time. Everything is indexed and cached locally inside a robust SQLite database, ensuring completely offline playback without any internet."
                    },
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step < totalSteps) {
                        step++
                    } else {
                        onComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier.testTag("welcome_next_confirm_button")
            ) {
                Text(if (step < totalSteps) "Next" else "Get Started", color = Color.White)
            }
        },
        dismissButton = {
            if (step > 1) {
                TextButton(onClick = { step-- }) {
                    Text("Back", color = colors.accent)
                }
            }
        }
    )
}
