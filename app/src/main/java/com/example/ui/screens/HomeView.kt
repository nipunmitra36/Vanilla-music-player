package com.example.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Playlist
import com.example.data.PlaylistSongCrossRef
import com.example.data.Song
import com.example.ui.MusicViewModel
import com.example.ui.ScreenState
import com.example.ui.SortMode
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    viewModel: MusicViewModel,
    colors: ColorPalette,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val activeSong by viewModel.activeSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val hasPlayedAny by viewModel.hasPlayedAny.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    
    val allSongs by viewModel.allSongs.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()
    val allCrossRefs by viewModel.allCrossRefs.collectAsState()
    val playQueue by viewModel.playQueue.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var songSelectedForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showSortingMenu by remember { mutableStateOf(false) }
    var activeSearch by remember { mutableStateOf(false) }

    var showSongActionsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    // Filtered & Sorted Songs logic
    val filteredSongs = remember(allSongs, searchQuery, sortMode) {
        val list = allSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
        val sortedList = when (sortMode) {
            SortMode.A_Z -> list.sortedBy { it.title.lowercase() }
            SortMode.Z_A -> list.sortedByDescending { it.title.lowercase() }
            SortMode.DATE_MODIFIED_NEWEST -> list.sortedByDescending { it.dateAdded }
            SortMode.DATE_MODIFIED_OLDEST -> list.sortedBy { it.dateAdded }
        }
        android.util.Log.d("HomeView", "filteredSongs size: ${sortedList.size}")
        sortedList
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val voiceSearchLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.setSearchQuery(spokenText)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    if (activeSearch) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search songs, artists...", color = colors.textSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("search_text_input"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak song or artist name...")
                                        }
                                        try {
                                            voiceSearchLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            viewModel.postInAppNotification("Speech recognition is not supported on this device.")
                                        }
                                    }) {
                                        Icon(Icons.Default.Mic, contentDescription = "Voice search", tint = colors.accent)
                                    }
                                    IconButton(onClick = {
                                        viewModel.setSearchQuery("")
                                        activeSearch = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close search", tint = colors.accent)
                                    }
                                }
                            }
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MusiclyLogo(modifier = Modifier.size(28.dp))
                            Text(
                                text = "Musicly",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                modifier = Modifier.testTag("app_title_text")
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(ScreenState.SETTINGS) },
                        modifier = Modifier.testTag("settings_drawer_button")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu Settings", tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (!activeSearch) {
                        IconButton(
                            onClick = {
                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
                                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak song or artist name...")
                                }
                                try {
                                    activeSearch = true
                                    voiceSearchLauncher.launch(intent)
                                } catch (e: Exception) {
                                    viewModel.postInAppNotification("Speech recognition is not supported on this device.")
                                }
                            },
                            modifier = Modifier.testTag("voice_search_icon_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice search", tint = colors.textPrimary)
                        }
                        IconButton(
                            onClick = { activeSearch = true },
                            modifier = Modifier.testTag("search_icon_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.textPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            if (activeSong != null && hasPlayedAny) {
                Spacer(modifier = Modifier.height(96.dp).navigationBarsPadding())
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            // Sliding category navigation tabs
            TabSelector(
                tabs = listOf("Songs", "Favorites", "Smart AI", "Most Played", "Playlists", "Albums", "Artists", "Genres", "Folders", "Media platform", "Statistics"),
                selected = selectedTab,
                colors = colors,
                onSelected = { tab ->
                    if (tab == "Media platform") {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:"))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    } else {
                        viewModel.selectTab(tab)
                    }
                }
            )

            // Content based on tab
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "TabTransition"
            ) { targetTab ->
                Box(modifier = Modifier.weight(1f)) {
                    when (targetTab) {
                        "Smart AI" -> {
                            SmartAITabContent(
                                viewModel = viewModel,
                                colors = colors
                            )
                        }
                        "Songs" -> {
                            SongsTabContent(
                                viewModel = viewModel,
                                songs = filteredSongs,
                                activeSong = activeSong,
                                colors = colors,
                                sortMode = sortMode,
                                onSortModeSelect = { viewModel.setSortMode(it) },
                                onSongSelect = { viewModel.playSongFromList(it, filteredSongs) },
                                onMoreOptions = {
                                    songSelectedForPlaylist = it
                                    showSongActionsDialog = true
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(it) }
                            )
                        }
                        "Favorites" -> {
                            val favoriteSongs = remember(allSongs) {
                                allSongs.filter { it.isFavorite }
                            }
                            FavoritesTabContent(
                                songs = favoriteSongs,
                                activeSong = activeSong,
                                colors = colors,
                                isMostPlayed = false,
                                isPlaying = isPlaying,
                                onSongSelect = { viewModel.playSongFromList(it, favoriteSongs) },
                                onMoreOptions = {
                                    songSelectedForPlaylist = it
                                    showSongActionsDialog = true
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(it) }
                            )
                        }
                        "Most Played" -> {
                            val mostPlayedSongs = remember(allSongs) {
                                allSongs.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
                            }
                            FavoritesTabContent(
                                songs = mostPlayedSongs,
                                activeSong = activeSong,
                                colors = colors,
                                isMostPlayed = true,
                                isPlaying = isPlaying,
                                onSongSelect = { viewModel.playSongFromList(it, mostPlayedSongs) },
                                onMoreOptions = {
                                    songSelectedForPlaylist = it
                                    showSongActionsDialog = true
                                },
                                onFavoriteToggle = { viewModel.toggleFavorite(it) }
                            )
                        }
                        "Playlists" -> {
                            PlaylistsTabContent(
                                playlists = playlists,
                                songs = allSongs,
                                crossRefs = allCrossRefs,
                                colors = colors,
                                onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                onPlaylistClick = { playlist ->
                                    viewModel.selectPlaylist(playlist)
                                    viewModel.navigateTo(ScreenState.PLAYLIST_DETAILS)
                                },
                                onPlaylistDelete = { viewModel.deletePlaylist(it.id) }
                            )
                        }
                        "Albums" -> {
                            AlbumsTabContent(
                                songs = allSongs,
                                colors = colors,
                                onAlbumClick = { albumName ->
                                    viewModel.selectAlbum(albumName)
                                }
                            )
                        }
                        "Artists" -> {
                            ArtistsTabContent(
                                songs = allSongs,
                                colors = colors,
                                onSongSelect = { viewModel.playSongFromList(it, allSongs) }
                            )
                        }
                        "Genres" -> {
                            GenresTabContent(
                                songs = allSongs,
                                colors = colors,
                                onSongSelect = { viewModel.playSongFromList(it, allSongs) }
                            )
                        }
                        "Folders" -> {
                            FoldersTabContent(
                                songs = allSongs,
                                colors = colors,
                                onSongSelect = { viewModel.playSongFromList(it, allSongs) }
                            )
                        }
                        "Statistics" -> {
                            StatisticsTabContent(
                                viewModel = viewModel,
                                colors = colors
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialog for associated Playlist assignment
    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Add to Playlist", color = colors.textPrimary) },
            containerColor = colors.surface,
            text = {
                LazyColumn {
                    items(playlists) { playlist ->
                        Text(
                            text = playlist.name,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    songSelectedForPlaylist?.let { song ->
                                        viewModel.addSongToPlaylist(playlist.id, song)
                                    }
                                    showPlaylistDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                    if (playlists.isEmpty()) {
                        item {
                            Text("No playlists created. Create one from the Playlists tab!", color = colors.textSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Cancel", color = colors.accent)
                }
            }
        )
    }

    // Create playlist dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist", color = colors.textPrimary) },
            containerColor = colors.surface,
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.textSecondary
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = colors.accent)
                }
            }
        )
    }

    // Song Actions Options Dialog
    if (showSongActionsDialog && songSelectedForPlaylist != null) {
        val song = songSelectedForPlaylist!!
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { showSongActionsDialog = false },
            title = { Text(song.title, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            containerColor = colors.surface,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Artist: ${song.artist}", color = colors.textSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Add to Playlist Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showSongActionsDialog = false
                                showPlaylistDialog = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = colors.accent)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Add to Playlist", color = colors.textPrimary, fontSize = 15.sp)
                    }

                    // Delete Song Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showSongActionsDialog = false
                                showDeleteConfirmationDialog = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Delete Song", color = Color.Red, fontSize = 15.sp)
                    }

                    // Toggle Favorite Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.toggleFavorite(song)
                                showSongActionsDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (song.isFavorite) Color(0xFFFF1744) else colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (song.isFavorite) "Remove from Favorites" else "Mark as Favorite",
                            color = colors.textPrimary,
                            fontSize = 15.sp
                        )
                    }

                    // Share Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val songId = song.id.substringAfter("external_").toLongOrNull() ?: return@clickable
                                val songUri = android.content.ContentUris.withAppendedId(
                                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    songId
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(android.content.Intent.EXTRA_STREAM, songUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooser = android.content.Intent.createChooser(intent, "Share Song")
                                context.startActivity(chooser)
                                showSongActionsDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = colors.accent)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Share", color = colors.textPrimary, fontSize = 15.sp)
                    }

                    // Ringtone Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val songId = song.id.substringAfter("external_").toLongOrNull() ?: return@clickable
                                val songUri = android.content.ContentUris.withAppendedId(
                                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    songId
                                )
                                // Simple approach for ringtone, might need more permissions
                                val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_RINGTONE)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone")
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, songUri)
                                }
                                context.startActivity(intent)
                                showSongActionsDialog = false
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.RingVolume, contentDescription = null, tint = colors.accent)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Set as Ringtone", color = colors.textPrimary, fontSize = 15.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSongActionsDialog = false }) {
                    Text("Cancel", color = colors.accent)
                }
            }
        )
    }

    // Delete Song Confirmation Modal
    if (showDeleteConfirmationDialog && songSelectedForPlaylist != null) {
        val song = songSelectedForPlaylist!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("Delete Song", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete this song?",
                    color = colors.textPrimary,
                    fontSize = 15.sp
                )
            },
            containerColor = colors.surface,
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSong(song)
                        showDeleteConfirmationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Yes", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                    Text("No", color = colors.accent)
                }
            }
        )
    }
}

