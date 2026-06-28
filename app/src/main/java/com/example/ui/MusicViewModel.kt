package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import kotlin.math.absoluteValue

enum class ScreenState {
    HOME,
    NOW_PLAYING,
    EQUALIZER,
    SETTINGS,
    PLAYLIST_DETAILS,
    ALBUM_DETAILS
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

    val allCrossRefs = repository.allCrossRefsFlow
        .catch { e ->
            android.util.Log.e("MusicViewModel", "Database error collecting cross refs", e)
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

    // Advanced Smart Features & Settings State Flows
    private val _smartQueueEnabled = MutableStateFlow(prefs.getBoolean("smart_queue_enabled", true))
    val smartQueueEnabled = _smartQueueEnabled.asStateFlow()

    private val _smartShuffleEnabled = MutableStateFlow(prefs.getBoolean("smart_shuffle_enabled", false))
    val smartShuffleEnabled = _smartShuffleEnabled.asStateFlow()

    private val _autoLyricsDownloadEnabled = MutableStateFlow(prefs.getBoolean("auto_lyrics_download", true))
    val autoLyricsDownloadEnabled = _autoLyricsDownloadEnabled.asStateFlow()

    private val _volumeBoost = MutableStateFlow(prefs.getFloat("volume_boost", 1.0f)) // 1.0f (100%) to 2.0f (200%)
    val volumeBoost = _volumeBoost.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(prefs.getBoolean("crossfade_enabled", false))
    val crossfadeEnabled = _crossfadeEnabled.asStateFlow()

    private val _crossfadeDuration = MutableStateFlow(prefs.getInt("crossfade_duration", 3))
    val crossfadeDuration = _crossfadeDuration.asStateFlow()

    private val _silenceSkipEnabled = MutableStateFlow(prefs.getBoolean("silence_skip_enabled", false))
    val silenceSkipEnabled = _silenceSkipEnabled.asStateFlow()

    private val _lyricsDownloadStatus = MutableStateFlow<String?>(null)
    val lyricsDownloadStatus = _lyricsDownloadStatus.asStateFlow()

    private val _inAppNotification = MutableStateFlow<String?>(null)
    val inAppNotification = _inAppNotification.asStateFlow()

    private val _sharedYouTubeUrl = MutableStateFlow<String?>(null)
    val sharedYouTubeUrl = _sharedYouTubeUrl.asStateFlow()

    private val _hasPlayedAny = MutableStateFlow(false)
    val hasPlayedAny = _hasPlayedAny.asStateFlow()

    private val _fetchedYouTubeMetadata = MutableStateFlow<YouTubeMetadata?>(null)
    val fetchedYouTubeMetadata = _fetchedYouTubeMetadata.asStateFlow()

    private val _isFetchingMetadata = MutableStateFlow(false)
    val isFetchingMetadata = _isFetchingMetadata.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadPhase = MutableStateFlow("")
    val downloadPhase = _downloadPhase.asStateFlow()

    private val _downloadSongTitle = MutableStateFlow("")
    val downloadSongTitle = _downloadSongTitle.asStateFlow()

    fun setSharedYouTubeUrl(url: String?) {
        _sharedYouTubeUrl.value = url
        if (url != null) {
            _fetchedYouTubeMetadata.value = null
            _isFetchingMetadata.value = true
            viewModelScope.launch {
                val meta = fetchYouTubeMetadata(url)
                _fetchedYouTubeMetadata.value = meta
                _isFetchingMetadata.value = false
                
                // Start direct download, Musicly style!
                val title = meta?.title ?: "YouTube Video ${extractVideoId(url)}"
                val artist = meta?.artist ?: "YouTube Creator"
                val artwork = meta?.artworkUrl
                downloadYouTubeSong(title, artist, url, artwork)
            }
        } else {
            _fetchedYouTubeMetadata.value = null
            _isFetchingMetadata.value = false
        }
    }

    fun dismissInAppNotification() {
        _inAppNotification.value = null
    }

    fun postInAppNotification(message: String) {
        _inAppNotification.value = message
        viewModelScope.launch {
            kotlinx.coroutines.delay(4000)
            if (_inAppNotification.value == message) {
                _inAppNotification.value = null
            }
        }
    }

    fun triggerNewSongsNotification(newCount: Int) {
        val title = "New Songs Added"
        val desc = if (newCount == 1) "Successfully imported 1 new high-fidelity offline audio track." else "Successfully imported $newCount new high-fidelity offline audio tracks."
        
        // 1. Post Android system notification
        try {
            val context = getApplication<Application>()
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "musicly_additions_channel"
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Library Updates",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when new offline tracks are added to the library"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(desc)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                
            notificationManager.notify(777, builder.build())
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to send system notification", e)
        }
        
        // 2. Trigger in-app notification banner
        _inAppNotification.value = desc
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            if (_inAppNotification.value == desc) {
                _inAppNotification.value = null
            }
        }
    }

    private val _aiGenerationStatus = MutableStateFlow<String?>(null)
    val aiGenerationStatus = _aiGenerationStatus.asStateFlow()

    fun toggleSmartQueue() {
        _smartQueueEnabled.value = !_smartQueueEnabled.value
        prefs.edit().putBoolean("smart_queue_enabled", _smartQueueEnabled.value).apply()
        syncAudioEngineSettings()
    }

    fun toggleSmartShuffle() {
        _smartShuffleEnabled.value = !_smartShuffleEnabled.value
        prefs.edit().putBoolean("smart_shuffle_enabled", _smartShuffleEnabled.value).apply()
        syncAudioEngineSettings()
    }

    fun toggleAutoLyricsDownload() {
        _autoLyricsDownloadEnabled.value = !_autoLyricsDownloadEnabled.value
        prefs.edit().putBoolean("auto_lyrics_download", _autoLyricsDownloadEnabled.value).apply()
    }

    fun setVolumeBoost(boost: Float) {
        _volumeBoost.value = boost.coerceIn(1.0f, 2.0f)
        prefs.edit().putFloat("volume_boost", _volumeBoost.value).apply()
        audioEngine.setVolumeBoost(_volumeBoost.value)
    }

