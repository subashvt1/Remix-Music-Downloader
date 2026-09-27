package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_track_items",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DownloadedTrack::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["trackId"]),
        Index(value = ["playlistId", "position"])
    ]
)
data class PlaylistTrackItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistSummary(
    val playlist: Playlist,
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val previewArtUris: List<String> = emptyList()
)
