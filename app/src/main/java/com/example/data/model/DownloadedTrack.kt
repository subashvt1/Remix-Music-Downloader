package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val filePath: String,
    val contentUri: String? = null,
    val mimeType: String = "audio/mpeg",
    val albumArtUri: String? = null,
    val originalAlbumArtUri: String? = null,
    val albumArtSource: String = "EMBEDDED", // "EMBEDDED", "ACCURATE_ONLINE", "CUSTOM", "NONE"
    val accuracyScore: Int = 0, // 0 to 100 confidence score of album art
    val categoryTag: String = "Educational", // "Educational", "Classical", "Study", "Lecture", "General"
    val dateAdded: Long = System.currentTimeMillis(),
    val downloadSourceUrl: String? = null,
    val isFavorite: Boolean = false,
    val year: String? = null,
    val genre: String? = null
)
