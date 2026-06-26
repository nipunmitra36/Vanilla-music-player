package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.Song
import com.example.ui.MusicViewModel
import com.example.ui.ScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailsView(
    viewModel: MusicViewModel,
    colors: ColorPalette
) {
    val albumName by viewModel.selectedAlbum.collectAsState()
    val songs by viewModel.albumSongs.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()

    var showAddPlaylistDialogForSong by remember { mutableStateOf<Song?>(null) }
    var showCreatePlaylistDialogInsideAdd by remember { mutableStateOf<Song?>(null) }
    var newPlaylistNameInsideAdd by remember { mutableStateOf("") }

    // Determine representative artist and representative cover
    val representativeArtist = remember(songs) {
        songs.firstOrNull()?.artist ?: "Unknown Artist"
    }
    val representativeCover = remember(songs) {
        songs.firstOrNull()?.let { getFeaturedImageSource(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = albumName ?: "Album Tracks",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(ScreenState.HOME) }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!albumName.isNullOrBlank()) {
                // Large styled Header card with Album Art or placeholder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.selectedBackground)
                    ) {
                        if (representativeCover != null) {
                            AsyncImage(
                                model = representativeCover,
                                contentDescription = "Album cover",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(colors.accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = albumName?.take(1)?.uppercase() ?: "A",
                                    color = colors.accent,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = albumName ?: "",
                            color = colors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Album • $representativeArtist",
                            color = colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${songs.size} ${if (songs.size == 1) "track" else "tracks"}",
                            color = colors.textSecondary.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (songs.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.playSongFromList(songs.first(), songs) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("play_album_all_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play All", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (songs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No songs in this album.", color = colors.textSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(songs, key = { it.id }) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("album_song_row_${song.id}")
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.playSongFromList(song, songs) }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(safeParseColor(song.artworkColorHex))
                                ) {
                                    val artSource = getFeaturedImageSource(song)
                                    AsyncImage(
                                        model = artSource,
                                        contentDescription = "Track Artwork",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${song.artist} • ${String.format("%d:%02d", song.durationSeconds / 60, song.durationSeconds % 60)}",
                                        color = colors.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // 3-dot options menu for Album song row
                                Box {
                                    var showDropdownMenu by remember { mutableStateOf(false) }

                                    IconButton(
                                        onClick = { showDropdownMenu = true },
                                        modifier = Modifier.testTag("album_song_options_${song.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Song options",
                                            tint = colors.textSecondary
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showDropdownMenu,
                                        onDismissRequest = { showDropdownMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Play Now") },
                                            onClick = {
                                                viewModel.playSongFromList(song, songs)
                                                showDropdownMenu = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Play Next") },
                                            onClick = {
                                                viewModel.playNext(song)
                                                showDropdownMenu = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Queue, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add to Queue") },
                                            onClick = {
                                                viewModel.addToQueue(song)
                                                showDropdownMenu = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.AddToPhotos, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (song.isFavorite) "Remove Favorite" else "Add Favorite") },
                                            onClick = {
                                                viewModel.toggleFavorite(song)
                                                showDropdownMenu = false
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = if (song.isFavorite) androidx.compose.ui.graphics.Color(0xFFFF1744) else colors.textSecondary
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add to Playlist...") },
                                            onClick = {
                                                showDropdownMenu = false
                                                showAddPlaylistDialogForSong = song
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Album not found.", color = colors.textSecondary)
                }
            }
        }
    }

    // Modal dialog to Add to Playlist
    showAddPlaylistDialogForSong?.let { song ->
        Dialog(onDismissRequest = { showAddPlaylistDialogForSong = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Add to playlist",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = song.title,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 250.dp)
                    ) {
                        items(allPlaylists) { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.addSongToPlaylist(p.id, song)
                                        showAddPlaylistDialogForSong = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = p.name,
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showCreatePlaylistDialogInsideAdd = song }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create New", color = colors.accent, fontWeight = FontWeight.Bold)
                        }

                        TextButton(onClick = { showAddPlaylistDialogForSong = null }) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }

    // Dialog inside add playlist dialog to create a new playlist
    showCreatePlaylistDialogInsideAdd?.let { song ->
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylistDialogInsideAdd = null
                newPlaylistNameInsideAdd = ""
            },
            title = { Text("New Playlist", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistNameInsideAdd,
                    onValueChange = { newPlaylistNameInsideAdd = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedLabelColor = colors.accent,
                        focusedIndicatorColor = colors.accent
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_dialog_new_playlist_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistNameInsideAdd.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistNameInsideAdd)
                            newPlaylistNameInsideAdd = ""
                            showCreatePlaylistDialogInsideAdd = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreatePlaylistDialogInsideAdd = null
                    newPlaylistNameInsideAdd = ""
                }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }
}
