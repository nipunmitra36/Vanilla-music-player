package com.example.audio

import android.media.AudioAttributes
import android.util.Log
import com.example.data.EqualizerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost

class AudioEngine private constructor(private val context: android.content.Context) {
    private val scope = CoroutineScope(Dispatchers.Default)

    // MediaPlayer for actual music files
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var isPlayingMedia = false
    private var isSeeking = false
    private var mediaPlaybackJob: Job? = null

    companion object {
        @Volatile
        private var instance: AudioEngine? = null

        fun getInstance(context: android.content.Context): AudioEngine {
            return instance ?: synchronized(this) {
                instance ?: AudioEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    // Observable states
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing = _isPreparing.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _currentBufferPercentage = MutableStateFlow(100)
    val currentBufferPercentage = _currentBufferPercentage.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError = _playbackError.asStateFlow()

    private var isPrepared = false
    private var playWhenReady = false

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    // Smart Features Configuration
    var crossfadeEnabled = false
    var crossfadeDuration = 3
    var silenceSkipEnabled = false
    
    private var volumeBoostValue = 1.0f
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    fun setVolumeBoost(boost: Float) {
        volumeBoostValue = boost.coerceIn(1.0f, 2.0f)
        applyVolumeBoost()
    }

    private fun applyVolumeBoost() {
        val le = loudnessEnhancer ?: return
        try {
            // Amplifies up to +12dB or 1200 mB of hardware loudness gain based on volume target (up to 200%)
            val gainDb = (volumeBoostValue - 1.0f) * 12f
            val gainMb = (gainDb * 100).toInt()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                le.setTargetGain(gainMb)
                Log.d("AudioEngine", "Applied volume booster: $gainMb mB")
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error setting target gain on LoudnessEnhancer", e)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        applyPlaybackSpeed()
    }

    private fun applyPlaybackSpeed() {
        val mp = mediaPlayer ?: return
        val speed = _playbackSpeed.value
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val params = mp.playbackParams
                params.speed = speed
                mp.playbackParams = params
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error setting playback speed on MediaPlayer", e)
        }
    }

    private val _currentPositionSeconds = MutableStateFlow(0)
    val currentPositionSeconds = _currentPositionSeconds.asStateFlow()

    private val _trackDurationSeconds = MutableStateFlow(0)
    val trackDurationSeconds = _trackDurationSeconds.asStateFlow()

    val activeSong = MutableStateFlow<com.example.data.Song?>(null)
    val playQueue = MutableStateFlow<List<com.example.data.Song>>(emptyList())
    // Let's also put Next, Previous functions here inside AudioEngine so they don't depend on ViewModel.

    private var onSongCompletionListener: (() -> Unit)? = null

    fun setOnSongCompletionListener(listener: (() -> Unit)?) {
        this.onSongCompletionListener = listener
    }

    private var currentEq = EqualizerState()
    private val lock = Any()
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            if (wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MusiclyWifiLock")
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error creating WifiLock", e)
        }
    }

    fun setSong(songId: String, preset: String, durationSecs: Int) {
        synchronized(lock) {
            _isPlaying.value = false
            _trackDurationSeconds.value = durationSecs
            _currentPositionSeconds.value = 0
            _playbackError.value = null
            _isPreparing.value = true
            _isBuffering.value = false
            _currentBufferPercentage.value = 0
            isPrepared = false
            playWhenReady = false
            isSeeking = false

            // Release any existing MediaPlayer completely
            try {
                equalizer?.release()
                bassBoost?.release()
            } catch (e: Throwable) {
                Log.e("AudioEngine", "Error releasing audio effects", e)
            }
            equalizer = null
            bassBoost = null

            try {
                mediaPlayer?.release()
            } catch (e: Throwable) {
                Log.e("AudioEngine", "Error releasing MediaPlayer", e)
            }
            mediaPlayer = null
            isPlayingMedia = false
            mediaPlaybackJob?.cancel()
            mediaPlaybackJob = null

            try {
                val mp = android.media.MediaPlayer().apply {
                    setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    if (songId.startsWith("external_")) {
                        val mediaId = songId.substringAfter("external_").toLongOrNull()
                        if (mediaId != null) {
                            val trackUri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                mediaId
                            )
                            setDataSource(context, trackUri)
                        } else {
                            setDataSource(songId)
                        }
                    } else if (songId.startsWith("content://")) {
                        try {
                            val uri = android.net.Uri.parse(songId)
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                setDataSource(pfd.fileDescriptor)
                            }
                        } catch (e: Exception) {
                            Log.e("AudioEngine", "Error loading content:// URI via FD, falling back", e)
                            setDataSource(context, android.net.Uri.parse(songId))
                        }
                    } else if (songId.startsWith("file://") || java.io.File(songId).exists()) {
                        try {
                            val path = if (songId.startsWith("file://")) songId.substringAfter("file://") else songId
                            val file = java.io.File(path)
                            java.io.FileInputStream(file).use { fis ->
                                setDataSource(fis.fd)
                            }
                        } catch (e: Exception) {
                            Log.e("AudioEngine", "Error loading file via FD, falling back", e)
                            setDataSource(songId)
                        }
                    } else {
                        setDataSource(songId)
                    }

                    setOnPreparedListener {
                        synchronized(lock) {
                            isPrepared = true
                            _isPreparing.value = false
                            _trackDurationSeconds.value = duration / 1000
                            _playbackError.value = null

                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    val speed = _playbackSpeed.value
                                    if (speed != 1.0f) {
                                        val params = playbackParams
                                        params.speed = speed
                                        playbackParams = params
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("AudioEngine", "Error setting speed in onPrepared", e)
                            }

                            // Setup Volume booster (LoudnessEnhancer) on the new session ID
                            try {
                                loudnessEnhancer?.release()
                            } catch (e: Exception) {}
                            loudnessEnhancer = null

                            try {
                                val le = android.media.audiofx.LoudnessEnhancer(audioSessionId)
                                le.enabled = true
                                loudnessEnhancer = le
                                applyVolumeBoost()
                            } catch (e: Exception) {
                                Log.e("AudioEngine", "LoudnessEnhancer allocation error", e)
                            }

                            // Handle fade-in initialization if crossfade is enabled
                            if (crossfadeEnabled) {
                                setVolume(0.0f, 0.0f)
                            } else {
                                setVolume(1.0f, 1.0f)
                            }

                            applyEqualizerSettings()

                            if (playWhenReady) {
                                play()
                            }
                        }
                    }

                    setOnBufferingUpdateListener { _, percent ->
                        _currentBufferPercentage.value = percent
                        if (percent >= 100) {
                            _isBuffering.value = false
                        }
                    }

                    setOnInfoListener { _, what, extra ->
                        when (what) {
                            android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                                _isBuffering.value = true
                            }
                            android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                                _isBuffering.value = false
                            }
                        }
                        true
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e("AudioEngine", "MediaPlayer error for $songId: what=$what, extra=$extra")
                        synchronized(lock) {
                            _isPreparing.value = false
                            _isBuffering.value = false
                            val errorMsg = when (extra) {
                                android.media.MediaPlayer.MEDIA_ERROR_IO -> "Network connection timeout or IO error."
                                android.media.MediaPlayer.MEDIA_ERROR_MALFORMED -> "Malformed streaming media stream."
                                android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Unsupported audio format."
                                android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Streaming connection timed out."
                                else -> "Broken or unavailable music stream link."
                            }
                            _playbackError.value = errorMsg
                            val wasPlaying = _isPlaying.value
                            _isPlaying.value = false
                            isPrepared = false
                            if (wasPlaying) {
                                scope.launch(Dispatchers.Main) {
                                    onSongCompletionListener?.invoke()
                                }
                            }
                        }
                        true
                    }

                    setOnCompletionListener {
                        val wasPlaying = _isPlaying.value
                        _isPlaying.value = false
                        pause()
                        if (wasPlaying) {
                            scope.launch(Dispatchers.Main) {
                                onSongCompletionListener?.invoke()
                            }
                        }
                    }

                    setOnSeekCompleteListener {
                        synchronized(lock) {
                            isSeeking = false
                        }
                    }

                    // For network URLs, always prepareAsync to avoid NetworkOnMainThreadException or UI freezes!
                    if (songId.startsWith("http://") || songId.startsWith("https://")) {
                        prepareAsync()
                    } else {
                        // For local tracks, we can call traditional prepare() synchronously as it is extremely fast and low-latency
                        try {
                            prepare()
                            isPrepared = true
                            _isPreparing.value = false
                            _trackDurationSeconds.value = duration / 1000
                            applyEqualizerSettings()
                        } catch (e: Throwable) {
                            Log.e("AudioEngine", "Local prepare failed", e)
                            prepareAsync()
                        }
                    }
                }
                mediaPlayer = mp
                isPlayingMedia = true
            } catch (e: Throwable) {
                Log.e("AudioEngine", "Error initializing or Preparing MediaPlayer for $songId", e)
                isPlayingMedia = false
                _isPreparing.value = false
                _isBuffering.value = false
                _playbackError.value = "Failed to initialize audio player device: ${e.localizedMessage}"
            }
        }
    }

    fun play() {
        if (_isPlaying.value && isPrepared) return

        try {
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (e: Exception) {}

        if (isPlayingMedia) {
            synchronized(lock) {
                if (!isPrepared) {
                    playWhenReady = true
                    _isPlaying.value = true // seamlessly show loading/active state
                    return
                }
            }
            _isPlaying.value = true
            val mp = mediaPlayer
            if (mp != null) {
                try {
                    applyPlaybackSpeed()
                    mp.start()
                } catch (e: Throwable) {
                    Log.e("AudioEngine", "Error calling start() on MediaPlayer", e)
                }

                // Launch periodic position tracker job
                mediaPlaybackJob = scope.launch {
                    while (isActive && _isPlaying.value) {
                        try {
                            if (!isSeeking) {
                                val currentPosMs = mp.currentPosition
                                val currentPosSec = currentPosMs / 1000
                                _currentPositionSeconds.value = currentPosSec
                            }
                            val currentPosSec = _currentPositionSeconds.value
                            val durationSec = _trackDurationSeconds.value

                            // Dynamic fade controls if Crossfade is active
                            if (crossfadeEnabled && durationSec > crossfadeDuration) {
                                val elapsedSec = currentPosSec
                                val remainingSec = durationSec - currentPosSec

                                if (elapsedSec < crossfadeDuration) {
                                    // Fade-in ramp: 0.0 to 1.0
                                    val vol = elapsedSec.toFloat() / crossfadeDuration
                                    mp.setVolume(vol, vol)
                                } else if (remainingSec <= crossfadeDuration) {
                                    // Fade-out ramp: 1.0 to 0.0
                                    val vol = remainingSec.toFloat() / crossfadeDuration
                                    mp.setVolume(vol.coerceIn(0.0f, 1.0f), vol.coerceIn(0.0f, 1.0f))
                                } else {
                                    // Mid-song: full volume status
                                    mp.setVolume(1.0f, 1.0f)
                                }
                            }

                            // Dynamic Silence Skip: if nearing end and silence skip active, skip silence Outro
                            if (silenceSkipEnabled && durationSec > 10 && currentPosSec >= durationSec - 6) {
                                Log.d("AudioEngine", "Silence skip triggered at $currentPosSec seconds")
                                launch(Dispatchers.Main) {
                                    _isPlaying.value = false
                                    pause()
                                    onSongCompletionListener?.invoke()
                                }
                                break
                            }
                        } catch (tf: Throwable) {
                            // Suppress errors during transient states
                        }
                        kotlinx.coroutines.delay(250)
                    }
                }
            }
        } else {
            _isPlaying.value = true
            // Mock playback for placeholder synthetic songs
            mediaPlaybackJob = scope.launch {
                while (isActive && _isPlaying.value) {
                    val delayMs = (1000 / _playbackSpeed.value).toLong().coerceIn(100, 5000)
                    kotlinx.coroutines.delay(delayMs)
                    val nextSec = _currentPositionSeconds.value + 1
                    if (nextSec >= _trackDurationSeconds.value) {
                        _currentPositionSeconds.value = _trackDurationSeconds.value
                        _isPlaying.value = false
                        launch(Dispatchers.Main) { onSongCompletionListener?.invoke() }
                    } else {
                        _currentPositionSeconds.value = nextSec
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun skipNext() {
        val queue = playQueue.value
        val current = activeSong.value
        if (queue.isNotEmpty() && current != null) {
            val i = queue.indexOfFirst { it.id == current.id }
            val nextSong = if (i != -1 && i < queue.size - 1) queue[i + 1] else queue.first()
            setSong(nextSong.id, nextSong.audioPreset, nextSong.durationSeconds)
            activeSong.value = nextSong
            play()
            updateService(nextSong)
        }
    }

    fun skipPrevious() {
        val queue = playQueue.value
        val current = activeSong.value
        if (queue.isNotEmpty() && current != null) {
            val i = queue.indexOfFirst { it.id == current.id }
            val prevSong = if (i > 0) queue[i - 1] else queue.last()
            setSong(prevSong.id, prevSong.audioPreset, prevSong.durationSeconds)
            activeSong.value = prevSong
            play()
            updateService(prevSong)
        }
    }

    private fun updateService(song: com.example.data.Song) {
        try {
            val intent = android.content.Intent(context, MusicService::class.java).apply {
                action = "UPDATE"
                putExtra("SONG_ID", song.id)
                putExtra("SONG_ARTWORK", song.artworkUri)
                putExtra("SONG_TITLE", song.title)
                putExtra("SONG_ARTIST", song.artist)
                putExtra("SONG_COLOR", song.artworkColorHex)
                putExtra("IS_PLAYING", true)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch(e: Throwable){}
    }

    fun pause() {
        _isPlaying.value = false
        playWhenReady = false
        mediaPlaybackJob?.cancel()
        mediaPlaybackJob = null

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {}

        if (isPlayingMedia) {
            try {
                mediaPlayer?.pause()
            } catch (e: Throwable) {
                Log.e("AudioEngine", "Error pausing MediaPlayer", e)
            }
        }
    }

    fun seekTo(seconds: Int) {
        synchronized(lock) {
            val clamped = seconds.coerceIn(0, _trackDurationSeconds.value)
            if (isPlayingMedia) {
                try {
                    isSeeking = true
                    mediaPlayer?.seekTo(clamped * 1000)
                    _currentPositionSeconds.value = clamped
                } catch (e: Throwable) {
                    Log.e("AudioEngine", "Error seeking MediaPlayer", e)
                    isSeeking = false
                }
            } else {
                _currentPositionSeconds.value = clamped
            }
        }
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private fun getClosestBand(eq: Equalizer, frequencyHz: Int): Short {
        val numBands = eq.numberOfBands
        var closestBand: Short = 0
        var minDiff = Int.MAX_VALUE
        val targetMilliHz = frequencyHz * 1000
        for (i in 0 until numBands.toInt()) {
            val bandFreqs = try { eq.getBandFreqRange(i.toShort()) } catch (e: Exception) { null }
            if (bandFreqs != null && bandFreqs.size >= 2) {
                val centerFreq = (bandFreqs[0] + bandFreqs[1]) / 2
                val diff = kotlin.math.abs(centerFreq - targetMilliHz)
                if (diff < minDiff) {
                    minDiff = diff
                    closestBand = i.toShort()
                }
            } else {
                val centerFreq = try { eq.getCenterFreq(i.toShort()) } catch (e: Exception) { 0 }
                if (centerFreq > 0) {
                    val diff = kotlin.math.abs(centerFreq - targetMilliHz)
                    if (diff < minDiff) {
                        minDiff = diff
                        closestBand = i.toShort()
                    }
                }
            }
        }
        return closestBand
    }

    private fun setTrackBand(frequencyHz: Int, valueDb: Float, minLevel: Short, maxLevel: Short) {
        val eq = equalizer ?: return
        try {
            val band = getClosestBand(eq, frequencyHz)
            val levelMillibels = (valueDb * 100).toInt().toShort()
            val clampedLevel = levelMillibels.coerceIn(minLevel, maxLevel)
            eq.setBandLevel(band, clampedLevel)
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error setting band for frequency $frequencyHz", e)
        }
    }

    private fun applyEqualizerSettings() {
        if (mediaPlayer == null) return
        try {
            if (equalizer == null) {
                try {
                    equalizer = Equalizer(0, mediaPlayer!!.audioSessionId)
                } catch (e: Exception) {
                    Log.e("AudioEngine", "Error instantiating Equalizer", e)
                }
            }
            if (bassBoost == null) {
                try {
                    bassBoost = BassBoost(0, mediaPlayer!!.audioSessionId)
                } catch (e: Exception) {
                    Log.e("AudioEngine", "Error instantiating BassBoost", e)
                }
            }

            equalizer?.enabled = currentEq.isEnabled
            if (currentEq.isEnabled && equalizer != null) {
                val range = try { equalizer!!.bandLevelRange } catch (e: Exception) { null }
                val minLevel: Short = (range?.get(0) ?: -1500).toShort()
                val maxLevel: Short = (range?.get(1) ?: 1500).toShort()

                setTrackBand(60, currentEq.band60Hz, minLevel, maxLevel)
                setTrackBand(230, currentEq.band230Hz, minLevel, maxLevel)
                setTrackBand(910, currentEq.band910Hz, minLevel, maxLevel)
                setTrackBand(3600, currentEq.band3600Hz, minLevel, maxLevel)
                setTrackBand(14000, currentEq.band14000Hz, minLevel, maxLevel)
            }

            bassBoost?.enabled = currentEq.bassBoostEnabled
            if (currentEq.bassBoostEnabled && bassBoost != null) {
                bassBoost?.setStrength((currentEq.bassBoostStrength * 1000).toInt().toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEqualizerState(state: EqualizerState) {
        synchronized(lock) {
            currentEq = state
            applyEqualizerSettings()
        }
    }

    fun release() {
        pause()
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {}
        wifiLock = null
        try {
            equalizer?.release()
            bassBoost?.release()
            mediaPlayer?.release()
        } catch (e: Throwable) {
            Log.e("AudioEngine", "Error releasing MediaPlayer", e)
        }
        equalizer = null
        bassBoost = null
        mediaPlayer = null
    }
}
