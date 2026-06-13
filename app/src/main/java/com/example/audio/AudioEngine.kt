package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.data.EqualizerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class AudioEngine private constructor(private val context: android.content.Context) {
    private val SAMPLE_RATE = 22050
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
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

    private val _currentPositionSeconds = MutableStateFlow(0)
    val currentPositionSeconds = _currentPositionSeconds.asStateFlow()

    private val _trackDurationSeconds = MutableStateFlow(244) // 4:04
    val trackDurationSeconds = _trackDurationSeconds.asStateFlow()

    private var onSongCompletionListener: (() -> Unit)? = null

    fun setOnSongCompletionListener(listener: (() -> Unit)?) {
        this.onSongCompletionListener = listener
    }

    // Sound variables
    private var sampleCount: Long = 0
    private var activePreset: String = "ambient"
    private var currentEq = EqualizerState()

    // Mutex or lock for synth updates
    private val lock = Any()

    // Do not initialize AudioTrack on startup to avoid blocking or crashing on unsupported platforms/emulators.
    init {
        // Empty to ensure instant startup
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            // Protect against negative buffer size errors on some devices/emulators
            val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack?.play()
        } catch (e: Throwable) {
            Log.e("AudioEngine", "Error initializing AudioTrack", e)
        }
    }

    fun setSong(songId: String, preset: String, durationSecs: Int) {
        synchronized(lock) {
            activePreset = preset
            _trackDurationSeconds.value = durationSecs
            sampleCount = 0
            _currentPositionSeconds.value = 0

            // Release any existing MediaPlayer completely
            try {
                mediaPlayer?.release()
            } catch (e: Throwable) {
                Log.e("AudioEngine", "Error releasing MediaPlayer", e)
            }
            mediaPlayer = null
            isPlayingMedia = false
            mediaPlaybackJob?.cancel()
            mediaPlaybackJob = null

            // If ID indicates a real audio file (MediaStore content, downloaded file, or custom path)
            val isSynthetic = songId.startsWith("song_")
            if (!isSynthetic) {
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
                            scope.launch(Dispatchers.Main) {
                                onSongCompletionListener?.invoke()
                            }
                            _isPlaying.value = false
                            true
                        }

                        setOnCompletionListener {
                            _isPlaying.value = false
                            pause()
                            scope.launch(Dispatchers.Main) {
                                onSongCompletionListener?.invoke()
                            }
                        }

                        prepare()
                    }
                    mediaPlayer = mp
                    isPlayingMedia = true
                    _trackDurationSeconds.value = mp.duration / 1000
                } catch (e: Throwable) {
                    Log.e("AudioEngine", "Error preparing MediaPlayer for $songId, fallback to synth", e)
                    isPlayingMedia = false
                }
            } else {
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
                    mp.start()
                } catch (e: Throwable) {
                    Log.e("AudioEngine", "Error calling start() on MediaPlayer, attempting re-prep", e)
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
            return
        }

        // If not media, execute synthesis loop
        if (audioTrack == null || audioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
            initAudioTrack()
        }

        try {
            audioTrack?.play()
        } catch (e: Throwable) {
            Log.e("AudioEngine", "Error calling play() on AudioTrack, reinitializing...", e)
            initAudioTrack()
        }

        playbackJob = scope.launch {
            val bufferSize = 1024
            val buffer = ShortArray(bufferSize)

            while (isActive && _isPlaying.value) {
                try {
                    if (audioTrack == null || audioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
                        kotlinx.coroutines.delay(150)
                        continue
                    }
                    synchronized(lock) {
                        val eqState = currentEq
                        
                        // EQ decibel gain conversion to multipliers (nominal 1.0 at 0dB)
                        // Maps dB offset (-12 to +12) to scale factor (from ~0.1x to ~3.0x)
                        val dbToMult = { db: Float ->
                            if (eqState.isEnabled) {
                                Math.pow(10.0, (db / 15.0)).toFloat()
                            } else {
                                1.0f
                            }
                        }

                        val g60 = dbToMult(eqState.band60Hz) * (if (eqState.bassBoostEnabled) 2.2f else 1.0f)
                        val g230 = dbToMult(eqState.band230Hz)
                        val g910 = dbToMult(eqState.band910Hz)
                        val g3600 = dbToMult(eqState.band3600Hz)
                        val g14000 = dbToMult(eqState.band14000Hz)

                        for (i in 0 until bufferSize) {
                            val t = sampleCount / SAMPLE_RATE.toDouble()
                            
                            // Synthesize 5 distinct frequency layers
                            
                            // 1. Bass band (60Hz range - rich drone + soft steady kick beat)
                            val beatInterval = SAMPLE_RATE / 1.5 // pulse beat rate
                            val beatT = (sampleCount % beatInterval) / beatInterval
                            val kickDecay = Math.max(0.0, 1.0 - beatT * 5.0)
                            val kick = sin(2.0 * Math.PI * 55.0 * t) * kickDecay * 15000.0
                            val bassDrone = sin(2.0 * Math.PI * 65.0 * t) * 3000.0
                            val bass = (kick + bassDrone) * g60

                            // 2. Mid-bass band (230Hz range - warm synth chords bassline)
                            val chordFreqs = getChordRootFreq()
                            val bassline = sin(2.0 * Math.PI * chordFreqs.first * t) * 4500.0 * g230

                            // 3. Mid Range band (910Hz range - lush chords melody pads)
                            val padSine = sin(2.0 * Math.PI * chordFreqs.second * t)
                            val padTri = getTriangleSample(chordFreqs.second * 1.5f, t) * 0.5f // add harmonic
                            val midRange = (padSine + padTri) * 3500.0 * g910

                            // 4. High-mid band (3600Hz range - arpeggiator / chime pluck melodies)
                            val chimeFreq = getArpFreq(t)
                            val decayMultiplier = getArpDecay(t)
                            val keyPluck = sin(2.0 * Math.PI * chimeFreq * t) * decayMultiplier * 3000.0
                            val highMid = keyPluck * g3600

                            // 5. Treble band (14000Hz range - white noise rainfall + crisp hi-hat ticks)
                            val noise = (Math.random() * 2.0 - 1.0) * 80.0
                            val tickT = (sampleCount % (beatInterval / 2.0)) / (beatInterval / 2.0)
                            val tickDecay = Math.max(0.0, 1.0 - tickT * 32.0)
                            val hihat = (Math.random() * 2.0 - 1.0) * tickDecay * 900.0
                            val treble = (noise + hihat) * g14000

                            // Sum layers & safe clip
                            val sum = bass + bassline + midRange + highMid + treble
                            buffer[i] = sum.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()

                            sampleCount++
                        }
                    }

                    // Push buffer to AudioTrack
                    audioTrack?.write(buffer, 0, buffer.size)

                    // Update play position seconds
                    val posSecs = (sampleCount / SAMPLE_RATE).toInt()
                    if (posSecs != _currentPositionSeconds.value) {
                        if (posSecs >= _trackDurationSeconds.value) {
                            // Loop or trigger next track automatically
                            _currentPositionSeconds.value = _trackDurationSeconds.value
                            pause()
                            scope.launch(Dispatchers.Main) {
                                onSongCompletionListener?.invoke()
                            }
                        } else {
                            _currentPositionSeconds.value = posSecs
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("AudioEngine", "Error in playback loop, attempting auto-reconnect", e)
                    kotlinx.coroutines.delay(250)
                }
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        mediaPlaybackJob?.cancel()
        mediaPlaybackJob = null

        if (isPlayingMedia) {
            try {
                mediaPlayer?.pause()
            } catch (e: Throwable) {
                Log.e("AudioEngine", "Error pausing MediaPlayer", e)
            }
        } else {
            try {
                audioTrack?.pause()
            } catch (e: Throwable) {
                Log.e("AudioEngine", "Error calling pause() on AudioTrack", e)
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
                sampleCount = clamped.toLong() * SAMPLE_RATE
                _currentPositionSeconds.value = clamped
            }
        }
    }

    fun setEqualizerState(state: EqualizerState) {
        synchronized(lock) {
            currentEq = state
        }
    }

    fun release() {
        pause()
        try {
            mediaPlayer?.release()
        } catch (e: Throwable) {
            Log.e("AudioEngine", "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null

        try {
            audioTrack?.flush()
            audioTrack?.release()
        } catch (e: Throwable) {
            Log.e("AudioEngine", "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }

    // Helper Synthesizer algorithms based on presets & chords
    private fun getChordRootFreq(): Pair<Float, Float> {
        val seconds = (sampleCount / SAMPLE_RATE)
        val measure = (seconds / 4) % 4 // changes chord every 4 seconds
        return when (activePreset) {
            "romantic" -> { // Dil Ko Karaar Aaya: Warm loving progression
                when(measure) {
                    0L -> Pair(110.0f, 220.0f) // A3, A4
                    1L -> Pair(130.8f, 261.6f) // C3, C4
                    2L -> Pair(98.0f, 196.0f)  // G3, G4
                    else -> Pair(116.5f, 233.1f) // Bb3, Bb4
                }
            }
            "indie" -> { // Thik Emon Ebhabe: acoustic guitar style
                when(measure) {
                    0L -> Pair(73.4f, 146.8f) // D3, D4
                    1L -> Pair(82.4f, 164.8f) // E3, E4
                    2L -> Pair(110.0f, 220.0f) // A3, A4
                    else -> Pair(98.0f, 196.0f)  // G3, G4
                }
            }
            "cinematic" -> { // Teri Meri Kahaani: epic progressions
                when(measure) {
                    0L -> Pair(87.3f, 174.6f) // F3, F4
                    1L -> Pair(98.0f, 196.0f)  // G3, G4
                    2L -> Pair(82.4f, 164.8f) // E3, E4
                    else -> Pair(110.0f, 220.0f) // A3, A4
                }
            }
            "bouncy" -> { // Tauba: Upbeat syncopated chords
                when(measure) {
                    0L -> Pair(130.8f, 261.6f) // C3, C4
                    1L -> Pair(146.8f, 293.7f) // D3, D4
                    2L -> Pair(164.8f, 329.6f) // E3, E4
                    else -> Pair(110.0f, 220.0f) // A3, A4
                }
            }
            "pentatonic" -> { // Chupi Chupi Mon: playful high range
                when(measure) {
                    0L -> Pair(116.5f, 233.1f)
                    1L -> Pair(130.8f, 261.6f)
                    2L -> Pair(146.8f, 293.7f)
                    else -> Pair(98.0f, 196.0f)
                }
            }
            else -> { // Default ambient lofi loop
                when (measure) {
                    0L -> Pair(87.3f, 174.6f) // F3 / F4
                    1L -> Pair(98.0f, 196.0f)  // G3 / G4
                    2L -> Pair(82.4f, 164.8f) // E3 / E4
                    else -> Pair(110.0f, 220.0f)// A3 / A4
                }
            }
        }
    }

    private fun getArpFreq(t: Double): Float {
        val seconds = (sampleCount / SAMPLE_RATE.toDouble())
        val index = ((seconds * 6).toInt()) % 8 // 16th notes
        val roots = getChordRootFreq()
        
        // Form a musical pentatonic scale centered around chord roots
        val scale = floatArrayOf(1.0f, 1.2f, 1.25f, 1.5f, 1.66f, 1.875f, 2.0f, 2.4f)
        val multiplier = scale[Math.abs(index + roots.first.toInt()) % 8]
        return roots.second * multiplier
    }

    private fun getArpDecay(t: Double): Double {
        val seconds = (sampleCount / SAMPLE_RATE.toDouble())
        val arpIntervalSec = 1.0 / 6.0
        val relativeT = (seconds % arpIntervalSec) / arpIntervalSec
        return Math.max(0.0, 1.0 - relativeT * 4.0)
    }

    private fun getTriangleSample(freq: Float, t: Double): Float {
        val period = 1.0 / freq
        val relativePos = (t % period) / period
        return if (relativePos < 0.5) {
            (relativePos * 4.0 - 1.0).toFloat()
        } else {
            ((1.0 - relativePos) * 4.0 - 1.0).toFloat()
        }
    }
}
