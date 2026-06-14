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

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

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

    init {
    }

    fun setSong(songId: String, preset: String, durationSecs: Int) {
        synchronized(lock) {
            _isPlaying.value = false
            _trackDurationSeconds.value = durationSecs
            _currentPositionSeconds.value = 0

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
                    } else if (songId.startsWith("content://") || songId.startsWith("file://")) {
                        setDataSource(context, android.net.Uri.parse(songId))
                    } else {
                        setDataSource(songId)
                    }

                    setOnErrorListener { _, what, extra ->
                        Log.e("AudioEngine", "MediaPlayer error for $songId: what=$what, extra=$extra")
                        val wasPlaying = _isPlaying.value
                        _isPlaying.value = false
                        if (wasPlaying) {
                            scope.launch(Dispatchers.Main) {
                                onSongCompletionListener?.invoke()
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

                    prepare()
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
                        Log.e("AudioEngine", "Error setting speed in setSong", e)
                    }
                }
                mediaPlayer = mp
                isPlayingMedia = true
                applyEqualizerSettings()
                _trackDurationSeconds.value = mp.duration / 1000
            } catch (e: Throwable) {
                Log.e("AudioEngine", "Error preparing MediaPlayer for $songId", e)
                isPlayingMedia = false
            }
        }
    }

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true

        if (isPlayingMedia) {
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
                            val currentPos = mp.currentPosition / 1000
                            _currentPositionSeconds.value = currentPos
                        } catch (tf: Throwable) {
                            // Suppress errors during transient states
                        }
                        kotlinx.coroutines.delay(500)
                    }
                }
            }
        } else {
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
        mediaPlaybackJob?.cancel()
        mediaPlaybackJob = null

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
                    mediaPlayer?.seekTo(clamped * 1000)
                    _currentPositionSeconds.value = clamped
                } catch (e: Throwable) {
                    Log.e("AudioEngine", "Error seeking MediaPlayer", e)
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
                equalizer = Equalizer(0, mediaPlayer!!.audioSessionId)
            }
            if (bassBoost == null) {
                bassBoost = BassBoost(0, mediaPlayer!!.audioSessionId)
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
