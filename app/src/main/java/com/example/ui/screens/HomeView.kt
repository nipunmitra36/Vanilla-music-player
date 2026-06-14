package com.example.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
    
    val coroutineScope = rememberCoroutineScope()
    
    val allSongs by viewModel.allSongs.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()
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
                                IconButton(onClick = {
                                    viewModel.setSearchQuery("")
                                    activeSearch = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search", tint = colors.accent)
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
            if (activeSong != null) {
                Spacer(modifier = Modifier.height(96.dp).navigationBarsPadding())
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sliding category navigation tabs
            TabSelector(
                tabs = listOf("Songs", "Favorites", "Most Played", "Playlists", "Albums", "Artists", "Genres", "Folders"),
                selected = selectedTab,
                colors = colors,
                onSelected = { viewModel.selectTab(it) }
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
                                onSongSelect = { viewModel.playSongFromList(it, allSongs) }
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
                            tint = if (song.isFavorite) Color.Red else colors.textSecondary
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
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs) { tab ->
            val isSelected = (tab == selected)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) colors.selectedBackground else Color.Transparent)
                    .clickable { onSelected(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("tab_$tab")
            ) {
                Text(
                    text = tab,
                    color = if (isSelected) colors.accent else colors.textSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort By",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Sort By (${songs.size})",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Dropdown Menu Anchor
            var expanded by remember { mutableStateOf(false) }
            val currentLabel = when (sortMode) {
                SortMode.A_Z -> "A–Z"
                SortMode.Z_A -> "Z–A"
                SortMode.DATE_MODIFIED_NEWEST -> "Newest First"
                SortMode.DATE_MODIFIED_OLDEST -> "Oldest First"
            }

            Box {
                // Better trigger UI
                Button(
                    onClick = { expanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.selectedBackground),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp).testTag("sort_dropdown_trigger")
                ) {
                    Text(
                        text = currentLabel,
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Expand Sort Menu",
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(colors.surface)
                        .testTag("sort_dropdown_menu")
                ) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = when (mode) {
                                        SortMode.A_Z -> "A-Z"
                                        SortMode.Z_A -> "Z-A"
                                        SortMode.DATE_MODIFIED_NEWEST -> "Newest First"
                                        SortMode.DATE_MODIFIED_OLDEST -> "Oldest First"
                                    },
                                    color = if (sortMode == mode) colors.accent else colors.textPrimary,
                                    fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            },
                            onClick = {
                                onSortModeSelect(mode)
                                expanded = false
                            },
                            modifier = Modifier.testTag("sort_item_${mode.name.lowercase()}")
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
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    val isActive = (activeSong?.id == song.id)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) colors.selectedBackground.copy(alpha = 0.8f) else Color.Transparent)
                            .combinedClickable(
                                onClick = { onSongSelect(song) },
                                onLongClick = { onFavoriteToggle(song) }
                            )
                            .testTag("song_item_${song.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Art Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(12.dp))
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

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = if (isActive) colors.accent else colors.textPrimary,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = song.artist,
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
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
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        val isActive = (activeSong?.id == song.id)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isActive) colors.selectedBackground.copy(alpha = 0.8f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { onSongSelect(song) },
                                    onLongClick = { onFavoriteToggle(song) }
                                )
                                .testTag("favorite_song_item_${song.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Art Thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
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

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = if (isActive) colors.accent else colors.textPrimary,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = song.artist,
                                        color = colors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Right actions (dropdown overflow menu)
                                IconButton(onClick = { onMoreOptions(song) }) {
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist) },
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
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
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.selectedBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = colors.accent)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = playlist.name,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Custom offline pool",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            IconButton(onClick = { onPlaylistDelete(playlist) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.textSecondary)
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
    onSongSelect: (Song) -> Unit
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
                modifier = Modifier.fillMaxWidth().aspectRatio(0.85f).clickable { /* TODO: show songs of album */ },
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 80.dp)
    ) {
        items(artists.keys.toList()) { artist ->
            val artistSongs = artists[artist] ?: emptyList()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(colors.selectedBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(artist, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Publisher & Artist", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    artistSongs.forEach { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSongSelect(song) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(song.title, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 80.dp)
    ) {
        items(genres.keys.toList()) { genre ->
            val genreSongs = genres[genre] ?: emptyList()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(genre, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${genreSongs.size} songs", color = colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    genreSongs.forEach { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSongSelect(song) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(song.title, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 80.dp)
    ) {
        items(folders.keys.toList()) { folder ->
            val folderSongs = folders[folder] ?: emptyList()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            val folderName = folder.substringAfterLast("/")
                            Text(folderName, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(folder, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${folderSongs.size} songs", color = colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    folderSongs.forEach { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSongSelect(song) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(song.title, color = colors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
