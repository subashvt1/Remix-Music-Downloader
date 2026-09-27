package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.db.TrackDao
import com.example.data.media.MetadataExtractor
import com.example.data.model.AlbumArtCandidate
import com.example.data.model.DownloadProgress
import com.example.data.model.DownloadedTrack
import com.example.data.network.AlbumArtSearchApi
import com.example.data.network.MusicDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class TrackRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val downloadService: MusicDownloadService = MusicDownloadService(context),
    private val artSearchApi: AlbumArtSearchApi = AlbumArtSearchApi()
) {

    fun getAllTracks(): Flow<List<DownloadedTrack>> = trackDao.getAllTracks()

    fun searchTracks(query: String): Flow<List<DownloadedTrack>> = trackDao.searchTracks(query)

    fun getTracksByCategory(category: String): Flow<List<DownloadedTrack>> =
        if (category.equals("All", ignoreCase = true)) {
            trackDao.getAllTracks()
        } else {
            trackDao.getTracksByCategory(category)
        }

    suspend fun getTrackById(id: Long): DownloadedTrack? = trackDao.getTrackById(id)

    suspend fun downloadAndProcessMusic(
        url: String,
        suggestedTitle: String? = null,
        suggestedArtist: String? = null,
        category: String = "Educational",
        autoUpdateAccurateArt: Boolean = true,
        onProgress: (DownloadProgress) -> Unit
    ): Result<DownloadedTrack> = withContext(Dispatchers.IO) {
        // 1. Download file to Music folder
        val downloadResult = downloadService.downloadAudioFile(url, suggestedTitle, onProgress)
        if (!downloadResult.success || (downloadResult.contentUri == null && downloadResult.filePath.isBlank())) {
            return@withContext Result.failure(
                Exception(downloadResult.errorMessage ?: "Failed to download audio file")
            )
        }

        onProgress(
            DownloadProgress(
                isDownloading = true,
                trackTitle = downloadResult.fileName,
                progressPercent = 100,
                statusText = "Reading embedded ID3 tags & album art..."
            )
        )

        // 2. Read embedded album art and ID3 metadata
        val fileUri = downloadResult.contentUri ?: Uri.fromFile(File(downloadResult.filePath))
        val initialTitle = suggestedTitle ?: downloadResult.fileName.substringBeforeLast(".")
        val initialArtist = suggestedArtist ?: "Unknown Artist"

        val extracted = MetadataExtractor.extract(
            context = context,
            fileUri = fileUri,
            fallbackTitle = initialTitle,
            fallbackArtist = initialArtist
        )

        var finalArtUri = extracted.embeddedArtFileUri
        var artSource = if (extracted.hasEmbeddedArt) "EMBEDDED" else "NONE"
        var accuracyScore = if (extracted.hasEmbeddedArt) 70 else 0

        // 3. Try to find the most accurate album art online if requested
        if (autoUpdateAccurateArt) {
            onProgress(
                DownloadProgress(
                    isDownloading = true,
                    trackTitle = extracted.title,
                    progressPercent = 100,
                    statusText = "Searching for accurate high-res artwork..."
                )
            )

            try {
                val candidates = artSearchApi.searchAccurateArtwork(
                    title = extracted.title,
                    artist = if (extracted.artist != "Unknown Artist") extracted.artist else ""
                )

                val bestCandidate = candidates.firstOrNull { it.matchScore >= 60 }
                if (bestCandidate != null) {
                    val savedArtUri = MetadataExtractor.saveArtFromUrl(
                        context = context,
                        imageUrl = bestCandidate.highResArtworkUrl,
                        trackId = System.currentTimeMillis()
                    )
                    if (savedArtUri != null) {
                        finalArtUri = savedArtUri
                        artSource = "ACCURATE_ONLINE"
                        accuracyScore = bestCandidate.matchScore
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4. Save into local Room database
        val track = DownloadedTrack(
            title = extracted.title,
            artist = extracted.artist,
            album = extracted.album,
            durationMs = extracted.durationMs,
            fileSize = downloadResult.fileSize,
            filePath = downloadResult.filePath,
            contentUri = downloadResult.contentUri?.toString(),
            mimeType = downloadResult.mimeType,
            albumArtUri = finalArtUri,
            originalAlbumArtUri = extracted.embeddedArtFileUri,
            albumArtSource = artSource,
            accuracyScore = accuracyScore,
            categoryTag = category,
            dateAdded = System.currentTimeMillis(),
            downloadSourceUrl = url,
            year = extracted.year,
            genre = extracted.genre
        )

        val insertedId = trackDao.insertTrack(track)
        val savedTrack = track.copy(id = insertedId)

        onProgress(DownloadProgress(isDownloading = false, statusText = "Ready"))
        Result.success(savedTrack)
    }

    suspend fun searchArtCandidates(track: DownloadedTrack): List<AlbumArtCandidate> = withContext(Dispatchers.IO) {
        artSearchApi.searchAccurateArtwork(
            title = track.title,
            artist = if (track.artist != "Unknown Artist") track.artist else ""
        )
    }

    suspend fun applyCandidateArt(trackId: Long, candidate: AlbumArtCandidate): Boolean = withContext(Dispatchers.IO) {
        val savedUri = MetadataExtractor.saveArtFromUrl(
            context = context,
            imageUrl = candidate.highResArtworkUrl,
            trackId = trackId
        ) ?: return@withContext false

        trackDao.updateAlbumArt(
            id = trackId,
            artUri = savedUri,
            source = "ACCURATE_ONLINE",
            score = candidate.matchScore
        )
        true
    }

    suspend fun restoreOriginalEmbeddedArt(trackId: Long) = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(trackId) ?: return@withContext
        val original = track.originalAlbumArtUri
        if (original != null) {
            trackDao.updateAlbumArt(
                id = trackId,
                artUri = original,
                source = "EMBEDDED",
                score = 70
            )
        }
    }

    suspend fun updateTrack(track: DownloadedTrack) = withContext(Dispatchers.IO) {
        trackDao.updateTrack(track)
    }

    suspend fun deleteTrack(track: DownloadedTrack) = withContext(Dispatchers.IO) {
        // Delete from database
        trackDao.deleteTrackById(track.id)

        // Delete from MediaStore / filesystem if possible
        try {
            if (!track.contentUri.isNullOrBlank()) {
                context.contentResolver.delete(Uri.parse(track.contentUri), null, null)
            } else if (track.filePath.isNotBlank()) {
                val f = File(track.filePath)
                if (f.exists()) f.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun toggleFavorite(trackId: Long) = withContext(Dispatchers.IO) {
        trackDao.toggleFavorite(trackId)
    }
}
