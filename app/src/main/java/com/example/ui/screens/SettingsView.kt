package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
            
            // 7. Scan Songs
            item {
                SettingsItem(
                    title = "Scan Songs",
                    subtitle = if (isScanning) "Scanning... (${scannedFilesCount} files)" else "Rescan local media files",
                    icon = Icons.Default.Refresh,
                    iconTint = Color(0xFFE67E22), 
                    colors = colors,
                    onClick = { viewModel.scanMedia() }
                )
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
                    subtitle = "Version: 0.2.1",
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
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Themes Icon",
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "App Theme",
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = colors.surface,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select a visual style to personalize your player experience.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val themesList = listOf(
                        ThemeInfo("Dark Forest", Color(0xFF131914), Color(0xFF5FA169), "Spruce green tones"),
                        ThemeInfo("Slate Gray", Color(0xFF1E1E22), Color(0xFF8A9FB4), "Cold titanium steel"),
                        ThemeInfo("Midnight Blue", Color(0xFF111827), Color(0xFF3B82F6), "Electric ocean depth"),
                        ThemeInfo("Obsidian Crimson", Color(0xFF181818), Color(0xFFFF0033), "Sleek obsidian crimson"),
                        ThemeInfo("Serene Alabaster", Color(0xFFFFFFFF), Color(0xFF0F766E), "Pristine minimalist light")
                    )

                    themesList.forEach { t ->
                        val isSelected = themeMode == t.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) colors.selectedBackground else Color.Transparent)
                                .clickable {
                                    viewModel.changeTheme(t.name)
                                    showThemeDialog = false
                                }
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) colors.accent else colors.textSecondary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.changeTheme(t.name)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.accent)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = t.name,
                                    color = if (isSelected) colors.accent else colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = t.description,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // Color swatches preview
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.background.copy(alpha = 0.4f))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(t.surfaceColor)
                                        .border(0.5.dp, colors.textPrimary.copy(alpha = 0.2f), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(t.accentColor)
                                        .border(0.5.dp, colors.textPrimary.copy(alpha = 0.2f), CircleShape)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = colors.accent, fontWeight = FontWeight.SemiBold)
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
            icon = { MusiclyLogo(modifier = Modifier.size(56.dp)) },
            title = { Text("Musicly", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            containerColor = colors.surface,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Version 0.2.1", color = colors.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.background)
                            .border(1.dp, colors.textSecondary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Premium Developer Avatar Image
                        Image(
                            painter = painterResource(id = com.example.R.drawable.img_profile_avatar),
                            contentDescription = "Nipun Mitra",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, colors.accent, CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        // Verified Name Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("Nipun Mitra", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            // Blue Verified Badge!
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2196F3)), // verified blue
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Verified Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(9.dp)
                                )
                            }
                        }

                        Text("Software Developer", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Styled Premium Contact Pill Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.textSecondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .clickable { uriHandler.openUri("mailto:nipunmitra03@gmail.com") }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = "Email", tint = colors.accent, modifier = Modifier.size(16.dp))
                            Text("nipunmitra03@gmail.com", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.textSecondary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .clickable { uriHandler.openUri("https://www.linkedin.com/in/nipunmitra/") }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "LinkedIn", tint = colors.accent, modifier = Modifier.size(16.dp))
                            Text("linkedin.com/in/nipunmitra", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        

                    }
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

data class ThemeInfo(
    val name: String,
    val surfaceColor: Color,
    val accentColor: Color,
    val description: String
)
