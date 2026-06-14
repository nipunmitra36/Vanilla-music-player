package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String = "Musicly Album",
    val durationSeconds: Int = 244, // Default 04:04
    val artworkColorHex: String = "#1A301D", // Distinct artwork branding
    val audioPreset: String = "ambient", // preset for audio synth
    val isFavorite: Boolean = false,
    val orderIndex: Int = 0,
    val lyrics: String = "No lyrics found", // Synchronized/scrolling lyrics
    val artworkUri: String? = null, // Real external audio album art URI string
    val dateAdded: Long = System.currentTimeMillis(),
    val recentPlayedAt: Long = 0L,
    val playCount: Int = 0,
    val dateModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_song_cross_ref", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String
)

@Entity(tableName = "equalizer_state")
data class EqualizerState(
    @PrimaryKey val id: Int = 1, // Single row for system settings
    val isEnabled: Boolean = true,
    val band60Hz: Float = 0f,   // dB offset, e.g. -12dB to +12dB
    val band230Hz: Float = 0f,
    val band910Hz: Float = 0f,
    val band3600Hz: Float = 0f,
    val band14000Hz: Float = 0f,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Float = 0.5f
)
