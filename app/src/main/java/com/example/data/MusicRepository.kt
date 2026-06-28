package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {
    
    private var mediaObserver: android.database.ContentObserver? = null

    // Lazy instance of room database to keep things simple and safe
    private val database: MusicDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            MusicDatabase::class.java,
            "musicly_database_v3"
        )
        .fallbackToDestructiveMigrationOnDowngrade()
        .fallbackToDestructiveMigration()
        .build()
    }

    private val songDao by lazy { database.songDao() }
    private val playlistDao by lazy { database.playlistDao() }
    private val equalizerDao by lazy { database.equalizerDao() }

    // Expose flows directly
    val allSongsFlow: Flow<List<Song>> = songDao.getAllSongsFlow()
    val allPlaylistsFlow: Flow<List<Playlist>> = playlistDao.getAllPlaylistsFlow()
    val equalizerSettingsFlow: Flow<EqualizerState?> = equalizerDao.getEqualizerStateFlow()
    val allCrossRefsFlow: Flow<List<PlaylistSongCrossRef>> = playlistDao.getAllCrossRefs()

    suspend fun initializeDatabase() = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("MusicRepository", "Initializing database...")
            // 1. Initialise baseline equalizer
            val currentEq = equalizerDao.getEqualizerState()
            if (currentEq == null) {
                android.util.Log.d("MusicRepository", "Seeding equalizer...")
                equalizerDao.saveEqualizerState(
                    EqualizerState(
                        isEnabled = true,
                        band60Hz = 0f,
                        band230Hz = 0f,
                        band910Hz = 0f,
                        band3600Hz = 0f,
                        band14000Hz = 0f,
                        bassBoostEnabled = false,
                        bassBoostStrength = 0.5f
                    )
                )
            }
            
            // 2. No default songs seeded. Ready for user's custom local & imported tracks.
            val currentSongs = songDao.getAllSongs()
            if (currentSongs.isEmpty()) {
                android.util.Log.d("MusicRepository", "Clean database initialization complete.")
            }
        } catch (e: Throwable) {
            android.util.Log.e("MusicRepository", "Error initializing database", e)
            e.printStackTrace()
        }
    }

    suspend fun getSongById(id: String): Song? = withContext(Dispatchers.IO) {
        songDao.getSongById(id)
    }

    suspend fun updateSongFavorite(songId: String, isFav: Boolean) = withContext(Dispatchers.IO) {
        val song = songDao.getSongById(songId)
        if (song != null) {
            songDao.updateSong(song.copy(isFavorite = isFav))
        }
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: Song) = withContext(Dispatchers.IO) {
        songDao.insertSongs(listOf(song))
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, song.id))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        playlistDao.deleteCrossRefsForSong(song.id)
        songDao.deleteSong(song)
        
        // Try to delete physical file if it exists
        if (song.path.isNotEmpty()) {
            try {
                val file = java.io.File(song.path)
                if (file.exists() && file.isFile) {
                    val deleted = file.delete()
                    android.util.Log.d("MusicRepository", "Physical file deleted: $deleted, path: ${song.path}")
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicRepository", "Error deleting physical file", e)
            }
        }
        
        // Track the deleted song to prevent it from reappearing during MediaStore scans
        val sharedPrefs = context.getSharedPreferences("musicly_deleted_songs", Context.MODE_PRIVATE)
        val deletedSet = sharedPrefs.getStringSet("deleted_song_ids", emptySet()) ?: emptySet()
        val newDeletedSet = deletedSet.toMutableSet().apply {
            add(song.id)
            if (song.path.isNotEmpty()) {
                add(song.path)
            }
        }
        sharedPrefs.edit().putStringSet("deleted_song_ids", newDeletedSet).apply()
    }

    suspend fun addDownloadedSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.insertSongs(listOf(song))
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return playlistDao.getSongsForPlaylistFlow(playlistId)
    }

    suspend fun getSongsForPlaylistSync(playlistId: Long): List<Song> = withContext(Dispatchers.IO) {
        playlistDao.getSongsForPlaylist(playlistId)
    }

    suspend fun getEqualizerSettings(): EqualizerState = withContext(Dispatchers.IO) {
        equalizerDao.getEqualizerState() ?: EqualizerState()
    }

    suspend fun saveEqualizerSettings(state: EqualizerState) = withContext(Dispatchers.IO) {
        equalizerDao.saveEqualizerState(state)
    }

    suspend fun scanLocalMediaStoreSongs(): List<Song> = withContext(Dispatchers.IO) {
        val existingSongs = songDao.getAllSongs()
        val existingSongsMap = existingSongs.associateBy { it.id }
        
        // Load the set of deleted songs to ignore them during scanning
        val sharedPrefs = context.getSharedPreferences("musicly_deleted_songs", android.content.Context.MODE_PRIVATE)
        val deletedSet = sharedPrefs.getStringSet("deleted_song_ids", emptySet()) ?: emptySet()
        
        val scannedList = mutableListOf<Song>()
        try {
            val contentResolver = context.contentResolver
            val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.ALBUM_ID,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.DATE_ADDED
            )
            // Search for all audio files if possible
            val selection: String? = null
            val cursor = contentResolver.query(
                uri,
                projection,
                selection,
                null,
                "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC"
            )

            android.util.Log.d("MusicRepository", "Cursor count: ${cursor?.count ?: -1}")

            cursor?.use { c ->
                val idCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DURATION)
                val albumIdCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ALBUM_ID)
                val dateAddedCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val mediaId = if (idCol >= 0) c.getLong(idCol) else -1L
                    if (mediaId == -1L) continue

                    val title = if (titleCol >= 0) c.getString(titleCol) ?: "Unknown Title" else "Unknown Title"
                    val artist = if (artistCol >= 0) c.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val album = if (albumCol >= 0) c.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val durationMs = if (durationCol >= 0) c.getLong(durationCol) else 0L
                    val albumId = if (albumIdCol >= 0) c.getLong(albumIdCol) else 0L
                    val path = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                    val dateAddedSecs = if (dateAddedCol >= 0) c.getLong(dateAddedCol) else 0L

                    android.util.Log.d("MusicRepository", "Found file: $title, Path: $path")

                    val durationSecs = if (durationMs > 0) (durationMs / 1000).toInt() else 244
                    
                    val songId = "external_$mediaId"
                    if (deletedSet.contains(songId) || (path.isNotEmpty() && deletedSet.contains(path))) {
                        android.util.Log.d("MusicRepository", "Skipping deleted song: $title (ID: $songId)")
                        continue
                    }
                    val existingSong = existingSongsMap[songId]
                    
                    if (existingSong != null) {
                        var updatedSong = existingSong
                        var needsUpdate = false
                        
                        // Preserve existing data, only update path if it changed
                        if (existingSong.path != path) {
                            updatedSong = updatedSong.copy(path = path)
                            needsUpdate = true
                        }
                        
                        // Heal missing artwork if it hasn't been extracted before
                        if (existingSong.artworkUri.isNullOrBlank()) {
                            val extractedArt = extractArtwork(context, mediaId)
                            if (!extractedArt.isNullOrBlank()) {
                                updatedSong = updatedSong.copy(artworkUri = extractedArt)
                                needsUpdate = true
                            }
                        } else if (existingSong.artworkUri?.startsWith("/") == true) {
                            val artFile = java.io.File(existingSong.artworkUri ?: "")
                            if (!artFile.exists()) {
                                val extractedArt = extractArtwork(context, mediaId)
                                if (!extractedArt.isNullOrBlank()) {
                                    updatedSong = updatedSong.copy(artworkUri = extractedArt)
                                    needsUpdate = true
                                }
                            }
                        }
                        
                        if (needsUpdate) {
                            scannedList.add(updatedSong)
                        }
                    } else {
                        // New song
                        val artworkUri = extractArtwork(context, mediaId) ?: ""

                        // Different random pleasant colors based on albumID name
                        val hueColors = listOf("#1D3F23", "#4A1D20", "#4D381B", "#1F232D", "#32163F", "#1D2C3D", "#4A181C", "#232616")
                        val artworkColorHex = hueColors[(albumId % hueColors.size).toInt().coerceAtLeast(0)]

                        // Use actual date added, generate other values
                        val dateAddedValue = dateAddedSecs * 1000
                        val dateModifiedValue = System.currentTimeMillis()
                        val playCountValue = 0
                        val recentPlayedValue = 0L

                        scannedList.add(
                            Song(
                                id = songId,
                                title = title,
                                artist = artist,
                                album = album,
                                durationSeconds = durationSecs,
                                artworkColorHex = artworkColorHex,
                                audioPreset = listOf("ambient", "romantic", "indie", "cinematic", "bouncy").random(),
                                isFavorite = false,
                                orderIndex = existingSongs.size + scannedList.size,
                                lyrics = "Enjoy custom synthesized beats with Musicly!\n\nTake deep breaths...\n\nFeel the harmony of premium sound.\n\nCrafted specifically for your offline pleasure.",
                                artworkUri = artworkUri,
                                path = path,
                                dateAdded = dateAddedValue,
                                dateModified = dateModifiedValue,
                                playCount = playCountValue,
                                recentPlayedAt = recentPlayedValue
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        if (scannedList.isNotEmpty()) {
            android.util.Log.d("MusicRepository", "Inserting/Updating ${scannedList.size} scanned songs")
            songDao.insertSongs(scannedList)
        } else {
            android.util.Log.d("MusicRepository", "No new songs scanned")
        }
        
        // Return full list from DB directly and synchronously
        songDao.getAllSongs()
    }

    private fun extractArtwork(context: Context, mediaId: Long): String? {
        var retriever: android.media.MediaMetadataRetriever? = null
        try {
            retriever = android.media.MediaMetadataRetriever()
            val songUri = android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                mediaId
            )
            retriever.setDataSource(context, songUri)
            val art = retriever.embeddedPicture
            if (art != null) {
                val artDir = java.io.File(context.filesDir, "art_images")
                if (!artDir.exists()) {
                    artDir.mkdirs()
                }
                val file = java.io.File(artDir, "art_$mediaId.jpg")
                java.io.FileOutputStream(file).use { it.write(art) }
                return file.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "Error extracting artwork for $mediaId", e)
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    suspend fun updateSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.updateSong(song)
    }

    fun registerMediaObserver(onMusicChanged: () -> Unit) {
        mediaObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                android.util.Log.d("MusicRepository", "ContentObserver onChange triggered!")
                onMusicChanged()
            }
        }
        mediaObserver = observer
        try {
            context.contentResolver.registerContentObserver(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
