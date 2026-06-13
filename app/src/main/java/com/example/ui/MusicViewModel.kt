package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ScreenState {
    HOME,
    NOW_PLAYING,
    EQUALIZER,
    SETTINGS
}

enum class RepeatMode {
    NONE,
    ALL,
    ONE
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)
    private val audioEngine = AudioEngine.getInstance(application)

    // Database Flows
    val allSongs = repository.allSongsFlow
        .catch { e ->
            android.util.Log.e("MusicViewModel", "Database error collecting songs", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists = repository.allPlaylistsFlow
        .catch { e ->
            android.util.Log.e("MusicViewModel", "Database error collecting playlists", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active state flows
    private val _currentScreen = MutableStateFlow(ScreenState.HOME)
    val currentScreen = _currentScreen.asStateFlow()

    private val _selectedTab = MutableStateFlow("Songs") // Playlists, Play queue, Songs, Albums, Artists
    val selectedTab = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortDescending = MutableStateFlow(true)
    val sortDescending = _sortDescending.asStateFlow()

    private val _activeSong = MutableStateFlow<Song?>(null)
    val activeSong = _activeSong.asStateFlow()

    private val _playQueue = MutableStateFlow<List<Song>>(emptyList())
    val playQueue = _playQueue.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode = _repeatMode.asStateFlow()

    // Observed from synthesis player
    val isPlaying = audioEngine.isPlaying
    val currentPositionSeconds = audioEngine.currentPositionSeconds
    val trackDurationSeconds = audioEngine.trackDurationSeconds

    // Local Equalizer State cache
    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState = _equalizerState.asStateFlow()

    // Playlist details
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    val playlistSongs = _selectedPlaylist.flatMapLatest { playlist ->
        if (playlist == null) flowOf(emptyList())
        else repository.getSongsForPlaylist(playlist.id)
    }
    .catch { e ->
        android.util.Log.e("MusicViewModel", "Error in playlist songs flow", e)
        emit(emptyList())
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Lyrics configuration
    private val _isLyricsExpanded = MutableStateFlow(false)
    val isLyricsExpanded = _isLyricsExpanded.asStateFlow()

    // Scanning progress/Settings mock state
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scannedFilesCount = MutableStateFlow(0)
    val scannedFilesCount = _scannedFilesCount.asStateFlow()

    private val _themeMode = MutableStateFlow("Dark Forest") // Dark Forest, Slate Gray, Midnight Blue
    val themeMode = _themeMode.asStateFlow()

    private val _setupGuideCompleted = MutableStateFlow(true)
    val setupGuideCompleted = _setupGuideCompleted.asStateFlow()

    init {
        // Wire completion listener to handle next track or looping automatically based on repeat settings
        audioEngine.setOnSongCompletionListener {
            viewModelScope.launch {
                val mode = _repeatMode.value
                val current = _activeSong.value
                val queue = _playQueue.value
                if (current == null || queue.isEmpty()) return@launch

                when (mode) {
                    RepeatMode.ONE -> {
                        seekTo(0)
                        audioEngine.play()
                    }
                    RepeatMode.ALL -> {
                        val index = queue.indexOfFirst { it.id == current.id }
                        if (index != -1) {
                            val nextIndex = (index + 1) % queue.size
                            setSong(queue[nextIndex])
                        }
                    }
                    RepeatMode.NONE -> {
                        val index = queue.indexOfFirst { it.id == current.id }
                        if (index != -1) {
                            if (index + 1 < queue.size) {
                                setSong(queue[index + 1])
                            } else {
                                audioEngine.pause()
                                seekTo(0)
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            try {
                // Seed database
                repository.initializeDatabase()
                
                // Auto scan local MediaStore on startup
                try {
                    repository.scanLocalMediaStoreSongs()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }

                // Read active EQ
                repository.equalizerSettingsFlow
                    .catch { e -> android.util.Log.e("MusicViewModel", "Error in EQ flow", e) }
                    .collect { eq ->
                        if (eq != null) {
                            _equalizerState.value = eq
                            audioEngine.setEqualizerState(eq)
                        }
                    }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // Auto play queue setup once songs are loaded
        viewModelScope.launch {
            try {
                allSongs.collect { songs ->
                    if (songs.isNotEmpty()) {
                        // Keep current queue filtered to only contain existing database songs
                        _playQueue.value = _playQueue.value.filter { q -> songs.any { s -> s.id == q.id } }
                        if (_playQueue.value.isEmpty()) {
                            _playQueue.value = songs
                        }
                        // set default first song (but don't autoplay until tapped)
                        if (_activeSong.value == null) {
                            setSong(songs.first(), autoplay = false)
                        } else if (!songs.any { it.id == _activeSong.value?.id }) {
                            // Active song was deleted, load first available
                            val nextFirst = songs.firstOrNull()
                            if (nextFirst != null) {
                                setSong(nextFirst, autoplay = false)
                            } else {
                                _activeSong.value = null
                            }
                        }
                    } else {
                        _playQueue.value = emptyList()
                        _activeSong.value = null
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("MusicViewModel", "Error collecting auto play queue", e)
            }
        }
    }

    fun navigateTo(screen: ScreenState) {
        _currentScreen.value = screen
    }

    fun selectTab(tab: String) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSortOrder() {
        _sortDescending.value = !_sortDescending.value
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    private fun startMusicService(song: Song) {
        try {
            val intent = android.content.Intent(getApplication(), com.example.audio.MusicService::class.java).apply {
                action = "START"
                putExtra("SONG_TITLE", song.title)
                putExtra("SONG_ARTIST", song.artist)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (e: Throwable) {
            android.util.Log.e("MusicViewModel", "Error starting MusicService", e)
        }
    }

    private fun stopMusicService() {
        try {
            val intent = android.content.Intent(getApplication(), com.example.audio.MusicService::class.java).apply {
                action = "STOP"
            }
            getApplication<Application>().startService(intent)
        } catch (e: Throwable) {
            android.util.Log.e("MusicViewModel", "Error stopping MusicService", e)
        }
    }

    fun setSong(song: Song, autoplay: Boolean = true) {
        _activeSong.value = song
        audioEngine.setSong(song.id, song.audioPreset, song.durationSeconds)
        if (autoplay) {
            audioEngine.play()
            startMusicService(song)
        } else {
            audioEngine.pause()
            stopMusicService()
        }
    }

    fun togglePlayPause() {
        val song = _activeSong.value
        if (isPlaying.value) {
            audioEngine.pause()
            stopMusicService()
        } else {
            audioEngine.play()
            if (song != null) {
                startMusicService(song)
            }
        }
    }

    fun skipNext() {
        val queue = _playQueue.value
        val current = _activeSong.value
        if (queue.isEmpty() || current == null) return
        val index = queue.indexOfFirst { it.id == current.id }
        if (index != -1) {
            val nextIndex = (index + 1) % queue.size
            setSong(queue[nextIndex])
        }
    }

    fun skipPrevious() {
        val queue = _playQueue.value
        val current = _activeSong.value
        if (queue.isEmpty() || current == null) return
        val index = queue.indexOfFirst { it.id == current.id }
        if (index != -1) {
            val prevIndex = if (index - 1 < 0) queue.size - 1 else index - 1
            setSong(queue[prevIndex])
        }
    }

    fun seekTo(seconds: Int) {
        audioEngine.seekTo(seconds)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.updateSongFavorite(song.id, !song.isFavorite)
            // Sync with local active song reference
            if (_activeSong.value?.id == song.id) {
                _activeSong.value = song.copy(isFavorite = !song.isFavorite)
            }
        }
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            val currentActive = _activeSong.value
            val currentQueue = _playQueue.value
            
            // If deleting active song, pause and load next track
            if (currentActive?.id == song.id) {
                audioEngine.pause()
                val index = currentQueue.indexOfFirst { it.id == song.id }
                if (index != -1 && currentQueue.size > 1) {
                    val nextIndex = if (index + 1 < currentQueue.size) index + 1 else 0
                    setSong(currentQueue[nextIndex], autoplay = false)
                } else {
                    _activeSong.value = null
                }
            }

            // Remove cross-references & song from DB
            repository.deleteSong(song)

            // Filter out of current queue state
            _playQueue.value = _playQueue.value.filter { it.id != song.id }
        }
    }

    fun downloadCustomSong(title: String, artist: String, artworkUrl: String? = null) {
        viewModelScope.launch {
            val randomId = "download_" + System.currentTimeMillis()
            val finalArtUrl = artworkUrl ?: when {
                title.contains("Kesariya", ignoreCase = true) -> "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?q=80&w=400"
                title.contains("Pasoori", ignoreCase = true) -> "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?q=80&w=400"
                title.contains("Senorita", ignoreCase = true) -> "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=400"
                title.contains("Shape", ignoreCase = true) -> "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=400"
                title.contains("Believer", ignoreCase = true) -> "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?q=80&w=400"
                else -> {
                    val fallbackArts = listOf(
                        "https://images.unsplash.com/photo-1506157786151-b8491531f063?q=80&w=400",
                        "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=400",
                        "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?q=80&w=400",
                        "https://images.unsplash.com/photo-1487180142328-054b783fc471?q=80&w=400",
                        "https://images.unsplash.com/photo-1513829096960-ef9a314d3ac5?q=80&w=400"
                    )
                    fallbackArts.random()
                }
            }

            val simulatedLyrics = """
                [00:15] Cruising down the offline highway...
                [00:35] High-fidelity notes singing my way.
                [00:55] Feel the soft synthesizer ripple inside,
                [01:15] With high-contrast themes we glide.
                [01:45] Deep acoustic echoes and premium bass,
                [02:05] Music is our personal timeline space.
                [02:30] Bring the beat back again!
                [02:50] Serene end of the downloaded masterpiece.
            """.trimIndent()

            val downloadedSong = Song(
                id = randomId,
                title = title,
                artist = artist,
                album = "Cloud Downloads",
                durationSeconds = 188,
                artworkColorHex = "#2E1B4E", // Sleek deep purple artwork
                audioPreset = listOf("ambient", "jazz", "electronic", "rock").random(),
                isFavorite = false,
                lyrics = simulatedLyrics,
                artworkUri = finalArtUrl
            )

            repository.addDownloadedSong(downloadedSong)
        }
    }

    // Playlist Controls
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
            }
            repository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    // Equalizer Controls
    fun updateEqualizerBand(band: String, value: Float) {
        viewModelScope.launch {
            val oldState = _equalizerState.value
            val newState = when(band) {
                "60Hz" -> oldState.copy(band60Hz = value)
                "230Hz" -> oldState.copy(band230Hz = value)
                "910Hz" -> oldState.copy(band910Hz = value)
                "3600Hz" -> oldState.copy(band3600Hz = value)
                "14000Hz" -> oldState.copy(band14000Hz = value)
                else -> oldState
            }
            _equalizerState.value = newState
            audioEngine.setEqualizerState(newState)
            repository.saveEqualizerSettings(newState)
        }
    }

    fun toggleEqualizer(isEnabled: Boolean) {
        viewModelScope.launch {
            val newState = _equalizerState.value.copy(isEnabled = isEnabled)
            _equalizerState.value = newState
            audioEngine.setEqualizerState(newState)
            repository.saveEqualizerSettings(newState)
        }
    }

    fun toggleBassBoost(isEnabled: Boolean) {
        viewModelScope.launch {
            val newState = _equalizerState.value.copy(bassBoostEnabled = isEnabled)
            _equalizerState.value = newState
            audioEngine.setEqualizerState(newState)
            repository.saveEqualizerSettings(newState)
        }
    }

    fun resetEqualizer() {
        viewModelScope.launch {
            val newState = EqualizerState(
                isEnabled = _equalizerState.value.isEnabled,
                band60Hz = 0f,
                band230Hz = 0f,
                band910Hz = 0f,
                band3600Hz = 0f,
                band14000Hz = 0f,
                bassBoostEnabled = false,
                bassBoostStrength = 0.5f
            )
            _equalizerState.value = newState
            audioEngine.setEqualizerState(newState)
            repository.saveEqualizerSettings(newState)
        }
    }

    fun toggleLyricsExpanded() {
        _isLyricsExpanded.value = !_isLyricsExpanded.value
    }

    // Settings actions
    fun scanMedia() {
        if (_isScanning.value) return
        _isScanning.value = true
        _scannedFilesCount.value = 0
        viewModelScope.launch {
            try {
                val results = repository.scanLocalMediaStoreSongs()
                val targetCount = results.size.coerceAtLeast(8)
                for (i in 1..targetCount) {
                    kotlinx.coroutines.delay(100)
                    _scannedFilesCount.value = i
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Simple animation fallback
                for (i in 1..8) {
                    kotlinx.coroutines.delay(100)
                    _scannedFilesCount.value = i
                }
            }
            _isScanning.value = false
        }
    }

    fun changeTheme(theme: String) {
        _themeMode.value = theme
    }

    fun setSetupGuideCompleted(completed: Boolean) {
        _setupGuideCompleted.value = completed
    }

    fun launchExternalEqualizer() {
        val context = getApplication<Application>()
        try {
            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, 0)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            try {
                val settingsIntent = android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
            } catch (ex: Throwable) {
                android.util.Log.e("MusicViewModel", "Failed to launch external equalizer", ex)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Do not release audioEngine so that background play continues when app/viewModel is cleared.
    }
}