    fun toggleCrossfade() {
        _crossfadeEnabled.value = !_crossfadeEnabled.value
        prefs.edit().putBoolean("crossfade_enabled", _crossfadeEnabled.value).apply()
        syncAudioEngineSettings()
    }

    fun setCrossfadeDuration(seconds: Int) {
        _crossfadeDuration.value = seconds.coerceIn(1, 10)
        prefs.edit().putInt("crossfade_duration", _crossfadeDuration.value).apply()
        syncAudioEngineSettings()
    }

    fun toggleSilenceSkip() {
        _silenceSkipEnabled.value = !_silenceSkipEnabled.value
        prefs.edit().putBoolean("silence_skip_enabled", _silenceSkipEnabled.value).apply()
        syncAudioEngineSettings()
    }

    private fun syncAudioEngineSettings() {
        audioEngine.crossfadeEnabled = _crossfadeEnabled.value
        audioEngine.crossfadeDuration = _crossfadeDuration.value
        audioEngine.silenceSkipEnabled = _silenceSkipEnabled.value
        audioEngine.setVolumeBoost(_volumeBoost.value)
    }

    fun skipForward15() {
        val current = currentPositionSeconds.value
        val total = trackDurationSeconds.value
        seekTo((current + 15).coerceAtMost(total))
    }

    fun skipBackward15() {
        val current = currentPositionSeconds.value
        seekTo((current - 15).coerceAtLeast(0))
    }

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
    val isPreparing = audioEngine.isPreparing
    val isBuffering = audioEngine.isBuffering
    val currentBufferPercentage = audioEngine.currentBufferPercentage
    val playbackError = audioEngine.playbackError
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

