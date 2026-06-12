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

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)
    private val audioEngine = AudioEngine()

    // Database Flows
    val allSongs = repository.allSongsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists = repository.allPlaylistsFlow
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        viewModelScope.launch {
            // Seed database
            repository.initializeDatabase()
            
            // Read active EQ
            repository.equalizerSettingsFlow.collect { eq ->
                if (eq != null) {
                    _equalizerState.value = eq
                    audioEngine.setEqualizerState(eq)
                }
            }
        }

        // Auto play queue setup once songs are loaded
        viewModelScope.launch {
            allSongs.collect { songs ->
                if (songs.isNotEmpty() && _playQueue.value.isEmpty()) {
                    _playQueue.value = songs
                    // set default first song (but don't autoplay until tapped)
                    if (_activeSong.value == null) {
                        setSong(songs.first(), autoplay = false)
                    }
                }
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

    fun setSong(song: Song, autoplay: Boolean = true) {
        _activeSong.value = song
        audioEngine.setSong(song.audioPreset, song.durationSeconds)
        if (autoplay) {
            audioEngine.play()
        } else {
            audioEngine.pause()
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            audioEngine.pause()
        } else {
            audioEngine.play()
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
            for (i in 1..12) {
                kotlinx.coroutines.delay(200)
                _scannedFilesCount.value = i
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

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
