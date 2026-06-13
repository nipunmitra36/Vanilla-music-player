package com.example.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    viewModel: MusicViewModel,
    colors: ColorPalette,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortDescending by viewModel.sortDescending.collectAsState()
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

    var showDownloadDialog by remember { mutableStateOf(false) }
    var isDownloadingSimulated by remember { mutableStateOf(false) }
    var downloadCustomTitle by remember { mutableStateOf("") }
    var downloadCustomArtist by remember { mutableStateOf("") }

    // Filtered & Sorted Songs logic
    val filteredSongs = remember(allSongs, searchQuery, sortDescending) {
        var list = allSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
        list = if (sortDescending) {
            list.sortedByDescending { it.id }
        } else {
            list.sortedBy { it.id }
        }
        list
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    if (activeSearch) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search songs, artists...", color = colors.textSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
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
                        Text(
                            text = when(selectedTab) {
                                "Songs" -> "Songs"
                                "Playlists" -> "Playlists"
                                "Play queue" -> "Play Queue"
                                "Albums" -> "Albums"
                                "Artists" -> "Artists"
                                else -> "Vanilla"
                            },
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            modifier = Modifier.testTag("app_title_text")
                        )
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
            // Sticky Bottom Mini Player
            activeSong?.let { song ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(colors.surface)
                        .clickable { viewModel.navigateTo(ScreenState.NOW_PLAYING) }
                        .testTag("mini_player_bar")
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
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(safeParseColor(song.artworkColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = getFeaturedImageSource(song),
                                contentDescription = "Active song cover",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent)
                            )
                            if (song.artworkUri.isNullOrBlank() && song.id.startsWith("external")) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Text Details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
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

                        // Play/Pause button
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(colors.selectedBackground, RoundedCornerShape(22.dp))
                                .testTag("mini_play_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Next button
                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier.testTag("mini_next_button")
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Skip Next",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
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
                tabs = listOf("Songs", "Favorites", "Playlists", "Play queue", "Albums", "Artists"),
                selected = selectedTab,
                colors = colors,
                onSelected = { viewModel.selectTab(it) }
            )

            // Content based on tab
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    "Songs" -> {
                        SongsTabContent(
                            songs = filteredSongs,
                            activeSong = activeSong,
                            colors = colors,
                            sortDescending = sortDescending,
                            onToggleSort = { viewModel.toggleSortOrder() },
                            onSongSelect = { viewModel.setSong(it) },
                            onMoreOptions = {
                                songSelectedForPlaylist = it
                                showSongActionsDialog = true
                            },
                            onFavoriteToggle = { viewModel.toggleFavorite(it) },
                            onDownloadClick = { showDownloadDialog = true }
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
                            onSongSelect = { viewModel.setSong(it) },
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
                                viewModel.navigateTo(ScreenState.NOW_PLAYING) // or view details
                            },
                            onPlaylistDelete = { viewModel.deletePlaylist(it.id) }
                        )
                    }
                    "Play queue" -> {
                        PlayQueueTabContent(
                            playQueue = playQueue,
                            activeSong = activeSong,
                            colors = colors,
                            onSongSelect = { viewModel.setSong(it) }
                        )
                    }
                    "Albums" -> {
                        AlbumsTabContent(
                            songs = allSongs,
                            colors = colors,
                            onSongSelect = { viewModel.setSong(it) }
                        )
                    }
                    "Artists" -> {
                        ArtistsTabContent(
                            songs = allSongs,
                            colors = colors,
                            onSongSelect = { viewModel.setSong(it) }
                        )
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
                                        viewModel.addSongToPlaylist(playlist.id, song.id)
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

    // Simulated cloud download online track dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDownloadingSimulated) showDownloadDialog = false },
            title = { Text("Cloud Digital Downloads", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            containerColor = colors.surface,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDownloadingSimulated) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            Text("Downloading & caching...", color = colors.textPrimary, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(12.dp))
                            CircularProgressIndicator(color = colors.accent)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Fetching high-fidelity cover artwork & scrolling lyrics...", color = colors.textSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    } else {
                        Text(
                            text = "Download premium free high-fidelity songs offline. Album artwork & synchronized scrolling lyrics are automatically resolved!",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        
                        Divider(color = colors.textSecondary.copy(alpha = 0.2f))
                        
                        Text("Curated Hot Online Releases", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                        
                        val curators = listOf(
                            Pair("Kesariya", "Arijit Singh"),
                            Pair("Pasoori", "Ali Sethi & Shae Gill"),
                            Pair("Senorita", "Shawn Mendes"),
                            Pair("Shape of You", "Ed Sheeran"),
                            Pair("Believer", "Imagine Dragons")
                        )
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            curators.forEach { (title, artist) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surface)
                                        .clickable {
                                            isDownloadingSimulated = true
                                            // Launch fake download delay to show animation
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(2000)
                                                viewModel.downloadCustomSong(title, artist)
                                                isDownloadingSimulated = false
                                                showDownloadDialog = false
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(artist, color = colors.textSecondary, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        
                        Divider(color = colors.textSecondary.copy(alpha = 0.2f))
                        
                        Text("Download Custom Song", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                        
                        OutlinedTextField(
                            value = downloadCustomTitle,
                            onValueChange = { downloadCustomTitle = it },
                            label = { Text("Track Title") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.textSecondary
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = downloadCustomArtist,
                            onValueChange = { downloadCustomArtist = it },
                            label = { Text("Artist Name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.textSecondary
                            ),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (downloadCustomTitle.isNotBlank() && downloadCustomArtist.isNotBlank()) {
                                    isDownloadingSimulated = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        viewModel.downloadCustomSong(downloadCustomTitle, downloadCustomArtist)
                                        downloadCustomTitle = ""
                                        downloadCustomArtist = ""
                                        isDownloadingSimulated = false
                                        showDownloadDialog = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = downloadCustomTitle.isNotBlank() && downloadCustomArtist.isNotBlank()
                        ) {
                            Text("Download Custom Track", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                if (!isDownloadingSimulated) {
                    TextButton(onClick = { showDownloadDialog = false }) {
                        Text("Cancel", color = colors.accent)
                    }
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
    songs: List<Song>,
    activeSong: Song?,
    colors: ColorPalette,
    sortDescending: Boolean,
    onToggleSort: () -> Unit,
    onSongSelect: (Song) -> Unit,
    onMoreOptions: (Song) -> Unit,
    onFavoriteToggle: (Song) -> Unit,
    onDownloadClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
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
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clickable { onToggleSort() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Modified time",
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = "Sort toggle",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDownloadClick() }) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Download Music",
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = { onToggleSort() }) {
                    Icon(
                        imageVector = Icons.Default.SortByAlpha,
                        contentDescription = "Sort alphabetical",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = {
                    if (songs.isNotEmpty()) {
                        onSongSelect(songs.first())
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play all",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
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
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(16.dp))
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
                                if (song.artworkUri.isNullOrBlank() && song.id.startsWith("external")) {
                                    Text(
                                        text = song.title.take(1).uppercase(),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
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

                            // Right actions (Favorites icon + dropdown overflow menu)
                            IconButton(onClick = { onFavoriteToggle(song) }) {
                                Icon(
                                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (song.isFavorite) Color.Red else colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

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
                    .padding(bottom = 24.dp, end = 24.dp)
            ) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesTabContent(
    songs: List<Song>,
    activeSong: Song?,
    colors: ColorPalette,
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
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "No favorites",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Text(
                    text = "No favorites yet",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Tap the heart icon on any song to add it to your premium offline Favorites collection.",
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
                    text = "${songs.size} favorite songs",
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
                                    if (song.artworkUri.isNullOrBlank() && song.id.startsWith("external")) {
                                        Text(
                                            text = song.title.take(1).uppercase(),
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
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

                                // Right actions (Favorites icon + dropdown overflow menu)
                                IconButton(onClick = { onFavoriteToggle(song) }) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Remove Favorite",
                                        tint = Color.Red,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

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
                        .padding(bottom = 24.dp, end = 24.dp)
                ) {
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
                        if (song.artworkUri.isNullOrBlank() && song.id.startsWith("external")) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                        }
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 80.dp)
    ) {
        items(albums.keys.toList()) { album ->
            val albumSongs = albums[album] ?: emptyList()
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
                            Icon(Icons.Default.Album, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(album, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${albumSongs.size} songs", color = colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    albumSongs.forEach { song ->
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
        song.artworkUri
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
