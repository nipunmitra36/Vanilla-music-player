package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MusicViewModel
import com.example.ui.ScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    viewModel: MusicViewModel,
    colors: ColorPalette,
    modifier: Modifier = Modifier
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val scannedFilesCount by viewModel.scannedFilesCount.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var showAboutDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var lyricScrollSpeed by remember { mutableStateOf("Normal") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(ScreenState.HOME) },
                        modifier = Modifier.testTag("settings_back_button")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp)
        ) {
            // 1. Media Playback
            item {
                SettingsItem(
                    title = "Media playback",
                    subtitle = "Audio focus, equalizer",
                    icon = Icons.Default.MusicNote,
                    iconTint = Color(0xFFC75F68), // reddish circle as in screenshot 3
                    colors = colors,
                    onClick = { viewModel.navigateTo(ScreenState.EQUALIZER) }
                )
            }

            // 2. Media Store
            item {
                if (isScanning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = colors.accent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Scanning Media Store...",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = scannedFilesCount / 12f,
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.accent,
                            trackColor = colors.background
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Scanned $scannedFilesCount of 12 cache chunks.",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    SettingsItem(
                        title = "Media Store",
                        subtitle = if (scannedFilesCount > 0) "Scan completed! Added 8 tracks" else "Scan for music",
                        icon = Icons.Default.Folder,
                        iconTint = Color(0xFFC78B45), // brownish/yellowish circle as in screenshot 3
                        colors = colors,
                        onClick = { viewModel.scanMedia() }
                    )
                }
            }

            // 3. Theme & UI
            item {
                SettingsItem(
                    title = "Theme & UI",
                    subtitle = "Active: $themeMode",
                    icon = Icons.Default.GridView,
                    iconTint = Color(0xFF4C9E6A), // greenish circle as in screenshot 3
                    colors = colors,
                    onClick = { showThemeDialog = true }
                )
            }

            // 4. Lyrics
            item {
                SettingsItem(
                    title = "Lyrics",
                    subtitle = "Translation, scroll speed: $lyricScrollSpeed",
                    icon = Icons.Default.Lyrics,
                    iconTint = Color(0xFF388E9E), // teal/blueish circle as in screenshot 3
                    colors = colors,
                    onClick = { showLyricsDialog = true }
                )
            }

            // 5. About Musicly
            item {
                SettingsItem(
                    title = "About Musicly",
                    subtitle = "Version: 0.2.0-dev09",
                    icon = Icons.Default.Info,
                    iconTint = Color(0xFF3B67AD), // blue circle as in screenshot 3
                    colors = colors,
                    onClick = { showAboutDialog = true }
                )
            }

            // 6. Setup Guide
            item {
                SettingsItem(
                    title = "Run setup guide",
                    subtitle = "Welcome tutorial, reset guide configurations",
                    icon = Icons.Default.Dashboard,
                    iconTint = Color(0xFF673AB7), // purple circle
                    colors = colors,
                    onClick = { viewModel.setSetupGuideCompleted(false) }
                )
            }
        }
    }

    // Theme selector dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("App Theme", color = colors.textPrimary) },
            containerColor = colors.surface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("Dark Forest", "Slate Gray", "Midnight Blue", "Serene Alabaster").forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.changeTheme(theme)
                                    showThemeDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (themeMode == theme),
                                onClick = {
                                    viewModel.changeTheme(theme)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(theme, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = colors.accent)
                }
            }
        )
    }

    // Lyrics configuration dialog
    if (showLyricsDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            title = { Text("Lyrics settings", color = colors.textPrimary) },
            containerColor = colors.surface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Auto-Scroll Speed", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    listOf("Slow", "Normal", "Fast").forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { lyricScrollSpeed = speed; showLyricsDialog = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (lyricScrollSpeed == speed),
                                onClick = { lyricScrollSpeed = speed; showLyricsDialog = false },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(speed, color = colors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricsDialog = false }) {
                    Text("Done", color = colors.accent)
                }
            }
        )
    }

    // About Dialog
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.accent, modifier = Modifier.size(40.dp)) },
            title = { Text("Musicly", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            containerColor = colors.surface,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Version 0.2.0-dev09", color = colors.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "An elegant, high-fidelity offline music player built with Jetpack Compose. Featuring a dynamic hardware-aligned audio synthesis engine, custom 5-band soft-equalizer, reactive playlists, and scrolling real-time synced lyrics.",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text("Developer Profile", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.background)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Nipun Mitra", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Software Developer", color = colors.textSecondary, fontSize = 12.sp)
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { uriHandler.openUri("mailto:nipunmitra03@gmail.com") }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Email, contentDescription = "Email", tint = colors.accent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("nipunmitra03@gmail.com", color = colors.textPrimary, fontSize = 11.sp)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { uriHandler.openUri("https://www.linkedin.com/in/nipunmitra/") }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "LinkedIn", tint = colors.accent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("linkedin.com/in/nipunmitra", color = colors.textPrimary, fontSize = 11.sp)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { uriHandler.openUri("https://wa.me/8801761227436") }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "WhatsApp", tint = colors.accent, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+880 1761 227436 (WhatsApp)", color = colors.textPrimary, fontSize = 11.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Developed for high performance under local SQLite Room cache.", color = colors.textSecondary, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text("Dismiss", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    colors: ColorPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon circular backgrounds exactly like Screenshot 3
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 13.sp
            )
        }
    }
}
