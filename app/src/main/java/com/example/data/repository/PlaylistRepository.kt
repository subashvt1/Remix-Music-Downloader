package com.example.data.repository

import android.content.Context
import android.os.Environment
import com.example.data.db.PlaylistDao
import com.example.data.db.TrackDao
import com.example.data.model.DownloadedTrack
import com.example.data.model.Playlist
import com.example.data.model.PlaylistSummary
import com.example.data.model.PlaylistTrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

class PlaylistRepository(
    private val context: Context,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao
) {

    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    fun getPlaylistById(id: Long): Flow<Playlist?> = playlistDao.getPlaylistById(id)

    fun getTracksForPlaylist(playlistId: Long): Flow<List<DownloadedTrack>> =
        playlistDao.getTracksForPlaylist(playlistId)

    suspend fun createPlaylist(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        val playlist = Playlist(
            name = if (trimmed.isBlank()) "New Playlist" else trimmed,
            description = description.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(playlist)
    }

    suspend fun updatePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long): Boolean = withContext(Dispatchers.IO) {
        val count = playlistDao.isTrackInPlaylist(playlistId, trackId)
        if (count > 0) return@withContext false

        val maxPos = playlistDao.getMaxPosition(playlistId)
        val nextPos = maxPos + 1
        playlistDao.insertTrackItem(
            PlaylistTrackItem(
                playlistId = playlistId,
                trackId = trackId,
                position = nextPos,
                addedAt = System.currentTimeMillis()
            )
        )
        val p = playlistDao.getPlaylistByIdSync(playlistId)
        p?.let { playlistDao.updatePlaylist(it.copy(updatedAt = System.currentTimeMillis())) }
        true
    }

    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) = withContext(Dispatchers.IO) {
        var nextPos = playlistDao.getMaxPosition(playlistId) + 1
        val itemsToAdd = mutableListOf<PlaylistTrackItem>()

        for (trackId in trackIds) {
            val count = playlistDao.isTrackInPlaylist(playlistId, trackId)
            if (count == 0) {
                itemsToAdd.add(
                    PlaylistTrackItem(
                        playlistId = playlistId,
                        trackId = trackId,
                        position = nextPos++,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        if (itemsToAdd.isNotEmpty()) {
            playlistDao.insertTrackItems(itemsToAdd)
            val p = playlistDao.getPlaylistByIdSync(playlistId)
            p?.let { playlistDao.updatePlaylist(it.copy(updatedAt = System.currentTimeMillis())) }
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
        // Normalize positions
        val remaining = playlistDao.getTrackItemsForPlaylist(playlistId)
        val updated = remaining.mapIndexed { index, item -> item.copy(position = index) }
        playlistDao.updateTrackItems(updated)
        val p = playlistDao.getPlaylistByIdSync(playlistId)
        p?.let { playlistDao.updatePlaylist(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    suspend fun moveTrack(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val items = playlistDao.getTrackItemsForPlaylist(playlistId).toMutableList()
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
            return@withContext
        }
        val moved = items.removeAt(fromIndex)
        items.add(toIndex, moved)

        val newOrderTrackIds = items.map { it.trackId }
        playlistDao.reorderTracks(playlistId, newOrderTrackIds)
        val p = playlistDao.getPlaylistByIdSync(playlistId)
        p?.let { playlistDao.updatePlaylist(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    suspend fun exportPlaylistToM3u(playlist: Playlist, tracks: List<DownloadedTrack>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val playlistsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Playlists"
                ).apply { mkdirs() }

                val cleanName = playlist.name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
                val m3uFile = File(playlistsDir, "$cleanName.m3u8")

                FileWriter(m3uFile).use { writer ->
                    writer.write("#EXTM3U\n")
                    writer.write("#PLAYLIST:${playlist.name}\n")
                    writer.write("#DESCRIPTION:${playlist.description}\n\n")

                    tracks.forEach { track ->
                        val durationSec = (track.durationMs / 1000).coerceAtLeast(0)
                        writer.write("#EXTINF:$durationSec,${track.artist} - ${track.title}\n")
                        val path = if (track.filePath.isNotBlank()) track.filePath else track.contentUri ?: ""
                        writer.write("$path\n")
                    }
                }

                Result.success(m3uFile.absolutePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun getPlaylistSummaries(): Flow<List<PlaylistSummary>> {
        return combine(
            playlistDao.getAllPlaylists(),
            trackDao.getAllTracks()
        ) { playlists, _ ->
            playlists.map { playlist ->
                val tracks = playlistDao.getTracksForPlaylistSync(playlist.id)
                val totalDuration = tracks.sumOf { it.durationMs }
                val previewArts = tracks.mapNotNull { it.albumArtUri }.distinct().take(4)
                PlaylistSummary(
                    playlist = playlist,
                    trackCount = tracks.size,
                    totalDurationMs = totalDuration,
                    previewArtUris = previewArts
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun createPresetPlaylistsIfEmpty(libraryTracks: List<DownloadedTrack>) = withContext(Dispatchers.IO) {
        val existing = playlistDao.getAllPlaylists()
        // If no playlists exist and library has tracks, create sample educational playlists
        val classicalTracks = libraryTracks.filter {
            it.categoryTag.equals("Classical", ignoreCase = true) || it.genre?.contains("Classical", true) == true
        }
        val studyTracks = libraryTracks.filter {
            it.categoryTag.equals("Study", ignoreCase = true) || it.categoryTag.equals("Educational", ignoreCase = true)
        }

        if (classicalTracks.isNotEmpty()) {
            val pId = createPlaylist("Classical Masterpieces", "High focus classical compositions for deep study")
            addTracksToPlaylist(pId, classicalTracks.map { it.id })
        }

        if (studyTracks.isNotEmpty()) {
            val pId = createPlaylist("Study & Deep Focus", "Educational background music and focus sessions")
            addTracksToPlaylist(pId, studyTracks.map { it.id })
        }
    }
}
