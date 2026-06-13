package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.imageLoader
import coil.request.ImageRequest
import android.graphics.drawable.BitmapDrawable

class MusicService : Service() {

    private val CHANNEL_ID = "musicly_playback_channel"
    private val NOTIFICATION_ID = 2623
    private var mediaSession: android.support.v4.media.session.MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = android.support.v4.media.session.MediaSessionCompat(this, "MusicService")
        mediaSession?.isActive = true
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.isActive = false
        mediaSession?.release()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "STOP" -> stopForegroundService()
            "PLAY_PAUSE" -> {
                AudioEngine.getInstance(this).togglePlayPause()
                val currentSong = AudioEngine.getInstance(this).activeSong.value
                showNotification(
                    songId = currentSong?.id ?: "",
                    artworkUri = currentSong?.artworkUri,
                    title = currentSong?.title ?: "Musicly Song",
                    artist = currentSong?.artist ?: "Musicly Artist",
                    isPlaying = AudioEngine.getInstance(this).isPlaying.value,
                    colorHex = currentSong?.artworkColorHex
                )
            }
            "NEXT" -> AudioEngine.getInstance(this).skipNext()
            "PREVIOUS" -> AudioEngine.getInstance(this).skipPrevious()
            "START", "UPDATE" -> {
                val songId = intent.getStringExtra("SONG_ID") ?: ""
                val artworkUri = intent.getStringExtra("SONG_ARTWORK")
                val songTitle = intent.getStringExtra("SONG_TITLE") ?: "Musicly Song"
                val songArtist = intent.getStringExtra("SONG_ARTIST") ?: "Musicly Artist"
                val colorHex = intent.getStringExtra("SONG_COLOR")
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", true)
                showNotification(songId, artworkUri, songTitle, songArtist, isPlaying, colorHex)
            }
        }
        return START_STICKY
    }

    private fun getArtworkUriString(songId: String, artworkUri: String?): String? {
        if (!artworkUri.isNullOrBlank()) {
            return artworkUri
        }
        return when (songId) {
            "song_tauba" -> "https://images.unsplash.com/photo-1448375240586-882707db888b?q=80&w=400"
            "song_dilko" -> "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?q=80&w=400"
            "song_vibes" -> "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=400"
            "song_midnight" -> "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=400"
            "song_lofi" -> "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?q=80&w=400"
            "" -> null
            else -> "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=400"
        }
    }

    private fun showNotification(
        songId: String,
        artworkUri: String?,
        title: String,
        artist: String,
        isPlaying: Boolean,
        colorHex: String? = null
    ) {
        val parsedColorHex = colorHex ?: "#9C27B0"
        
        // Generate a 256x256 solid bitmap for lock screen album art placeholder
        val fallbackBitmap = try {
            val bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.parseColor(parsedColorHex))
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                alpha = 100
                textSize = 100f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("♫", 128f, 164f, paint)
            bitmap
        } catch (e: Exception) {
            null
        }

        // Helper to construct and display the notification with the provided bitmap
        fun buildAndPostNotification(albumArtBitmap: android.graphics.Bitmap?) {
            mediaSession?.setMetadata(
                android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArtBitmap)
                    .putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART, albumArtBitmap)
                    .putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, albumArtBitmap)
                    .build()
            )
            mediaSession?.setPlaybackState(
                android.support.v4.media.session.PlaybackStateCompat.Builder()
                    .setState(
                        if (isPlaying) android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                        else android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                        0,
                        1f
                    )
                    .setActions(
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .build()
            )

            val notificationIntent = Intent(this, MainActivity::class.java).apply {
                putExtra("OPEN_NOW_PLAYING", true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val playPauseAction = NotificationCompat.Action(
                playPauseIcon, "Play/Pause",
                getServiceIntent("PLAY_PAUSE")
            )

            val prevAction = NotificationCompat.Action(
                android.R.drawable.ic_media_previous, "Previous",
                getServiceIntent("PREVIOUS")
            )

            val nextAction = NotificationCompat.Action(
                android.R.drawable.ic_media_next, "Next",
                getServiceIntent("NEXT")
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setLargeIcon(albumArtBitmap)
                .setContentIntent(pendingIntent)
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSession?.sessionToken)
                )
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                }
            }
        }

        // Post the fallback notification immediately
        buildAndPostNotification(fallbackBitmap)

        // Asynchronously load the actual artwork
        val resolvedUri = getArtworkUriString(songId, artworkUri)
        if (resolvedUri != null) {
            serviceScope.launch {
                try {
                    val loader = this@MusicService.imageLoader
                    val request = ImageRequest.Builder(this@MusicService)
                        .data(if (resolvedUri.startsWith("/")) java.io.File(resolvedUri) else resolvedUri)
                        .allowHardware(false) // Software bitmap is required for system notifications & remote metadata
                        .build()
                    val result = loader.execute(request)
                    val drawable = result.drawable
                    if (drawable != null) {
                        val bitmap = if (drawable is BitmapDrawable) {
                            drawable.bitmap
                        } else {
                            val bmp = android.graphics.Bitmap.createBitmap(
                                drawable.intrinsicWidth.coerceAtLeast(1),
                                drawable.intrinsicHeight.coerceAtLeast(1),
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bmp)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            bmp
                        }
                        withContext(Dispatchers.Main) {
                            buildAndPostNotification(bitmap)
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getServiceIntent(actionStr: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { action = actionStr }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(this, actionStr.hashCode(), intent, flags)
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Settings",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for background offline music playback"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
