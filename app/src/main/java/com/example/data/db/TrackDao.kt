package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadedTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM downloaded_tracks ORDER BY dateAdded DESC")
    fun getAllTracks(): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloaded_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): DownloadedTrack?

    @Query("""
        SELECT * FROM downloaded_tracks 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%' 
           OR categoryTag LIKE '%' || :query || '%'
        ORDER BY dateAdded DESC
    """)
    fun searchTracks(query: String): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloaded_tracks WHERE categoryTag = :category ORDER BY dateAdded DESC")
    fun getTracksByCategory(category: String): Flow<List<DownloadedTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: DownloadedTrack): Long

    @Update
    suspend fun updateTrack(track: DownloadedTrack)

    @Query("DELETE FROM downloaded_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("UPDATE downloaded_tracks SET albumArtUri = :artUri, albumArtSource = :source, accuracyScore = :score WHERE id = :id")
    suspend fun updateAlbumArt(id: Long, artUri: String, source: String, score: Int)

    @Query("UPDATE downloaded_tracks SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long)
}
