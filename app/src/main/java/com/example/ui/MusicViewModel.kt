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
    SETTINGS,
    PLAYLIST_DETAILS
}

enum class RepeatMode {
    NONE,
    ALL,
    ONE
}

enum class SortMode {
    A_Z,
    Z_A,
    DATE_MODIFIED_NEWEST,
    DATE_MODIFIED_OLDEST
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPlaylists = repository.allPlaylistsFlow
        .catch { e ->
            android.util.Log.e("MusicViewModel", "Database error collecting playlists", e)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Active state flows
    private val _currentScreen = MutableStateFlow(ScreenState.HOME)
    val currentScreen = _currentScreen.asStateFlow()

    private val _selectedTab = MutableStateFlow("Songs") // Playlists, Play queue, Songs, Albums, Artists
    val selectedTab = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val prefs = application.getSharedPreferences("musicly_prefs", android.content.Context.MODE_PRIVATE)

    private val _sortMode = MutableStateFlow(
        try {
            SortMode.valueOf(prefs.getString("sort_mode", SortMode.A_Z.name) ?: SortMode.A_Z.name)
        } catch (e: Exception) {
            SortMode.A_Z
        }
    )
    val sortMode = _sortMode.asStateFlow()

    // Persistent scroll state properties for tabs
    var songsListScrollIndex = 0
        private set
    var songsListScrollOffset = 0
        private set

    var favoritesListScrollIndex = 0
        private set
    var favoritesListScrollOffset = 0
        private set

    var mostPlayedListScrollIndex = 0
        private set
    var mostPlayedListScrollOffset = 0
        private set

    fun saveSongsScrollState(index: Int, offset: Int) {
        songsListScrollIndex = index
        songsListScrollOffset = offset
    }

    fun saveFavoritesScrollState(index: Int, offset: Int) {
        favoritesListScrollIndex = index
        favoritesListScrollOffset = offset
    }

    fun saveMostPlayedScrollState(index: Int, offset: Int) {
        mostPlayedListScrollIndex = index
        mostPlayedListScrollOffset = offset
    }

    val activeSong = audioEngine.activeSong

    val playQueue = audioEngine.playQueue

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val _sleepTimeRemaining = MutableStateFlow<Int?>(null) // in seconds
    val sleepTimeRemaining = _sleepTimeRemaining.asStateFlow()

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepTimeRemaining.value = null
            return
        }
        _sleepTimeRemaining.value = minutes * 60
        sleepTimerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val currentRemaining = _sleepTimeRemaining.value
                if (currentRemaining != null) {
                    if (currentRemaining <= 1) {
                        _sleepTimeRemaining.value = null
                        audioEngine.pause()
                        break
                    } else {
                        _sleepTimeRemaining.value = currentRemaining - 1
                    }
                } else {
                    break
                }
            }
        }
    }

    // Observed from synthesis player
    val isPlaying = audioEngine.isPlaying
    val currentPositionSeconds = audioEngine.currentPositionSeconds
    val trackDurationSeconds = audioEngine.trackDurationSeconds
    val playbackSpeed = audioEngine.playbackSpeed

    fun setPlaybackSpeed(speed: Float) {
        audioEngine.setPlaybackSpeed(speed)
    }

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

    private var mediaObserverRegistered = false

    private val _scannedFilesCount = MutableStateFlow(0)
    val scannedFilesCount = _scannedFilesCount.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "Dark Forest") ?: "Dark Forest") // Dark Forest, Slate Gray, Midnight Blue
    val themeMode = _themeMode.asStateFlow()

    private val _setupGuideCompleted = MutableStateFlow(true)
    val setupGuideCompleted = _setupGuideCompleted.asStateFlow()

    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                "com.example.musicly.ACTION_PLAY_PAUSE" -> togglePlayPause()
                "com.example.musicly.ACTION_NEXT" -> skipNext()
                "com.example.musicly.ACTION_PREVIOUS" -> skipPrevious()
            }
        }
    }

    init {
        val filter = android.content.IntentFilter().apply {
            addAction("com.example.musicly.ACTION_PLAY_PAUSE")
            addAction("com.example.musicly.ACTION_NEXT")
            addAction("com.example.musicly.ACTION_PREVIOUS")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            getApplication<Application>().registerReceiver(receiver, filter)
        }

        // Wire completion listener to handle next track or looping automatically based on repeat settings
        audioEngine.setOnSongCompletionListener {
            viewModelScope.launch {
                val mode = _repeatMode.value
                val current = audioEngine.activeSong.value
                val queue = audioEngine.playQueue.value
                if (current == null || queue.isEmpty()) return@launch

                when (mode) {
                    RepeatMode.ONE -> {
                        seekTo(0)
                        audioEngine.play()
                    }
                    RepeatMode.ALL -> {
                        if (_shuffleEnabled.value) {
                            val nextSong = queue.random()
                            setSong(nextSong)
                        } else {
                            val index = queue.indexOfFirst { it.id == current.id }
                            if (index != -1) {
                                val nextIndex = (index + 1) % queue.size
                                setSong(queue[nextIndex])
                            }
                        }
                    }
                    RepeatMode.NONE -> {
                        if (_shuffleEnabled.value) {
                            val nextSong = queue.random()
                            setSong(nextSong)
                        } else {
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
        }

        // 1. Observe EQ settings independently and immediately
        viewModelScope.launch {
            try {
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

        // 2. Seed and scan MediaStore sequentially
        viewModelScope.launch {
            try {
                // Seed database first
                repository.initializeDatabase()
                
                // Auto scan local MediaStore on startup
                try {
                    repository.scanLocalMediaStoreSongs()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }

                // Register ContentObserver for automatic updates
                try {
                    repository.registerMediaObserver {
                        viewModelScope.launch {
                            try {
                                repository.scanLocalMediaStoreSongs()
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // 3. Auto play queue setup (one-shot initialization when songs flow becomes populated)
        viewModelScope.launch {
            try {
                allSongs.filter { it.isNotEmpty() }.firstOrNull()?.let { songs ->
                    if (audioEngine.playQueue.value.isEmpty()) {
                        audioEngine.playQueue.value = songs
                    }
                    if (audioEngine.activeSong.value == null && songs.isNotEmpty()) {
                        setSong(songs.first(), autoplay = false)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("MusicViewModel", "Error initializing play queue", e)
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

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        prefs.edit().putString("sort_mode", mode.name).apply()
    }

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
        if (playlist != null) {
            navigateTo(ScreenState.PLAYLIST_DETAILS)
        }
    }

    private fun startMusicService(song: Song) {
        try {
            val intent = android.content.Intent(getApplication(), com.example.audio.MusicService::class.java).apply {
                action = "START"
                putExtra("SONG_ID", song.id)
                putExtra("SONG_ARTWORK", song.artworkUri)
                putExtra("SONG_TITLE", song.title)
                putExtra("SONG_ARTIST", song.artist)
                putExtra("SONG_COLOR", song.artworkColorHex)
                putExtra("IS_PLAYING", isPlaying.value)
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

    private fun updateMusicService(song: Song) {
        try {
            val intent = android.content.Intent(getApplication(), com.example.audio.MusicService::class.java).apply {
                action = "UPDATE"
                putExtra("SONG_ID", song.id)
                putExtra("SONG_ARTWORK", song.artworkUri)
                putExtra("SONG_TITLE", song.title)
                putExtra("SONG_ARTIST", song.artist)
                putExtra("SONG_COLOR", song.artworkColorHex)
                putExtra("IS_PLAYING", isPlaying.value)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                getApplication<Application>().startForegroundService(intent)
            } else {
                getApplication<Application>().startService(intent)
            }
        } catch (e: Throwable) {
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

    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val songs = repository.getSongsForPlaylistSync(playlist.id)
            if (songs.isNotEmpty()) {
                playSongFromList(songs.first(), songs)
            }
        }
    }

    fun playSongFromList(song: Song, list: List<Song>, autoplay: Boolean = true) {
        audioEngine.playQueue.value = list
        setSong(song, autoplay)
    }

    fun setSong(song: Song, autoplay: Boolean = true) {
        audioEngine.activeSong.value = song
        audioEngine.setSong(song.id, song.audioPreset, song.durationSeconds)
        if (autoplay) {
            audioEngine.play()
            startMusicService(song)
            markSongAsPlayed(song)
        } else {
            audioEngine.pause()
        }
    }

    fun markSongAsPlayed(song: Song) {
        viewModelScope.launch {
            try {
                val updatedSong = song.copy(
                    playCount = song.playCount + 1,
                    recentPlayedAt = System.currentTimeMillis()
                )
                repository.updateSong(updatedSong)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        val song = audioEngine.activeSong.value
        if (isPlaying.value) {
            audioEngine.pause()
            if (song != null) {
                updateMusicService(song)
            }
        } else {
            audioEngine.play()
            if (song != null) {
                startMusicService(song)
            }
        }
    }

    fun skipNext() {
        val queue = playQueue.value
        val current = activeSong.value
        if (queue.isNotEmpty() && current != null) {
            val nextSong = if (_shuffleEnabled.value) {
                queue.random()
            } else {
                val index = queue.indexOfFirst { it.id == current.id }
                if (index != -1 && index < queue.size - 1) queue[index + 1] else queue.first()
            }
            setSong(nextSong)
        }
    }

    fun skipPrevious() {
        val queue = playQueue.value
        val current = activeSong.value
        if (queue.isNotEmpty() && current != null) {
            val prevSong = if (_shuffleEnabled.value) {
                queue.random()
            } else {
                val index = queue.indexOfFirst { it.id == current.id }
                if (index > 0) queue[index - 1] else queue.last()
            }
            setSong(prevSong)
        }
    }

    fun seekTo(seconds: Int) {
        audioEngine.seekTo(seconds)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.updateSongFavorite(song.id, !song.isFavorite)
            // Sync with local active song reference
            if (audioEngine.activeSong.value?.id == song.id) {
                audioEngine.activeSong.value = song.copy(isFavorite = !song.isFavorite)
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
            val currentActive = audioEngine.activeSong.value
            val currentQueue = audioEngine.playQueue.value
            
            // If deleting active song, pause and load next track
            if (currentActive?.id == song.id) {
                audioEngine.pause()
                val index = currentQueue.indexOfFirst { it.id == song.id }
                if (index != -1 && currentQueue.size > 1) {
                    val nextIndex = if (index + 1 < currentQueue.size) index + 1 else 0
                    setSong(currentQueue[nextIndex], autoplay = false)
                } else {
                    audioEngine.activeSong.value = null
                }
            }

            // Remove cross-references & song from DB
            repository.deleteSong(song)

            // Filter out of current queue state
            audioEngine.playQueue.value = audioEngine.playQueue.value.filter { it.id != song.id }
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

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, song)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    private val _activeEqPreset = MutableStateFlow(prefs.getString("active_eq_preset", "Custom") ?: "Custom")
    val activeEqPreset = _activeEqPreset.asStateFlow()

    private fun updateActiveEqPreset(preset: String) {
        _activeEqPreset.value = preset
        prefs.edit().putString("active_eq_preset", preset).apply()
    }

    // Equalizer Controls
    fun updateEqualizerBand(band: String, value: Float) {
        viewModelScope.launch {
            updateActiveEqPreset("Custom")
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

    fun applyEqualizerPreset(preset: String) {
        viewModelScope.launch {
            updateActiveEqPreset(preset)
            val state = _equalizerState.value
            val newState = when (preset) {
                "Balanced" -> state.copy(band60Hz = 0f, band230Hz = 0f, band910Hz = 0f, band3600Hz = 0f, band14000Hz = 0f)
                "Hall Room" -> state.copy(band60Hz = 3f, band230Hz = 2f, band910Hz = -1f, band3600Hz = 4f, band14000Hz = 5f)
                "Rock" -> state.copy(band60Hz = 5f, band230Hz = 3f, band910Hz = -2f, band3600Hz = 4f, band14000Hz = 6f)
                "Classical" -> state.copy(band60Hz = 4f, band230Hz = 3f, band910Hz = -2f, band3600Hz = 4f, band14000Hz = 4f)
                "Pop" -> state.copy(band60Hz = -1f, band230Hz = 2f, band910Hz = 5f, band3600Hz = 3f, band14000Hz = -1f)
                "Jazz" -> state.copy(band60Hz = 3f, band230Hz = 2f, band910Hz = -2f, band3600Hz = 3f, band14000Hz = 4f)
                "Bass Boost" -> state.copy(band60Hz = 8f, band230Hz = 5f, band910Hz = 0f, band3600Hz = 0f, band14000Hz = 0f)
                "Vocal Boost" -> state.copy(band60Hz = -2f, band230Hz = 0f, band910Hz = 6f, band3600Hz = 4f, band14000Hz = -1f)
                "Acoustic" -> state.copy(band60Hz = 4f, band230Hz = 1f, band910Hz = 2f, band3600Hz = 3f, band14000Hz = 4f)
                "Electronic" -> state.copy(band60Hz = 6f, band230Hz = 5f, band910Hz = -2f, band3600Hz = 5f, band14000Hz = 6f)
                else -> state
            }
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
                
                // Safely wire the auto-scan ContentObserver now that we are sure permissions exist
                if (!mediaObserverRegistered) {
                    try {
                        repository.registerMediaObserver {
                            viewModelScope.launch {
                                try {
                                    repository.scanLocalMediaStoreSongs()
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        mediaObserverRegistered = true
                        android.util.Log.d("MusicViewModel", "MediaStore content observer successfully registered.")
                    } catch (observerEx: Exception) {
                        observerEx.printStackTrace()
                    }
                }

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
        prefs.edit().putString("theme_mode", theme).apply()
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