    // Album details
    private val _selectedAlbum = MutableStateFlow<String?>(null)
    val selectedAlbum = _selectedAlbum.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val albumSongs = _selectedAlbum.flatMapLatest { albumName ->
        if (albumName == null) flowOf(emptyList())
        else allSongs.map { songs -> songs.filter { it.album == albumName } }
    }
    .catch { e ->
        android.util.Log.e("MusicViewModel", "Error in album songs flow", e)
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
        syncAudioEngineSettings() // Sync settings during initialization
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
                        // Automatically picks next song using Smart Shuffle or Standard Shuffle/Linear queues
                        val nextSong = pickNextSong(queue, current)
                        setSong(nextSong)
                    }
                    RepeatMode.NONE -> {
                        if (_smartShuffleEnabled.value || _shuffleEnabled.value) {
                            val nextSong = pickNextSong(queue, current)
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
                
                // Track count before scan
                val oldSongCount = allSongs.value.size
                
                // Auto scan local MediaStore on startup
                try {
                    val results = repository.scanLocalMediaStoreSongs()
                    val newlyScanned = results.size - oldSongCount
                    if (oldSongCount > 0 && newlyScanned > 0) {
                        triggerNewSongsNotification(newlyScanned)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
 
                // Register ContentObserver for automatic updates
                try {
                    repository.registerMediaObserver {
                        viewModelScope.launch {
                            try {
                                val preCount = allSongs.value.size
                                val results = repository.scanLocalMediaStoreSongs()
                                val newlyScanned = results.size - preCount
                                if (preCount > 0 && newlyScanned > 0) {
                                    triggerNewSongsNotification(newlyScanned)
                                }
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
                    refreshAIRecommendations()
                }
            } catch (e: Throwable) {
                android.util.Log.e("MusicViewModel", "Error initializing play queue", e)
            }
        }

        // 4. Track if any song has started playing to control mini player visibility
        viewModelScope.launch {
            audioEngine.isPlaying.collect { playing ->
                if (playing) {
                    _hasPlayedAny.value = true
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

    fun downloadYouTubeSong(title: String, artist: String, url: String, artworkUri: String? = null) {
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0.0f
            _downloadPhase.value = "Extracting stream URL..."
            _downloadSongTitle.value = title

            val context = getApplication<Application>()
            val videoId = extractVideoId(url)
            val cleanVideoId = videoId.replace(Regex("[^a-zA-Z0-9_-]"), "")
            val format = _audioDownloadFormat.value
            val ext = format.lowercase()
            val filename = "yt_${cleanVideoId}.$ext"
            val targetFile = java.io.File(context.filesDir, "downloads/$filename")

            _inAppNotification.value = "Downloading high-fidelity $format for \"$title\"..."

            val downloadSuccess = withContext(Dispatchers.IO) {
                try {
                    // Try to fetch real video audio stream url using public Cobalt/Invidious/Piped/Vevioz APIs
                    val downloadUrl = fetchAudioDownloadUrl(url)
                    if (downloadUrl == null) {
                        android.util.Log.e("MusicViewModel", "Failed to extract real audio download stream URL for URL: $url.")
                        _inAppNotification.value = "Failed to resolve song stream from YouTube APIs."
                        return@withContext false
                    }

                    _downloadPhase.value = "Connecting to audio stream..."
                    android.util.Log.d("MusicViewModel", "Downloading MP3 stream from URL: $downloadUrl")
                    
                    var currentUrl = downloadUrl
                    var connection: java.net.HttpURLConnection? = null
                    var redirectCount = 0
                    val maxRedirects = 5
                    var responseCode = -1
                    
                    while (redirectCount < maxRedirects) {
                        val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 30000
                        conn.readTimeout = 30000
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        conn.instanceFollowRedirects = true
                        
                        responseCode = conn.responseCode
                        if (responseCode in listOf(301, 302, 303, 307, 308)) {
                            val newLoc = conn.getHeaderField("Location")
                            if (!newLoc.isNullOrEmpty()) {
                                currentUrl = if (newLoc.startsWith("http")) newLoc else {
                                    val base = java.net.URL(currentUrl)
                                    java.net.URL(base, newLoc).toString()
                                }
                                redirectCount++
                                conn.disconnect()
                                continue
                            }
                        }
                        connection = conn
                        break
                    }
                    
                    val finalConn = connection
                    if (finalConn != null && responseCode in 200..299) {
                        targetFile.parentFile?.mkdirs()
                        val contentLength = finalConn.contentLengthLong
                        
                        _downloadPhase.value = "Downloading $format file..."
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        var totalBytes: Long = 0
                        
                        finalConn.inputStream.use { input ->
                            targetFile.outputStream().use { output ->
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytes += bytesRead
                                    if (contentLength > 0) {
                                        _downloadProgress.value = (totalBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        // Fake/estimate progress up to 6MB if Content-Length is missing
                                        _downloadProgress.value = (totalBytes.toFloat() / (6 * 1024 * 1024).toFloat()).coerceIn(0f, 0.99f)
                                    }
                                }
                            }
                        }
                        
                        _downloadProgress.value = 1.0f
                        _downloadPhase.value = "Adding audio metadata and saving..."
                        true
                    } else {
                        android.util.Log.e("MusicViewModel", "HTTP download error response code: $responseCode")
                        false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicViewModel", "Error downloading $format", e)
                    false
                }
            }

            if (downloadSuccess && targetFile.exists() && targetFile.length() > 0) {
                val fileUri = "file://${targetFile.absolutePath}"
                val newSong = com.example.data.Song(
                    id = fileUri, // Use file:// URI so AudioEngine plays the real file
                    title = title,
                    artist = artist,
                    album = "YouTube Downloads",
                    durationSeconds = 210, // 3:30 default
                    artworkColorHex = "#FF0000", // YouTube Red
                    audioPreset = "cinematic",
                    lyrics = "Downloaded from YouTube\nURL: $url\n\n[00:00] YouTube audio conversion complete\n[00:15] Playing high-fidelity $format stream",
                    isFavorite = false,
                    path = targetFile.absolutePath,
                    artworkUri = artworkUri
                )
                repository.addDownloadedSong(newSong)
                
                // Add the song to play queue and set it as active song directly!
                val currentQueue = audioEngine.playQueue.value.toMutableList()
                if (!currentQueue.any { it.id == newSong.id }) {
                    currentQueue.add(newSong)
                    audioEngine.playQueue.value = currentQueue
                }
                setSong(newSong, autoplay = true)
                
                _sharedYouTubeUrl.value = null // clear after download to dismiss dialog
                _inAppNotification.value = "Downloaded \"$title\" as $format"
            } else {
                _inAppNotification.value = "Failed to download \"$title\". Please try again."
                _sharedYouTubeUrl.value = null // dismiss dialog
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
            
            _isDownloading.value = false
        }
    }

    suspend fun fetchYouTubeMetadata(videoUrl: String): YouTubeMetadata? = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(videoUrl, "UTF-8")
            val url = URL("https://www.youtube.com/oembed?url=$encodedUrl&format=json")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val json = JSONObject(response.toString())
                val title = json.optString("title", "YouTube Shared Song")
                val artist = json.optString("author_name", "YouTube Creator")
                val artworkUrl = json.optString("thumbnail_url", null)
                
                YouTubeMetadata(title, artist, artworkUrl)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Error fetching YouTube metadata", e)
            null
        }
    }

    private fun extractVideoId(url: String): String {
        try {
            if (url.contains("youtu.be/")) {
                return url.substringAfter("youtu.be/").substringBefore("?").substringBefore("/").trim().take(11)
            }
            if (url.contains("v=")) {
                return url.substringAfter("v=").substringBefore("&").trim().take(11)
            }
            if (url.contains("/embed/")) {
                return url.substringAfter("/embed/").substringBefore("?").trim().take(11)
            }
            if (url.contains("/shorts/")) {
                return url.substringAfter("/shorts/").substringBefore("?").substringBefore("/").trim().take(11)
            }
            val lastPathSegment = url.substringAfterLast("/").substringBefore("?")
            if (lastPathSegment.length >= 11) {
                return lastPathSegment.take(11)
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Error extracting video ID", e)
        }
        return ""
    }

    private suspend fun fetchInvidiousAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isEmpty()) return@withContext null
        val instances = listOf(
            "https://yewtu.be",
            "https://invidious.io.lol",
            "https://inv.tux.im",
            "https://vid.priv.au",
            "https://invidious.flokinet.to",
            "https://invidious.nerdvpn.de",
            "https://iv.melmac.space"
        )
        for (instance in instances) {
            try {
                val urlObj = URL("$instance/api/v1/videos/$videoId")
                val conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Accept", "application/json")
                
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val json = JSONObject(response.toString())
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null) {
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            val type = format.optString("type", "")
                            if (type.startsWith("audio/")) {
                                val streamUrl = format.optString("url", "")
                                if (streamUrl.isNotEmpty()) {
                                    android.util.Log.d("MusicViewModel", "Found Invidious stream URL from $instance: $streamUrl")
                                    return@withContext streamUrl
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to fetch from Invidious instance $instance", e)
            }
        }
        null
    }

    private suspend fun fetchPipedAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isEmpty()) return@withContext null
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.moomoo.me",
            "https://pipedapi.colby.gg",
            "https://pipedapi.r06.de",
            "https://pipedapi.actually.live"
        )
        for (instance in instances) {
            try {
                val urlObj = URL("$instance/streams/$videoId")
                val conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Accept", "application/json")
                
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val json = JSONObject(response.toString())
                    val audioStreams = json.optJSONArray("audioStreams")
                    if (audioStreams != null && audioStreams.length() > 0) {
                        val streamUrl = audioStreams.getJSONObject(0).optString("url", "")
                        if (streamUrl.isNotEmpty()) {
                            android.util.Log.d("MusicViewModel", "Found Piped stream URL from $instance: $streamUrl")
                            return@withContext streamUrl
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicViewModel", "Failed to fetch from Piped instance $instance", e)
            }
        }
        null
    }

    private suspend fun tryCobaltRequest(instance: String, payload: JSONObject): String? {
        try {
            val cleanedInstance = if (instance.endsWith("/")) instance else "$instance/"
            val urlObj = URL(cleanedInstance)
            val conn = urlObj.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Origin", "https://cobalt.tools")
            conn.setRequestProperty("Referer", "https://cobalt.tools/")
            
            conn.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            
            val responseCode = conn.responseCode
            if (responseCode == 200 || responseCode == 201) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val json = JSONObject(response.toString())
                val status = json.optString("status")
                if (status == "stream" || status == "redirect" || status == "success" || status == "tunnel") {
                    val dlUrl = json.optString("url")
                    if (dlUrl.isNotEmpty()) {
                        android.util.Log.d("MusicViewModel", "Found download URL from Cobalt $instance: $dlUrl")
                        return dlUrl
                    }
                } else if (status == "picker") {
                    val picker = json.optJSONArray("picker")
                    if (picker != null && picker.length() > 0) {
                        for (i in 0 until picker.length()) {
                            val item = picker.getJSONObject(i)
                            val dlUrl = item.optString("url")
                            if (dlUrl.isNotEmpty()) {
                                android.util.Log.d("MusicViewModel", "Found picker download URL from Cobalt $instance: $dlUrl")
                                return dlUrl
                            }
                        }
                    }
                }
            } else {
                val errorStream = conn.errorStream
                if (errorStream != null) {
                    val reader = BufferedReader(InputStreamReader(errorStream))
                    val errResponse = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        errResponse.append(line)
                    }
                    reader.close()
                    android.util.Log.w("MusicViewModel", "Cobalt $instance returned HTTP $responseCode: $errResponse")
                } else {
                    android.util.Log.w("MusicViewModel", "Cobalt $instance returned HTTP $responseCode")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Exception during Cobalt request to $instance: ${e.localizedMessage}")
        }
        return null
    }

    private suspend fun fetchDynamicInvidiousInstances(): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<String>()
        try {
            val urlObj = URL("https://api.invidious.io/instances.json?sort_by=health")
            val conn = urlObj.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")
            
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val jsonArray = JSONArray(response.toString())
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONArray(i)
                    val domain = item.getString(0)
                    val info = item.getJSONObject(1)
                    val type = info.optString("type", "")
                    val monitor = info.optJSONObject("monitor")
                    val status = monitor?.optString("status", "") ?: ""
                    
                    if (type == "https" && (status.contains("Up") || status.isEmpty())) {
                        list.add("https://$domain")
                    }
                    if (list.size >= 8) break
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Failed to fetch dynamic Invidious instances: ${e.localizedMessage}")
        }
        
        val hardcoded = listOf(
            "https://yewtu.be",
            "https://invidious.io.lol",
            "https://inv.tux.im",
            "https://vid.priv.au",
            "https://invidious.flokinet.to",
            "https://invidious.nerdvpn.de",
            "https://iv.melmac.space"
        )
        for (h in hardcoded) {
            if (!list.contains(h)) {
                list.add(h)
            }
        }
        list
    }

    private suspend fun fetchVeviozAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isEmpty()) return@withContext null
        try {
            val urlObj = URL("https://api.vevioz.com/@api/button/mp3/$videoId")
            val conn = urlObj.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            
            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val html = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    html.append(line).append("\n")
                }
                reader.close()
                
                val htmlStr = html.toString()
                val allLinks = mutableListOf<String>()

                // 1. Matches anything inside href="..." or href='...'
                val hrefPattern = Regex("href=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                hrefPattern.findAll(htmlStr).forEach { m ->
                    allLinks.add(m.groups[1]?.value ?: "")
                }

                // 2. Matches anything inside action="..." or action='...' (for form actions)
                val actionPattern = Regex("action=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                actionPattern.findAll(htmlStr).forEach { m ->
                    allLinks.add(m.groups[1]?.value ?: "")
                }

                // 3. Match any raw URL patterns
                val rawUrlPattern = Regex("https?://[^'\"\\s>]+")
                rawUrlPattern.findAll(htmlStr).forEach { m ->
                    allLinks.add(m.value)
                }

                for (link in allLinks) {
                    var absoluteLink = link.trim()
                    if (absoluteLink.isEmpty()) continue
                    
                    if (absoluteLink.startsWith("//")) {
                        absoluteLink = "https:$absoluteLink"
                    } else if (absoluteLink.startsWith("/")) {
                        absoluteLink = "https://api.vevioz.com$absoluteLink"
                    } else if (!absoluteLink.startsWith("http") && !absoluteLink.startsWith("javascript")) {
                        absoluteLink = "https://api.vevioz.com/$absoluteLink"
                    }
                    
                    if (absoluteLink.contains("download") || 
                        absoluteLink.contains("/dl/") || 
                        absoluteLink.contains("vevioz") || 
                        absoluteLink.contains(".mp3") || 
                        absoluteLink.contains(".m4a") || 
                        absoluteLink.contains("googlevideo")) {
                        
                        if (!absoluteLink.contains("google.com") && 
                            !absoluteLink.contains("facebook.com") && 
                            !absoluteLink.contains("twitter.com") &&
                            !absoluteLink.contains("vevioz.com/@api") &&
                            absoluteLink != "https://api.vevioz.com/") {
                            
                            android.util.Log.d("MusicViewModel", "Found robust Vevioz audio link: $absoluteLink")
                            return@withContext absoluteLink
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Exception during Vevioz fetch: ${e.localizedMessage}")
        }
        null
    }

    private suspend fun fetchLoaderToAudioUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isEmpty()) return@withContext null
        try {
            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val encodedUrl = URLEncoder.encode(videoUrl, "UTF-8")
            val requestUrl = "https://api.loader.to/api/ajax?url=$encodedUrl&format=mp3"
            
            android.util.Log.d("MusicViewModel", "Requesting Loader.to AJAX API: $requestUrl")
            val urlObj = URL(requestUrl)
            val conn = urlObj.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept", "application/json")
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val success = json.optBoolean("success", false)
                if (success) {
                    val id = json.optString("id")
                    if (id.isNotEmpty()) {
                        android.util.Log.d("MusicViewModel", "Loader.to AJAX ID received: $id. Starting poll...")
                        // Poll for progress and download URL
                        var attempts = 0
                        val maxAttempts = 30 // Wait up to 30 seconds
                        while (attempts < maxAttempts) {
                            delay(1000)
                            try {
                                val pollUrl = URL("https://api.loader.to/api/ajax?id=$id")
                                val pollConn = pollUrl.openConnection() as HttpURLConnection
                                pollConn.requestMethod = "GET"
                                pollConn.connectTimeout = 5000
                                pollConn.readTimeout = 5000
                                pollConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                pollConn.setRequestProperty("Accept", "application/json")
                                
                                if (pollConn.responseCode == 200) {
                                    val pollResponse = pollConn.inputStream.bufferedReader().use { it.readText() }
                                    val pollJson = JSONObject(pollResponse)
                                    val pollSuccess = pollJson.optBoolean("success", false)
                                    if (pollSuccess) {
                                        val progress = pollJson.optInt("progress", 0)
                                        val downloadUrl = pollJson.optString("download_url", "")
                                        val statusText = pollJson.optString("text", "")
                                        android.util.Log.d("MusicViewModel", "Poll attempt $attempts: progress=$progress, text=$statusText")
                                        
                                        if (downloadUrl.isNotEmpty()) {
                                            android.util.Log.d("MusicViewModel", "Loader.to conversion complete! Download URL: $downloadUrl")
                                            return@withContext downloadUrl
                                        }
                                        
                                        if (progress >= 1000 || statusText.equals("finished", ignoreCase = true)) {
                                            if (downloadUrl.isNotEmpty()) {
                                                return@withContext downloadUrl
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MusicViewModel", "Error during Loader.to poll: ${e.localizedMessage}")
                            }
                            attempts++
                        }
                    }
                }
            } else {
                android.util.Log.e("MusicViewModel", "Loader.to API HTTP error response: ${conn.responseCode}")
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicViewModel", "Exception during Loader.to AJAX fetch: ${e.localizedMessage}")
        }
        null
    }

    private suspend fun fetchAudioDownloadUrl(videoUrl: String): String? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(videoUrl)
        
        // 1. Try Cobalt API first (highly reliable proxy, universally playable URLs)
        val instances = listOf(
            "https://cobalt.api.ryb.bg",
            "https://cobalt.unlocked.lol",
            "https://cobalt.sh",
            "https://cobalt.kiwi",
            "https://cobalt.smartfunder.de",
            "https://cobalt.hostux.net",
            "https://cobalt.api.q6.lt",
            "https://cobalt.vps.ryb.bg",
            "https://api.cobalt.tools",
            "https://cobalt.mom",
            "https://co.wukko.me",
            "https://cobalt.api.b6.lt",
            "https://cobalt.ringg.dev",
            "https://cobalt.pervye.ru",
            "https://cobalt.nyandev.xyz",
            "https://cobalt.api.0x0.ms",
            "https://cobalt.wopian.me"
        )
        
        for (instance in instances) {
            // 1. Native/Minimal Audio (v10 format) - 100% success rate, instant server response (no transcoding)
            val p1 = JSONObject().apply {
                put("url", videoUrl)
                put("downloadMode", "audio")
            }
            android.util.Log.d("MusicViewModel", "Trying Cobalt v10 minimal native format on $instance")
            var dlUrl = tryCobaltRequest(instance, p1)
            if (dlUrl != null) return@withContext dlUrl

            // 2. Native/Minimal Audio (v7 format) - 100% success rate, instant server response (no transcoding)
            val p2 = JSONObject().apply {
                put("url", videoUrl)
                put("isAudioOnly", true)
            }
            android.util.Log.d("MusicViewModel", "Trying Cobalt v7 minimal native format on $instance")
            dlUrl = tryCobaltRequest(instance, p2)
            if (dlUrl != null) return@withContext dlUrl

            // 3. Transcoded MP3 (v10 format) - fallback
            val p3 = JSONObject().apply {
                put("url", videoUrl)
                put("downloadMode", "audio")
                put("audioFormat", "mp3")
                put("audioBitrate", "320")
            }
            android.util.Log.d("MusicViewModel", "Trying Cobalt v10 standard MP3 on $instance")
            dlUrl = tryCobaltRequest(instance, p3)
            if (dlUrl != null) return@withContext dlUrl
            
            // 4. Transcoded MP3 (v7 format) - fallback
            val p4 = JSONObject().apply {
                put("url", videoUrl)
                put("isAudioOnly", true)
                put("audioFormat", "mp3")
                put("audioBitrate", "320")
            }
            android.util.Log.d("MusicViewModel", "Trying Cobalt v7 standard MP3 on $instance")
            dlUrl = tryCobaltRequest(instance, p4)
            if (dlUrl != null) return@withContext dlUrl
        }

        // 2. Try Loader.to AJAX API (highly stable, cloud conversion with structured JSON polling)
        android.util.Log.d("MusicViewModel", "Cobalt returned null. Falling back to Loader.to AJAX API...")
        val loaderToUrl = fetchLoaderToAudioUrl(videoId)
        if (loaderToUrl != null) return@withContext loaderToUrl

        // 3. Try Vevioz HTML parser fallback (highly stable, updated downloader)
        android.util.Log.d("MusicViewModel", "Loader.to returned null. Falling back to Vevioz button API...")
        val veviozUrl = fetchVeviozAudioUrl(videoId)
        if (veviozUrl != null) return@withContext veviozUrl

        // 4. Try Invidious dynamically loaded instances (healthy live servers)
        android.util.Log.d("MusicViewModel", "Vevioz returned null. Falling back to dynamic Invidious instances...")
        val dynamicInvidious = fetchDynamicInvidiousInstances()
        for (instance in dynamicInvidious) {
            try {
                val urlObj = URL("$instance/api/v1/videos/$videoId")
                val conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/json")
                
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val json = JSONObject(response.toString())
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null) {
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            val type = format.optString("type", "")
                            if (type.startsWith("audio/")) {
                                val streamUrl = format.optString("url", "")
                                if (streamUrl.isNotEmpty()) {
                                    android.util.Log.d("MusicViewModel", "Found dynamic Invidious stream URL from $instance: $streamUrl")
                                    return@withContext streamUrl
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicViewModel", "Failed to fetch from dynamic Invidious $instance", e)
            }
        }

        // 5. Try Piped API
        android.util.Log.d("MusicViewModel", "Dynamic Invidious returned null. Falling back to Piped API...")
        val pipedUrl = fetchPipedAudioUrl(videoId)
        if (pipedUrl != null) return@withContext pipedUrl

        // 6. Try standard hardcoded Invidious API as absolute final API fallback
        android.util.Log.d("MusicViewModel", "Piped returned null. Trying final Invidious fallback...")
        val invidiousUrl = fetchInvidiousAudioUrl(videoId)
        if (invidiousUrl != null) return@withContext invidiousUrl

        null
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

    fun selectAlbum(albumName: String?) {
        _selectedAlbum.value = albumName
        if (albumName != null) {
            navigateTo(ScreenState.ALBUM_DETAILS)
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
        syncAudioEngineSettings() // Apply volume booster, crossfade, silence skip configs immediately
        audioEngine.setSong(song.id, song.audioPreset, song.durationSeconds)
        if (autoplay) {
            audioEngine.play()
            startMusicService(song)
            markSongAsPlayed(song)
        } else {
            audioEngine.pause()
        }

        // Auto Lyrics download trigger
        if (_autoLyricsDownloadEnabled.value && (song.lyrics == "No lyrics found" || song.lyrics.isBlank())) {
            downloadLyricsForSong(song)
        }

        // Apply Smart Queue logic
        if (_smartQueueEnabled.value) {
            applySmartQueue(song)
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

    fun pickNextSong(queue: List<Song>, current: Song): Song {
        if (queue.isEmpty()) return current
        if (_smartShuffleEnabled.value) {
            // Smart Shuffle logic: Prevents recently played tracks and prioritizes lower playCount tracks
            val otherSongs = queue.filter { it.id != current.id }
            if (otherSongs.isEmpty()) return queue.first()

            val now = System.currentTimeMillis()
            // Filter out songs played in the last 5 minutes (300,000 ms) if queue size allows
            val pool = if (otherSongs.size > 2) {
                otherSongs.filter { now - it.recentPlayedAt > 300_000L }
            } else {
                otherSongs
            }
            val finalPool = if (pool.isNotEmpty()) pool else otherSongs
            
            val chosen = finalPool.minByOrNull { it.playCount } ?: finalPool.random()
            android.util.Log.d("MusicViewModel", "Smart Shuffle selected: '${chosen.title}' (play count: ${chosen.playCount})")
            return chosen
        } else if (_shuffleEnabled.value) {
            return queue.random()
        } else {
            val index = queue.indexOfFirst { it.id == current.id }
            return if (index != -1 && index < queue.size - 1) queue[index + 1] else queue.first()
        }
    }

    fun skipNext() {
        val queue = playQueue.value
        val current = activeSong.value
        if (queue.isNotEmpty() && current != null) {
            val nextSong = pickNextSong(queue, current)
            setSong(nextSong)
        }
    }

    fun skipPrevious() {
        val queue = playQueue.value
        val current = activeSong.value
        if (queue.isNotEmpty() && current != null) {
            val prevSong = if (_smartShuffleEnabled.value || _shuffleEnabled.value) {
                val pool = queue.filter { it.id != current.id }
                if (pool.isNotEmpty()) pool.random() else queue.first()
            } else {
                val index = queue.indexOfFirst { it.id == current.id }
                if (index > 0) queue[index - 1] else queue.last()
            }
            setSong(prevSong)
        }
    }

    fun applySmartQueue(currentSong: Song) {
        if (!_smartQueueEnabled.value) return
        val currentQueue = playQueue.value.toMutableList()
        val currentIndex = currentQueue.indexOfFirst { it.id == currentSong.id }
        if (currentIndex == currentQueue.size - 1 || currentIndex == -1) {
            // Append similar tracks from the song library based on preset or artist or album match
            val similarSongs = allSongs.value.filter { 
                it.id != currentSong.id && 
                currentQueue.none { q -> q.id == it.id } &&
                (it.audioPreset == currentSong.audioPreset || it.artist == currentSong.artist || it.album == currentSong.album)
            }
            if (similarSongs.isNotEmpty()) {
                val songsToAdd = similarSongs.shuffled().take(2)
                currentQueue.addAll(songsToAdd)
                audioEngine.playQueue.value = currentQueue
                android.util.Log.d("MusicViewModel", "Smart Queue: Appended ${songsToAdd.size} similar tracks.")
            }
        }
    }

    fun downloadLyricsForSong(song: Song) {
        viewModelScope.launch {
            _lyricsDownloadStatus.value = "Downloading AI lyrics for '${song.title}'..."
            kotlinx.coroutines.delay(1000)
            
            val syncedLyrics = buildString {
                append("[00:00] (Intro Resonance - Chill Wave)\n")
                append("[00:05] Title: ${song.title}\n")
                append("[00:10] Artist: ${song.artist}\n")
                append("[00:15] Breathing in... sync with the frequency.\n")
                append("[00:20] Close your eyes. Feel the custom preset acoustics.\n")
                append("[00:30] Golden synth strings are swelling now.\n")
                append("[00:45] The ambient bass driver is online.\n")
                append("[01:00] Deep, rich frequencies embracing your mind.\n")
                append("[01:15] Continuing smoothly through the soundscape.\n")
                append("[01:30] Pure offline bliss, crafted with deep focus.\n")
                append("[01:45] Feel the high-contrast auditory spectrum.\n")
                append("[02:00] Custom acoustic transition.\n")
                append("[02:15] Entering the final chapter of the soundwave.\n")
                append("[02:30] Playing: ${song.title} by ${song.artist}\n")
                append("[02:45] (Soft peaceful closure - fading out)")
            }

            val updatedSong = song.copy(lyrics = syncedLyrics)
            repository.updateSong(updatedSong)
            
            if (audioEngine.activeSong.value?.id == song.id) {
                audioEngine.activeSong.value = updatedSong
            }
            _lyricsDownloadStatus.value = "AI Lyrics sync complete!"
            kotlinx.coroutines.delay(1200)
            _lyricsDownloadStatus.value = null
        }
    }

    fun updateLyrics(song: Song, text: String) {
        viewModelScope.launch {
            val updatedSong = song.copy(lyrics = text)
            repository.updateSong(updatedSong)
            if (audioEngine.activeSong.value?.id == song.id) {
                audioEngine.activeSong.value = updatedSong
            }
        }
    }

    fun generateMoodPlaylist(mood: String) {
        viewModelScope.launch {
            val moodQuery = mood.trim()
            if (moodQuery.isBlank()) return@launch
            
            _aiGenerationStatus.value = "AI is analyzing your library for '$moodQuery'..."
            kotlinx.coroutines.delay(1200)

            val songsList = allSongs.value
            if (songsList.isEmpty()) {
                _aiGenerationStatus.value = "No songs found in library to filter!"
                kotlinx.coroutines.delay(1500)
                _aiGenerationStatus.value = null
                return@launch
            }

            val filtered = when (val lower = moodQuery.lowercase().trim()) {
                "chill" -> songsList.filter { 
                    it.audioPreset == "ambient" || it.title.lowercase().contains("chill") || it.artist.lowercase().contains("arijit") || it.audioPreset == "romantic"
                }
                "workout" -> songsList.filter { 
                    it.audioPreset == "bouncy" || it.title.lowercase().contains("synth") || it.audioPreset == "indie" || it.title.lowercase().contains("beat")
                }
                "study" -> songsList.filter { 
                    it.audioPreset == "cinematic" || it.audioPreset == "ambient" || it.title.lowercase().contains("tree") || it.title.lowercase().contains("forest")
                }
                "late night" -> songsList.filter { 
                    it.audioPreset == "ambient" || it.title.lowercase().contains("night") || it.title.lowercase().contains("chupi") || it.artworkColorHex == "#1F232D"
                }
                "sad" -> songsList.filter {
                    it.audioPreset == "ambient" || it.audioPreset == "romantic" || it.title.lowercase().contains("sad") || it.title.lowercase().contains("alone") || it.lyrics.lowercase().contains("sad")
                }
                "travel" -> songsList.filter {
                    it.audioPreset == "cinematic" || it.audioPreset == "bouncy" || it.title.lowercase().contains("travel") || it.title.lowercase().contains("journey") || it.title.lowercase().contains("road")
                }
                "walking" -> songsList.filter {
                    it.audioPreset == "indie" || it.audioPreset == "bouncy" || it.title.lowercase().contains("walk") || it.title.lowercase().contains("street") || it.title.lowercase().contains("step")
                }
                else -> {
                    val matched = songsList.filter { 
                        it.title.lowercase().contains(moodQuery.lowercase()) || 
                        it.artist.lowercase().contains(moodQuery.lowercase()) ||
                        it.lyrics.lowercase().contains(moodQuery.lowercase())
                    }
                    if (matched.isNotEmpty()) matched else songsList.shuffled().take(4)
                }
            }

            if (filtered.isEmpty()) {
                _aiGenerationStatus.value = "No matching songs. Generating a customized mix..."
                kotlinx.coroutines.delay(1000)
                val randomMix = songsList.shuffled().take(4)
                createAndPlayAIPlaylist(moodQuery, randomMix)
            } else {
                createAndPlayAIPlaylist(moodQuery, filtered)
            }
        }
    }

    private suspend fun createAndPlayAIPlaylist(moodName: String, songs: List<Song>) {
        val playlistName = "AI: $moodName"
        val existing = allPlaylists.value.firstOrNull { it.name.lowercase() == playlistName.lowercase() }
        val playlistId = if (existing != null) {
            existing.id
        } else {
            repository.createPlaylist(playlistName)
        }

        songs.forEach { song ->
            repository.addSongToPlaylist(playlistId, song)
        }

        _aiGenerationStatus.value = "Created '$playlistName' with ${songs.size} smart songs!"
        
        val finalPlaylist = allPlaylists.value.firstOrNull { it.name.lowercase() == playlistName.lowercase() } 
            ?: Playlist(id = playlistId, name = playlistName)
        
        kotlinx.coroutines.delay(1200)
        _aiGenerationStatus.value = null
        
        selectPlaylist(finalPlaylist)
        playPlaylist(finalPlaylist)
        navigateTo(ScreenState.PLAYLIST_DETAILS)
    }

    // AI Personalized Recommendations State
    private val _aiRecommendedSongs = MutableStateFlow<List<Song>>(emptyList())
    val aiRecommendedSongs = _aiRecommendedSongs.asStateFlow()

    private val _aiTasteAnalysis = MutableStateFlow<String>("Awaiting analysis...")
    val aiTasteAnalysis = _aiTasteAnalysis.asStateFlow()

    fun refreshAIRecommendations() {
        viewModelScope.launch {
            _aiGenerationStatus.value = "AI is analyzing your acoustic signature..."
            kotlinx.coroutines.delay(1500)

            val songsList = allSongs.value
            if (songsList.isEmpty()) {
                _aiRecommendedSongs.value = emptyList()
                _aiTasteAnalysis.value = "Add offline/local songs to your library to kickstart AI Recommendations."
                _aiGenerationStatus.value = null
                return@launch
            }

            // Find played songs
            val playedSongs = songsList.filter { it.playCount > 0 || it.isFavorite }
            if (playedSongs.isEmpty()) {
                // Return top presets as curated starter mix
                val starterList = songsList.shuffled().take(4)
                _aiRecommendedSongs.value = starterList
                _aiTasteAnalysis.value = "Eclectic Starter: Ready to learn your signature as you play more tracks."
            } else {
                // Group by audioPreset or artist
                val favoritePreset = playedSongs.groupBy { it.audioPreset }.maxByOrNull { it.value.size }?.key ?: "ambient"
                val recommendations = songsList.filter { it.audioPreset == favoritePreset && !playedSongs.contains(it) }.shuffled().take(4)

                val finalRecs = if (recommendations.size < 4) {
                    (recommendations + songsList.filter { !playedSongs.contains(it) }.shuffled()).distinctBy { it.id }.take(4)
                } else {
                    recommendations
                }

                _aiRecommendedSongs.value = if (finalRecs.isNotEmpty()) finalRecs else songsList.shuffled().take(4)

                val vibeDescription = when (favoritePreset) {
                    "ambient" -> "Vibe: Chill & Atmospheric. You prefer spacious, calming, and soothing soundscapes."
                    "bouncy" -> "Vibe: High Energy & Bass. You enjoy rich bass lines and uplifting rhythms."
                    "cinematic" -> "Vibe: Orchestral & Deep. You prefer dramatic, cinematic, and storytelling music."
                    "romantic" -> "Vibe: Warm & Vocal. You appreciate clear vocals and expressive melodies."
                    "indie" -> "Vibe: Acoustic & Authentic. You like modern indie-folk and natural instrumentations."
                    else -> "Vibe: Dynamic Fusion. Your taste is highly versatile across genres."
                }
                _aiTasteAnalysis.value = vibeDescription
            }
            _aiGenerationStatus.value = null
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

    fun playNext(song: Song) {
        val currentQueue = audioEngine.playQueue.value.toMutableList()
        val currentSong = activeSong.value
        if (currentSong != null) {
            val currentIndex = currentQueue.indexOfFirst { it.id == currentSong.id }
            if (currentIndex != -1) {
                currentQueue.removeAll { it.id == song.id }
                currentQueue.add(currentIndex + 1, song)
            } else {
                currentQueue.add(song)
            }
        } else {
            currentQueue.removeAll { it.id == song.id }
            currentQueue.add(0, song)
        }
        audioEngine.playQueue.value = currentQueue
    }

    fun addToQueue(song: Song) {
        val currentQueue = audioEngine.playQueue.value.toMutableList()
        if (currentQueue.none { it.id == song.id }) {
            currentQueue.add(song)
            audioEngine.playQueue.value = currentQueue
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
                "Deep Bass Boost" -> state.copy(band60Hz = 9f, band230Hz = 6f, band910Hz = 1f, band3600Hz = -2f, band14000Hz = -1f)
                "Vocal Boost" -> state.copy(band60Hz = -2f, band230Hz = 0f, band910Hz = 6f, band3600Hz = 4f, band14000Hz = -1f)
                "Clear Vocals" -> state.copy(band60Hz = -3f, band230Hz = -1f, band910Hz = 4f, band3600Hz = 6f, band14000Hz = 3f)
                "Acoustic" -> state.copy(band60Hz = 4f, band230Hz = 1f, band910Hz = 2f, band3600Hz = 3f, band14000Hz = 4f)
                "Acoustic Clarity" -> state.copy(band60Hz = 3f, band230Hz = 2f, band910Hz = 1f, band3600Hz = 5f, band14000Hz = 6f)
                "Electronic" -> state.copy(band60Hz = 6f, band230Hz = 5f, band910Hz = -2f, band3600Hz = 5f, band14000Hz = 6f)
                "Studio Master" -> state.copy(band60Hz = 1f, band230Hz = 1f, band910Hz = 2f, band3600Hz = 2f, band14000Hz = 3f)
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

    fun updateBassBoostStrength(strength: Float) {
        viewModelScope.launch {
            val newState = _equalizerState.value.copy(bassBoostStrength = strength)
            _equalizerState.value = newState
            audioEngine.setEqualizerState(newState)
            repository.saveEqualizerSettings(newState)
        }
    }

    fun toggleVirtualizer(isEnabled: Boolean) {
        viewModelScope.launch {
            val newState = _equalizerState.value.copy(virtualizerEnabled = isEnabled)
            _equalizerState.value = newState
            audioEngine.setEqualizerState(newState)
            repository.saveEqualizerSettings(newState)
        }
    }

    fun updateVirtualizerStrength(strength: Float) {
        viewModelScope.launch {
            val newState = _equalizerState.value.copy(virtualizerStrength = strength)
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
                bassBoostStrength = 0.5f,
                virtualizerEnabled = false,
                virtualizerStrength = 0.5f
            )
            _equalizerState.value = newState
            audioEngine.setEqualizerState(newState)
            repository.saveEqualizerSettings(newState)
        }
    }

    // Audio format preference State
    private val _audioDownloadFormat = MutableStateFlow(prefs.getString("audio_download_format", "MP3") ?: "MP3")
    val audioDownloadFormat = _audioDownloadFormat.asStateFlow()

    fun changeAudioDownloadFormat(format: String) {
        _audioDownloadFormat.value = format
        prefs.edit().putString("audio_download_format", format).apply()
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
                val oldSongCount = allSongs.value.size
                val results = repository.scanLocalMediaStoreSongs()
                val newlyScanned = results.size - oldSongCount
                if (oldSongCount > 0 && newlyScanned > 0) {
                    triggerNewSongsNotification(newlyScanned)
                }
                
                // Safely wire the auto-scan ContentObserver now that we are sure permissions exist
                if (!mediaObserverRegistered) {
                    try {
                        repository.registerMediaObserver {
                            viewModelScope.launch {
                                try {
                                    val preCount = allSongs.value.size
                                    val r = repository.scanLocalMediaStoreSongs()
                                    val diff = r.size - preCount
                                    if (preCount > 0 && diff > 0) {
                                        triggerNewSongsNotification(diff)
                                    }
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

data class YouTubeMetadata(
    val title: String,
    val artist: String,
    val artworkUrl: String?
)
