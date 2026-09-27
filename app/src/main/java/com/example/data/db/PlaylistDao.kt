package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.DownloadedTrack
import com.example.data.model.Playlist
import com.example.data.model.PlaylistTrackItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    fun getPlaylistById(id: Long): Flow<Playlist?>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistByIdSync(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("""
        SELECT dt.* FROM downloaded_tracks dt
        INNER JOIN playlist_track_items pti ON dt.id = pti.trackId
        WHERE pti.playlistId = :playlistId
        ORDER BY pti.position ASC
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<DownloadedTrack>>

    @Query("""
        SELECT dt.* FROM downloaded_tracks dt
        INNER JOIN playlist_track_items pti ON dt.id = pti.trackId
        WHERE pti.playlistId = :playlistId
        ORDER BY pti.position ASC
    """)
    suspend fun getTracksForPlaylistSync(playlistId: Long): List<DownloadedTrack>

    @Query("SELECT * FROM playlist_track_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTrackItemsForPlaylist(playlistId: Long): List<PlaylistTrackItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackItem(item: PlaylistTrackItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackItems(items: List<PlaylistTrackItem>)

    @Query("DELETE FROM playlist_track_items WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_track_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Update
    suspend fun updateTrackItems(items: List<PlaylistTrackItem>)

    @Query("SELECT COUNT(*) FROM playlist_track_items WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun isTrackInPlaylist(playlistId: Long, trackId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_track_items WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_track_items WHERE playlistId = :playlistId")
    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int>

    @Transaction
    suspend fun reorderTracks(playlistId: Long, trackIdsInNewOrder: List<Long>) {
        val currentItems = getTrackItemsForPlaylist(playlistId)
        val itemByTrackId = currentItems.associateBy { it.trackId }
        val updated = mutableListOf<PlaylistTrackItem>()

        trackIdsInNewOrder.forEachIndexed { newIndex, trackId ->
            itemByTrackId[trackId]?.let { item ->
                if (item.position != newIndex) {
                    updated.add(item.copy(position = newIndex))
                }
            }
        }
        if (updated.isNotEmpty()) {
            updateTrackItems(updated)
        }
    }
}
