package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

                var showSplash by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2500)
                    showSplash = false
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



                    AnimatedVisibility(
                        visible = showSplash,
                        enter = fadeIn(),
                        exit = fadeOut(animationSpec = tween(600))
                    ) {
                        SplashScreen(colors = colors)
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
fun SplashScreen(colors: ColorPalette) {
    // We animate background glow pulse and scale of the logo and tagline
    val infiniteTransition = rememberInfiniteTransition(label = "SplashGlow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        startAnimation = true
    }

    val scaleLogo by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.7f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "LogoScale"
    )

    val alphaContent by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1200, delayMillis = 300),
        label = "ContentAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Premium black base
        contentAlignment = Alignment.Center
    ) {
        // Blur gradient background (using canvas drawing behind)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Neon Violet gradient blob 1
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8E2DE2).copy(alpha = 0.25f * pulseGlow),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.25f, height * 0.3f),
                    radius = width * 0.7f
                )
            )
            
            // Neon Pink gradient blob 2
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF000FF).copy(alpha = 0.2f * (2f - pulseGlow)),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.75f, height * 0.7f),
                    radius = width * 0.8f
                )
            )
        }

        // Modern Glassmorphism Card
        Box(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .graphicsLayer {
                    scaleX = scaleLogo
                    scaleY = scaleLogo
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.04f)) // frosted glass look
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.02f)
                            )
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 32.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Fully filled premium logo
                MusiclyLogo(
                    modifier = Modifier
                        .size(96.dp)
                        .shadow(16.dp, CircleShape, ambientColor = Color(0xFFF000FF), spotColor = Color(0xFF8E2DE2))
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.graphicsLayer { alpha = alphaContent }
                ) {
                    // "Musicly" Text in Premium Typography
                    Text(
                        text = "Musicly",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    // Best Tagline Suggestion: "Feel Every Beat"
                    Text(
                        text = "Feel Every Beat",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Premium thin loading indicator
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    val progressTransition = rememberInfiniteTransition(label = "Progress")
                    val progressX by progressTransition.animateFloat(
                        initialValue = -100f,
                        targetValue = 100f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "progress"
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(40.dp)
                            .graphicsLayer {
                                translationX = progressX * density
                            }
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF8E2DE2),
                                        Color(0xFFF000FF)
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}