@Composable
fun TabSelector(
    tabs: List<String>,
    selected: String,
    colors: ColorPalette,
    onSelected: (String) -> Unit
) {
    val listState = rememberLazyListState()
    
    // Auto-scroll the selected tab to be visible when it is changed
    LaunchedEffect(selected, tabs) {
        val index = tabs.indexOf(selected)
        if (index != -1) {
            listState.animateScrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(tabs) { tab ->
            val isSelected = (tab == selected)
            
            // Smooth animations for background color, content color, and borders
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) colors.accent.copy(alpha = 0.16f) else colors.surface.copy(alpha = 0.45f),
                animationSpec = tween(durationMillis = 240),
                label = "TabBgColor"
            )
            
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) colors.accent else colors.textSecondary.copy(alpha = 0.85f),
                animationSpec = tween(durationMillis = 240),
                label = "TabContentColor"
            )

            // Dynamic scale/size for active and elegant response
            val iconSizeMultiplier by animateDpAsState(
                targetValue = if (isSelected) 18.dp else 15.dp,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f),
                label = "TabIconSize"
            )

            val borderColor by animateColorAsState(
                targetValue = if (isSelected) colors.accent.copy(alpha = 0.5f) else colors.textSecondary.copy(alpha = 0.12f),
                animationSpec = tween(durationMillis = 240),
                label = "TabBorder"
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelected(tab) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("tab_$tab"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Determine premium icon matching the tab
                val itemIcon = when (tab) {
                    "Songs" -> Icons.Default.MusicNote
                    "Favorites" -> Icons.Default.Favorite
                    "Smart AI" -> Icons.Default.AutoAwesome
                    "Most Played" -> Icons.Default.Whatshot
                    "Playlists" -> Icons.Default.QueueMusic
                    "Albums" -> Icons.Default.Album
                    "Artists" -> Icons.Default.Person
                    "Genres" -> Icons.Default.Category
                    "Folders" -> Icons.Default.Folder
                    "Media platform" -> Icons.Default.PlayArrow
                    "Statistics" -> Icons.Default.BarChart
                    else -> Icons.Default.MusicNote
                }

                Icon(
                    imageVector = itemIcon,
                    contentDescription = "$tab Tab Icon",
                    tint = contentColor,
                    modifier = Modifier.size(iconSizeMultiplier)
                )
                
                Text(
                    text = tab,
                    color = contentColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsTabContent(
    viewModel: MusicViewModel,                // ADDED
    songs: List<Song>,
    activeSong: Song?,
    colors: ColorPalette,
    sortMode: SortMode,
    onSortModeSelect: (SortMode) -> Unit,
    onSongSelect: (Song) -> Unit,
    onMoreOptions: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    // Use saved state if available
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.songsListScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.songsListScrollOffset
    )
    
    // Save scroll state when it changes
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.saveSongsScrollState(index, offset)
            }
    }
    
    // Auto-scroll to active song when it changes
    LaunchedEffect(activeSong) {
        activeSong?.let { song ->
            val index = songs.indexOfFirst { it.id == song.id }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
    }

    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sorting sub-bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Library Tracks",
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${songs.size} items cached",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }

            var expanded by remember { mutableStateOf(false) }

            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.selectedBackground.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = colors.textSecondary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("sort_dropdown_trigger"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort Options",
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when (sortMode) {
                            SortMode.A_Z -> "A-Z"
                            SortMode.Z_A -> "Z-A"
                            SortMode.DATE_MODIFIED_NEWEST -> "Newest"
                            SortMode.DATE_MODIFIED_OLDEST -> "Oldest"
                        },
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand Sort Menu",
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .width(180.dp)
                        .background(colors.surface)
                        .border(
                            width = 1.dp,
                            color = colors.textSecondary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("sort_dropdown_menu")
                ) {
                    SortMode.entries.forEach { mode ->
                        val isSelected = sortMode == mode
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = when (mode) {
                                            SortMode.A_Z -> "A-Z Alphabetical"
                                            SortMode.Z_A -> "Z-A Alphabetical"
                                            SortMode.DATE_MODIFIED_NEWEST -> "Newest Added"
                                            SortMode.DATE_MODIFIED_OLDEST -> "Oldest Added"
                                        },
                                        color = if (isSelected) colors.accent else colors.textPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = colors.accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSortModeSelect(mode)
                                expanded = false
                            },
                            modifier = Modifier.testTag("sort_item_${mode.name.lowercase()}"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Song item list wrapped in Box for Back-To-Top float button
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    val isActive = (activeSong?.id == song.id)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                            .combinedClickable(
                                onClick = { onSongSelect(song) },
                                onLongClick = { onFavoriteToggle(song) }
                            )
                            .testTag("song_item_${song.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Art Thumbnail (High-fidelity 46dp size)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(safeParseColor(song.artworkColorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = getFeaturedImageSource(song),
                                    contentDescription = "Song cover",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isActive) {
                                        val isPlayingFlowState by viewModel.isPlaying.collectAsState()
                                        Icon(
                                            imageVector = if (isPlayingFlowState) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                            contentDescription = "Playing state",
                                            tint = colors.accent,
                                            modifier = Modifier.size(15.dp).padding(end = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = song.title,
                                        color = if (isActive) colors.accent else colors.textPrimary,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = song.artist,
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Right actions (dropdown overflow menu)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                IconButton(
                                    onClick = { onMoreOptions(song) },
                                    modifier = Modifier.size(32.dp).padding(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showBackToTop,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (activeSong != null) 96.dp else 24.dp, end = 24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                val activeIndex = songs.indexOfFirst { it.id == activeSong?.id }
                                if (activeIndex != -1) {
                                    listState.animateScrollToItem(activeIndex)
                                }
                            }
                        },
                        containerColor = colors.selectedBackground,
                        contentColor = colors.accent,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.size(48.dp).testTag("scroll_target_songs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = "Scroll to Playing Song",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        containerColor = colors.selectedBackground,
                        contentColor = colors.accent,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.size(48.dp).testTag("scroll_top_songs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Back to Top",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesTabContent(
    songs: List<Song>,
    activeSong: Song?,
    colors: ColorPalette,
    isMostPlayed: Boolean = false,
    isPlaying: Boolean = false,
    onSongSelect: (Song) -> Unit,
    onMoreOptions: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMostPlayed) Icons.Default.PlayArrow else Icons.Default.FavoriteBorder,
                        contentDescription = if (isMostPlayed) "No played songs" else "No favorites",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = if (isMostPlayed) "No played songs yet" else "No favorites yet",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = if (isMostPlayed) {
                        "Listen to your premium offline songs to build your personalized top tracks dashboard!"
                    } else {
                        "Tap the heart icon on any song to add it to your premium offline Favorites collection."
                    },
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    } else {
        val coroutineScope = rememberCoroutineScope()
        val listState = rememberLazyListState()
        val showBackToTop by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex > 2
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isMostPlayed) "${songs.size} premium top tracks" else "${songs.size} favorite songs",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = {
                    if (songs.isNotEmpty()) {
                        onSongSelect(songs.first())
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play favorites",
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        val isActive = (activeSong?.id == song.id)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { onSongSelect(song) },
                                    onLongClick = { onFavoriteToggle(song) }
                                )
                                .testTag("favorite_song_item_${song.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Art Thumbnail (High-fidelity 46dp size)
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(safeParseColor(song.artworkColorHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = getFeaturedImageSource(song),
                                        contentDescription = "Song cover",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isActive) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                                contentDescription = "Playing state",
                                                tint = colors.accent,
                                                modifier = Modifier.size(15.dp).padding(end = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = song.title,
                                            color = if (isActive) colors.accent else colors.textPrimary,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = song.artist,
                                        color = colors.textSecondary,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Right actions (dropdown overflow menu)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                    IconButton(
                                        onClick = { onMoreOptions(song) },
                                        modifier = Modifier.size(32.dp).padding(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showBackToTop,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (activeSong != null) 96.dp else 24.dp, end = 24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    val activeIndex = songs.indexOfFirst { it.id == activeSong?.id }
                                    if (activeIndex != -1) {
                                        listState.animateScrollToItem(activeIndex)
                                    }
                                }
                            },
                            containerColor = colors.selectedBackground,
                            contentColor = colors.accent,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(48.dp).testTag("scroll_target_favorites")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = "Scroll to Playing Song",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            containerColor = colors.selectedBackground,
                            contentColor = colors.accent,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.size(48.dp).testTag("scroll_top_favorites")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Back to Top",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsTabContent(
    playlists: List<Playlist>,
    songs: List<Song>,
    crossRefs: List<PlaylistSongCrossRef>,
    colors: ColorPalette,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPlaylistDelete: (Playlist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = onCreatePlaylistClick,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("create_playlist_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create New Playlist", color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = colors.textSecondary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No Playlists Created",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Tap the button above to start your music pool.",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(playlists.size) { index ->
                    val playlist = playlists[index]
                    
                    // Filter songs for this playlist
                    val playlistSongIds = remember(crossRefs, playlist.id) {
                        crossRefs.filter { it.playlistId == playlist.id }.map { it.songId }
                    }
                    val playlistSongs = remember(songs, playlistSongIds) {
                        songs.filter { it.id in playlistSongIds }
                    }
                    val firstSong = playlistSongs.firstOrNull()
                    val coverSource = if (firstSong != null) getFeaturedImageSource(firstSong) else null

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .testTag("playlist_card_${playlist.id}")
                            .clickable { onPlaylistClick(playlist) },
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                if (coverSource != null) {
                                    AsyncImage(
                                        model = coverSource,
                                        contentDescription = "Playlist cover",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    // Empty playlist artwork placeholder (Modern gradient with a sleek Music icon)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                                    colors = listOf(colors.accent.copy(alpha = 0.8f), colors.surface)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(Color.White.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QueueMusic,
                                                contentDescription = null,
                                                tint = colors.accent,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlistSongs.size} ${if (playlistSongs.size == 1) "song" else "songs"}",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onPlaylistDelete(playlist) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayQueueTabContent(
    playQueue: List<Song>,
    activeSong: Song?,
    colors: ColorPalette,
    onSongSelect: (Song) -> Unit
) {
    if (playQueue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No loaded queue tracks.", color = colors.textSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, bottom = 80.dp)
        ) {
            item {
                Text(
                    text = "Playing Next Queue (${playQueue.size} songs)",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            items(playQueue) { song ->
                val isActive = (activeSong?.id == song.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) colors.selectedBackground else Color.Transparent)
                        .clickable { onSongSelect(song) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(safeParseColor(song.artworkColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = getFeaturedImageSource(song),
                            contentDescription = "Queue cover",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = song.title,
                            color = if (isActive) colors.accent else colors.textPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(text = song.artist, color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsTabContent(
    songs: List<Song>,
    colors: ColorPalette,
    onAlbumClick: (String) -> Unit
) {
    val albums = remember(songs) {
        songs.groupBy { it.album }
    }

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val albumList = albums.keys.toList()
        items(albumList.size) { index ->
            val album = albumList[index]
            val albumSongs = albums[album] ?: emptyList()
            if (album == null) return@items
            val albumCover = getFeaturedImageSource(albumSongs.first())
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .testTag("album_card_${album}")
                    .clickable { onAlbumClick(album) },
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column {
                    AsyncImage(
                        model = albumCover,
                        contentDescription = "Album cover",
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(album, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${albumSongs.size} songs", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsTabContent(
    songs: List<Song>,
    colors: ColorPalette,
    onSongSelect: (Song) -> Unit
) {
    val artists = remember(songs) {
        songs.groupBy { it.artist }
    }

    var expandedArtist by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 86.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val artistList = artists.keys.toList()
        items(artistList.size) { index ->
            val artist = artistList[index]
            val artistSongs = artists[artist] ?: emptyList()
            val representativeSong = artistSongs.firstOrNull()
            val isExpanded = expandedArtist == artist

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("artist_card_${artist}")
                    .clickable { expandedArtist = if (isExpanded) null else artist },
                colors = CardDefaults.cardColors(
                    containerColor = if (isExpanded) colors.selectedBackground.copy(alpha = 0.5f) else colors.surface
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Circular Avatar with artwork or dynamic letter
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (representativeSong != null) {
                                AsyncImage(
                                    model = getFeaturedImageSource(representativeSong),
                                    contentDescription = "Artist photo placeholder",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${artistSongs.size} ${if (artistSongs.size == 1) "released track" else "released tracks"}",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = colors.textSecondary
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = colors.textSecondary.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(8.dp))

                        artistSongs.forEach { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSongSelect(song) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(safeParseColor(song.artworkColorHex).copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = colors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = String.format("%d:%02d", song.durationSeconds / 60, song.durationSeconds % 60),
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom theme color object representing our layout variations
data class ColorPalette(
    val background: Color,
    val surface: Color,
    val selectedBackground: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

fun getFeaturedImageSource(song: Song): Any {
    return if (!song.artworkUri.isNullOrBlank()) {
        if (song.artworkUri.startsWith("/")) {
            java.io.File(song.artworkUri)
        } else {
            song.artworkUri
        }
    } else {
        when (song.id) {
            "song_tauba" -> "https://images.unsplash.com/photo-1448375240586-882707db888b?q=80&w=400"
            "song_dilko" -> "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?q=80&w=400"
            "song_thik_emon" -> "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?q=80&w=400"
            "song_teri_meri" -> "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?q=80&w=400"
            "song_chupi" -> "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=400"
            "song_ay_na" -> "https://images.unsplash.com/photo-1494905998402-395d579af36f?q=80&w=400"
            "song_ranu" -> "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=400"
            "song_shibu" -> "https://images.unsplash.com/photo-1506157786151-b8491531f063?q=80&w=400"
            else -> "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=400" // General fallback music concert
        }
    }
}

fun safeParseColor(colorHex: String?, defaultColor: Color = Color(0xFF1E1E1E)): Color {
    if (colorHex.isNullOrBlank()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Throwable) {
        defaultColor
    }
}

@Composable
fun GenresTabContent(
    songs: List<Song>,
    colors: ColorPalette,
    onSongSelect: (Song) -> Unit
) {
    val genres = remember(songs) {
        songs.groupBy { song ->
            when (song.audioPreset) {
                "ambient" -> "Chill / Ambient"
                "romantic" -> "Classical / Romance"
                "indie" -> "Indie / Rock"
                "cinematic" -> "Cinematic Soundscapes"
                "bouncy" -> "Electronic / Dance"
                else -> "Pop"
            }
        }
    }

    var expandedGenre by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 86.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val genreList = genres.keys.toList()
        items(genreList.size) { index ->
            val genre = genreList[index]
            val genreSongs = genres[genre] ?: emptyList()
            val isExpanded = expandedGenre == genre

            // Select specialized gradient based on genre
            val gradientBrush = remember(genre) {
                val startColor = when (genre) {
                    "Chill / Ambient" -> Color(0xFF004D40) // Soft Forest Teal
                    "Classical / Romance" -> Color(0xFF4A148C) // Wine Purple
                    "Indie / Rock" -> Color(0xFF1A237E) // Deep Indigo
                    "Cinematic Soundscapes" -> Color(0xFF263238) // Gunmetal Slate
                    "Electronic / Dance" -> Color(0xFF3E2723) // Rich Umber
                    else -> Color(0xFF0D47A1) // Cyan Blue
                }
                val endColor = when (genre) {
                    "Chill / Ambient" -> Color(0xFF00897B)
                    "Classical / Romance" -> Color(0xFF880E4F)
                    "Indie / Rock" -> Color(0xFFE65100)
                    "Cinematic Soundscapes" -> Color(0xFF455A64)
                    "Electronic / Dance" -> Color(0xFFBF360C)
                    else -> Color(0xFF00B0FF)
                }
                androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(startColor, endColor))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("genre_card_${genre}")
                    .clickable { expandedGenre = if (isExpanded) null else genre },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Header Area with linear gradient representing the vibe
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brush = gradientBrush)
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = genre,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "${genreSongs.size} tracks",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = Color.White
                            )
                        }
                    }

                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            genreSongs.forEach { song ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSongSelect(song) }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Track",
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            color = colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = String.format("%d:%02d", song.durationSeconds / 60, song.durationSeconds % 60),
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersTabContent(
    songs: List<Song>,
    colors: ColorPalette,
    onSongSelect: (Song) -> Unit
) {
    val folders = remember(songs) {
        songs.groupBy { song ->
            when (song.id.hashCode() % 3) {
                0 -> "/storage/emulated/0/Music/Downloads"
                1 -> "/storage/emulated/0/Music/Favorites"
                else -> "/storage/emulated/0/Music/Sync"
            }
        }
    }

    var expandedFolder by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 86.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val folderList = folders.keys.toList()
        items(folderList.size) { index ->
            val folder = folderList[index]
            val folderSongs = folders[folder] ?: emptyList()
            val isExpanded = expandedFolder == folder

            // Simulated folder storage size calculation (e.g. ~3.8 MB per track)
            val computedSize = remember(folderSongs) {
                String.format("%.1f MB", folderSongs.size * 3.8)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("folder_card_${folder}")
                    .clickable { expandedFolder = if (isExpanded) null else folder },
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Folder",
                                tint = colors.accent,
                                modifier = Modifier.size(28.dp)
                                )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            val folderName = folder.substringAfterLast("/")
                            Text(
                                text = folderName,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = folder,
                                color = colors.textSecondary.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                            Text(
                                text = "${folderSongs.size} songs • $computedSize",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = colors.textSecondary
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = colors.textSecondary.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(8.dp))

                        folderSongs.forEach { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSongSelect(song) }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Audio track",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${song.artist} • ${String.format("%d:%02d", song.durationSeconds / 60, song.durationSeconds % 60)}",
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
