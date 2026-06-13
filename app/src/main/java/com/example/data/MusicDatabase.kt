package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY orderIndex ASC")
    fun getAllSongsFlow(): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): Song?

    @Query("SELECT * FROM songs LIMIT 1")
    suspend fun getFirstSong(): Song?
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)

    @Query("DELETE FROM playlist_song_cross_ref WHERE songId = :songId")
    suspend fun deleteCrossRefsForSong(songId: String)

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_cross_ref r ON s.id = r.songId
        WHERE r.playlistId = :playlistId
        ORDER BY s.orderIndex ASC
    """)
    suspend fun getSongsForPlaylist(playlistId: Long): List<Song>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN playlist_song_cross_ref r ON s.id = r.songId
        WHERE r.playlistId = :playlistId
        ORDER BY s.orderIndex ASC
    """)
    fun getSongsForPlaylistFlow(playlistId: Long): Flow<List<Song>>

    @Query("SELECT * FROM playlist_song_cross_ref")
    fun getAllCrossRefs(): Flow<List<PlaylistSongCrossRef>>
}

@Dao
interface EqualizerDao {
    @Query("SELECT * FROM equalizer_state WHERE id = 1")
    fun getEqualizerStateFlow(): Flow<EqualizerState?>

    @Query("SELECT * FROM equalizer_state WHERE id = 1")
    suspend fun getEqualizerState(): EqualizerState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEqualizerState(state: EqualizerState)
}

@Database(
    entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class, EqualizerState::class],
    version = 2,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun equalizerDao(): EqualizerDao
}
