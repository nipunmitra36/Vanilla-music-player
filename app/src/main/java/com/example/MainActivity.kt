package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
                }

                val selectedTab by viewModel.selectedTab.collectAsState()
                var showExitDialog by remember { mutableStateOf(false) }

                BackHandler(enabled = true) {
                    when (viewModel.currentScreen.value) {
                        ScreenState.PLAYLIST_DETAILS -> viewModel.navigateTo(ScreenState.HOME)
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
                            }
                        }

                        // Mini Player
                        if (currentScreen != ScreenState.NOW_PLAYING) {
                            val activeSong by viewModel.activeSong.collectAsState()
                            val isPlaying by viewModel.isPlaying.collectAsState()
                            
                            if (activeSong != null) {
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

    AlertDialog(
        onDismissRequest = {}, // Enforce walkthrough completion
        containerColor = colors.surface,
        title = {
            Text(
                text = when(step) {
                    1 -> "Welcome to Musicly"
                    2 -> "Hi-Fi Equalizer Synth"
                    else -> "Offline Playlists Pool"
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(step) {
                            1 -> Icons.Default.MusicNote
                            2 -> Icons.Default.Tune
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = when(step) {
                        1 -> "Your gorgeous, high-fidelity local offline player. Experience lag-free transitions, synchronized lyrics, and forest-green design aesthetics."
                        2 -> "Adjust 60Hz bass thumps, warm 230Hz chords, 910Hz mid pads, 3600Hz details, and crisp 14000Hz sparkles. Sourced in real-time PCM synthesis."
                        else -> "Organize, queue, and manage individual pools completely client-side without internet. Long-press any song item to favorite instantly."
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
                    if (step < 3) {
                        step++
                    } else {
                        onComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier.testTag("welcome_next_confirm_button")
            ) {
                Text(if (step < 3) "Next" else "Get Started", color = Color.White)
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
