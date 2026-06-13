package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {
    
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
            // 1. Initialise baseline equalizer
            val currentEq = equalizerDao.getEqualizerState()
            if (currentEq == null) {
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
        } catch (e: Throwable) {
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
                android.provider.MediaStore.Audio.Media.DATA
            )
            // Only search for music files
            val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor = contentResolver.query(
                uri,
                projection,
                selection,
                null,
                "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                val albumIdCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)

                val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)

                while (c.moveToNext()) {
                    val mediaId = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Unknown Title"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val album = c.getString(albumCol) ?: "Unknown Album"
                    val durationMs = c.getLong(durationCol)
                    val albumId = c.getLong(albumIdCol)
                    val path = c.getString(dataCol)

                    val durationSecs = if (durationMs > 0) (durationMs / 1000).toInt() else 244
                    
                    var artworkUri: String? = null
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(path)
                        val art = retriever.embeddedPicture
                        if (art != null) {
                            val file = java.io.File(context.cacheDir, "art_$mediaId.jpg")
                            if (!file.exists()) {
                                java.io.FileOutputStream(file).use { it.write(art) }
                            }
                            artworkUri = file.absolutePath
                        }
                        retriever.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (artworkUri == null) {
                        artworkUri = ""
                    }

                    // Different random pleasant colors based on albumID name
                    val hueColors = listOf("#1D3F23", "#4A1D20", "#4D381B", "#1F232D", "#32163F", "#1D2C3D", "#4A181C", "#232616")
                    val artworkColorHex = hueColors[(albumId % hueColors.size).toInt().coerceAtLeast(0)]

                    scannedList.add(
                        Song(
                            id = "external_$mediaId",
                            title = title,
                            artist = artist,
                            album = album,
                            durationSeconds = durationSecs,
                            artworkColorHex = artworkColorHex,
                            audioPreset = listOf("ambient", "romantic", "indie", "cinematic", "bouncy").random(),
                            isFavorite = false,
                            orderIndex = scannedList.size,
                            lyrics = "This local track coordinates on physical PCM storage. Enjoy Musicly!",
                            artworkUri = artworkUri
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        if (scannedList.isNotEmpty()) {
            songDao.insertSongs(scannedList)
        }
        scannedList
    }
}
