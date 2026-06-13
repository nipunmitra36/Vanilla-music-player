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
            "vanilla_music_database_v3"
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

            // 2. Initialise songs if none exist in Room
            val firstSong = songDao.getFirstSong()
            if (firstSong == null) {
                val defaultSongs = listOf(
                    Song(
                        id = "song_tauba",
                        title = "Tauba Tumhare Ishare Full Song",
                        artist = "T-Series",
                        album = "Tauba Collection",
                        durationSeconds = 244,
                        artworkColorHex = "#1D3F23", // Dark Green-forest
                        audioPreset = "bouncy",
                        orderIndex = 0,
                        lyrics = "Tauba tumhare ishare...\n\nDil ko churaye dobara...\n\nNazar se nazar mila ke...\n\nChain churaya hamara!\n\n(Music Drop)\n\nO jaana jaana jaana...\n\nDil de diya tumko jaana...\n\nSoche bina humne dil de diya...\n\nTum jaise masum ko...\n\nDil de diya...\n\nTauba tumhare ishare..."
                    ),
                    Song(
                        id = "song_dilko",
                        title = "Dil Ko Karaar Aaya - Sidharth Shukla",
                        artist = "Desi Music Factory",
                        album = "Karaar",
                        durationSeconds = 260,
                        artworkColorHex = "#4A1D20", // Deep Claret
                        audioPreset = "romantic",
                        orderIndex = 1,
                        lyrics = "Dil ko karaar aaya...\n\nTujhpe hi pyaar aaya...\n\nPehli pehli baar aaya...\n\nO yaara...\n\n(String Interlude)\n\nSath mein tere jiyun main...\n\nHar lamha sath mein tera...\n\nKhwabon ka aashiyaana hamara...\n\nDuniya se dur hai pyara yaara..."
                    ),
                    Song(
                        id = "song_thik_emon",
                        title = "Lyrical Thik Emon Ebhabe",
                        artist = "REVIEW With RAY",
                        album = "Indie Sessions",
                        durationSeconds = 185,
                        artworkColorHex = "#4D381B", // Warm Bronze
                        audioPreset = "indie",
                        orderIndex = 2,
                        lyrics = "Thik emon ebhabe...\n\nTumi aamar kache thako...\n\nBuker bhitore jorie...\n\nChupi chupi gie dako...\n\nJeo na chere dur...\n\nAamar choto gaaner sur...\n\nAkasher oi bhalobasha...\n\nEmoni thak aamar bhitor..."
                    ),
                    Song(
                        id = "song_teri_meri",
                        title = "Teri Meri Kahaani 8K | Gabbar",
                        artist = "Zee Music Company",
                        album = "Gabbar",
                        durationSeconds = 215,
                        artworkColorHex = "#1F232D", // Deep Slate
                        audioPreset = "cinematic",
                        orderIndex = 3,
                        lyrics = "Teri meri kahaani...\n\nAdhoori hai jawani...\n\nKoshish karke dekh le humnava...\n\nSath mein likhenge ye dastan...\n\n(Epic Cinematic Pad)\n\nHath pakad ke chalo na pyara...\n\nDuniya haseen lagne lagega..."
                    ),
                    Song(
                        id = "song_chupi",
                        title = "Chupi Chupi Mon | Love Song",
                        artist = "Surinder Films",
                        album = "Sweet Romantics",
                        durationSeconds = 198,
                        artworkColorHex = "#32163F", // Royal Plum
                        audioPreset = "pentatonic",
                        orderIndex = 4,
                        lyrics = "Chupi chupi mon...\n\nHoye gache aapon...\n\nKothai tumi thako...\n\nKotha keno bolo na mon...\n\nO priyo bandhu...\n\nHridoyero majhe rekhechi...\n\nBondhu tumi jeno re..."
                    ),
                    Song(
                        id = "song_ay_na",
                        title = "Ay Na Aro Kache | Love Story",
                        artist = "Surinder Films",
                        album = "Sweet Romantics",
                        durationSeconds = 232,
                        artworkColorHex = "#1D2C3D", // Charcoal Teal
                        audioPreset = "ambient",
                        orderIndex = 5,
                        lyrics = "Ay na aro kache tumi...\n\nHridoy aamar dake...\n\nShara dao ebar...\n\nPhirie dio na aamake...\n\nOgo Swapna charini...\n\nTumi hridoyer rani...\n\nAkashe batase tor i gaan dake..."
                    ),
                    Song(
                        id = "song_ranu",
                        title = "RANU BAMBAI KI RANU ...",
                        artist = "Talha Circuit ♚ Official",
                        album = "Lyrical Beats",
                        durationSeconds = 165,
                        artworkColorHex = "#4A181C", // Deep Crimson
                        audioPreset = "bouncy",
                        orderIndex = 6,
                        lyrics = "Ranu bambai ki ranu...\n\nNache jb dhol baje...\n\nBambai ki galli galli...\n\nTeri hi dastan saje...\n\n(Bass Drop Beats)\n\nRanu bambai ki ranu...\n\nNachche dekho sotti subho saje..."
                    ),
                    Song(
                        id = "song_shibu",
                        title = "Shibu - 10 E 10",
                        artist = "Shibu",
                        album = "Shibu Album",
                        durationSeconds = 202,
                        artworkColorHex = "#232616", // Olivine charcoal
                        audioPreset = "indie",
                        orderIndex = 7,
                        lyrics = "Shibu re 10 e 10 paabi...\n\nShikhe ne bhalo kore...\n\nNa hole pichone thakbi...\n\nJibon ta jabe more...\n\n(Arpeggiator drop)\n\nShibu re tor i jonmodin...\n\nKhub anande katuk saradin..."
                    )
                )
                songDao.insertSongs(defaultSongs)
                
                // Create a default playlist to start out with
                val playlistId = playlistDao.insertPlaylist(Playlist(name = "Chill Vibes"))
                playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, "song_tauba"))
                playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, "song_dilko"))
                playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, "song_thik_emon"))
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

    suspend fun addSongToPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
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
                android.provider.MediaStore.Audio.Media.ALBUM_ID
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

                while (c.moveToNext()) {
                    val mediaId = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: "Unknown Title"
                    val artist = c.getString(artistCol) ?: "Unknown Artist"
                    val album = c.getString(albumCol) ?: "Unknown Album"
                    val durationMs = c.getLong(durationCol)
                    val albumId = c.getLong(albumIdCol)

                    val durationSecs = if (durationMs > 0) (durationMs / 1000).toInt() else 244
                    
                    // Construct standard Android Content Uri for album artwork
                    val artworkUri = android.content.ContentUris.withAppendedId(
                        android.net.Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

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
                            lyrics = "This local track coordinates on physical PCM storage. Enjoy Vanilla Music!",
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
