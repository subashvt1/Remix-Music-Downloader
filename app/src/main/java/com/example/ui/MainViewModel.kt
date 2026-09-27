package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.media.AudioPlayerManager
import com.example.data.media.PlaybackState
import com.example.data.model.AlbumArtCandidate
import com.example.data.model.DownloadProgress
import com.example.data.model.DownloadedTrack
import com.example.data.model.Playlist
import com.example.data.model.PlaylistSummary
import com.example.data.repository.PlaylistRepository
import com.example.data.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOption(val label: String) {
    DATE_DESC("Date Added (Newest)"),
    DATE_ASC("Date Added (Oldest)"),
    TITLE_AZ("Title (A-Z)"),
    ARTIST_AZ("Artist (A-Z)"),
    DURATION_DESC("Duration (Longest)"),
    SIZE_DESC("File Size (Largest)")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = TrackRepository(application, db.trackDao())
    val playlistRepository = PlaylistRepository(application, db.playlistDao(), db.trackDao())
    val playerManager = AudioPlayerManager(application)

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _downloadProgress = MutableStateFlow(DownloadProgress())
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0 = Library, 1 = Playlists, 2 = Educational Catalog
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Playlist management state
    val playlists: StateFlow<List<PlaylistSummary>> = playlistRepository.getPlaylistSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylistId.flatMapLatest { id ->
        if (id == null) flowOf(null) else playlistRepository.getPlaylistById(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPlaylistTracks: StateFlow<List<DownloadedTrack>> = _selectedPlaylistId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else playlistRepository.getTracksForPlaylist(id)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog: StateFlow<Boolean> = _showCreatePlaylistDialog.asStateFlow()

    private val _editingPlaylist = MutableStateFlow<Playlist?>(null)
    val editingPlaylist: StateFlow<Playlist?> = _editingPlaylist.asStateFlow()

    private val _showAddSongsToPlaylistDialog = MutableStateFlow(false)
    val showAddSongsToPlaylistDialog: StateFlow<Boolean> = _showAddSongsToPlaylistDialog.asStateFlow()

    private val _trackToAddToPlaylist = MutableStateFlow<DownloadedTrack?>(null)
    val trackToAddToPlaylist: StateFlow<DownloadedTrack?> = _trackToAddToPlaylist.asStateFlow()

    // Art updater dialog state
    private val _artUpdateTrack = MutableStateFlow<DownloadedTrack?>(null)
    val artUpdateTrack: StateFlow<DownloadedTrack?> = _artUpdateTrack.asStateFlow()

    private val _artCandidates = MutableStateFlow<List<AlbumArtCandidate>>(emptyList())
    val artCandidates: StateFlow<List<AlbumArtCandidate>> = _artCandidates.asStateFlow()

    private val _isSearchingArt = MutableStateFlow(false)
    val isSearchingArt: StateFlow<Boolean> = _isSearchingArt.asStateFlow()

    // Edit metadata dialog state
    private val _editTrack = MutableStateFlow<DownloadedTrack?>(null)
    val editTrack: StateFlow<DownloadedTrack?> = _editTrack.asStateFlow()

    // Download modal state
    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog: StateFlow<Boolean> = _showDownloadDialog.asStateFlow()

    // Full audio player modal state
    private val _showFullPlayer = MutableStateFlow(false)
    val showFullPlayer: StateFlow<Boolean> = _showFullPlayer.asStateFlow()

    // SnackBar message
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tracks: StateFlow<List<DownloadedTrack>> = combine(
        _searchQuery,
        _selectedCategory,
        _sortOption
    ) { query, category, sort ->
        Triple(query, category, sort)
    }.flatMapLatest { (query, category, sort) ->
        val baseFlow = if (query.isNotBlank()) {
            repository.searchTracks(query)
        } else {
            repository.getTracksByCategory(category)
        }
        combine(baseFlow) { trackArrays ->
            var list = trackArrays[0]
            if (category == "Favorites") {
                list = list.filter { it.isFavorite }
            } else if (category != "All" && query.isNotBlank()) {
                list = list.filter { it.categoryTag.equals(category, ignoreCase = true) }
            }
            when (sort) {
                SortOption.DATE_DESC -> list.sortedByDescending { it.dateAdded }
                SortOption.DATE_ASC -> list.sortedBy { it.dateAdded }
                SortOption.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
                SortOption.ARTIST_AZ -> list.sortedBy { it.artist.lowercase() }
                SortOption.DURATION_DESC -> list.sortedByDescending { it.durationMs }
                SortOption.SIZE_DESC -> list.sortedByDescending { it.fileSize }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun setShowDownloadDialog(show: Boolean) {
        _showDownloadDialog.value = show
    }

    fun setShowFullPlayer(show: Boolean) {
        _showFullPlayer.value = show
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun downloadTrack(
        url: String,
        title: String? = null,
        artist: String? = null,
        category: String = "Educational",
        autoAccurateArt: Boolean = true
    ) {
        if (url.isBlank()) {
            _userMessage.value = "Please provide a valid download URL"
            return
        }

        viewModelScope.launch {
            _downloadProgress.update {
                DownloadProgress(isDownloading = true, statusText = "Initializing download...")
            }
            val result = repository.downloadAndProcessMusic(
                url = url.trim(),
                suggestedTitle = title?.takeIf { it.isNotBlank() },
                suggestedArtist = artist?.takeIf { it.isNotBlank() },
                category = category,
                autoUpdateAccurateArt = autoAccurateArt,
                onProgress = { progress ->
                    _downloadProgress.value = progress
                }
            )

            result.onSuccess { savedTrack ->
                _userMessage.value = "Saved \"${savedTrack.title}\" to Music folder!"
                _downloadProgress.update { DownloadProgress(isDownloading = false) }
            }.onFailure { err ->
                _userMessage.value = "Download failed: ${err.message}"
                _downloadProgress.update {
                    DownloadProgress(isDownloading = false, error = err.message)
                }
            }
        }
    }

    fun startArtUpdate(track: DownloadedTrack) {
        _artUpdateTrack.value = track
        _artCandidates.value = emptyList()
        _isSearchingArt.value = true

        viewModelScope.launch {
            try {
                val candidates = repository.searchArtCandidates(track)
                _artCandidates.value = candidates
            } catch (e: Exception) {
                _userMessage.value = "Error searching artwork: ${e.message}"
            } finally {
                _isSearchingArt.value = false
            }
        }
    }

    fun dismissArtUpdate() {
        _artUpdateTrack.value = null
        _artCandidates.value = emptyList()
        _isSearchingArt.value = false
    }

    fun applyCandidateArtwork(candidate: AlbumArtCandidate) {
        val track = _artUpdateTrack.value ?: return
        viewModelScope.launch {
            val success = repository.applyCandidateArt(track.id, candidate)
            if (success) {
                _userMessage.value = "Updated album art (${candidate.matchScore}% match)!"
                dismissArtUpdate()
            } else {
                _userMessage.value = "Failed to download selected artwork."
            }
        }
    }

    fun restoreOriginalArt() {
        val track = _artUpdateTrack.value ?: return
        viewModelScope.launch {
            repository.restoreOriginalEmbeddedArt(track.id)
            _userMessage.value = "Restored original embedded album art."
            dismissArtUpdate()
        }
    }

    fun openEditMetadata(track: DownloadedTrack) {
        _editTrack.value = track
    }

    fun dismissEditMetadata() {
        _editTrack.value = null
    }

    fun saveEditedMetadata(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        category: String
    ) {
        val current = _editTrack.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                title = title.trim(),
                artist = artist.trim(),
                album = album.trim(),
                categoryTag = category
            )
            repository.updateTrack(updated)
            _userMessage.value = "Metadata updated successfully!"
            dismissEditMetadata()
        }
    }

    fun deleteTrack(track: DownloadedTrack) {
        viewModelScope.launch {
            if (playbackState.value.currentTrack?.id == track.id) {
                playerManager.stop()
            }
            repository.deleteTrack(track)
            _userMessage.value = "Removed \"${track.title}\" from library"
        }
    }

    fun toggleFavorite(track: DownloadedTrack) {
        viewModelScope.launch {
            repository.toggleFavorite(track.id)
        }
    }

    fun playTrack(track: DownloadedTrack) {
        playerManager.playTrack(track)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun skipForward() {
        playerManager.skipForward(10)
    }

    fun skipBackward() {
        playerManager.skipBackward(10)
    }

    fun toggleLoop() {
        playerManager.toggleLoop()
    }

    fun playNext() {
        if (!playerManager.playNext()) {
            _userMessage.value = "End of queue reached"
        }
    }

    fun playPrevious() {
        playerManager.playPrevious()
    }

    fun playPlaylist(playlist: Playlist, startIndex: Int = 0) {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                db.playlistDao().getTracksForPlaylistSync(playlist.id)
            }
            if (tracks.isEmpty()) {
                _userMessage.value = "Playlist is empty. Add songs to play!"
            } else {
                playerManager.playQueue(tracks, startIndex, playlist.name)
                _userMessage.value = "Playing \"${playlist.name}\""
            }
        }
    }

    fun shufflePlayPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                db.playlistDao().getTracksForPlaylistSync(playlist.id).shuffled()
            }
            if (tracks.isEmpty()) {
                _userMessage.value = "Playlist is empty. Add songs to play!"
            } else {
                playerManager.playQueue(tracks, 0, "${playlist.name} (Shuffle)")
                _userMessage.value = "Shuffle playing \"${playlist.name}\""
            }
        }
    }

    fun skipNext() {
        playNext()
    }

    fun skipPrevious() {
        playPrevious()
    }

    fun selectPlaylist(id: Long?) {
        _selectedPlaylistId.value = id
    }

    fun openCreatePlaylistDialog() {
        _editingPlaylist.value = null
        _showCreatePlaylistDialog.value = true
    }

    fun openEditPlaylistDialog(playlist: Playlist) {
        _editingPlaylist.value = playlist
        _showCreatePlaylistDialog.value = true
    }

    fun closeCreatePlaylistDialog() {
        _showCreatePlaylistDialog.value = false
        _editingPlaylist.value = null
    }

    fun setShowCreatePlaylistDialog(show: Boolean) {
        _showCreatePlaylistDialog.value = show
        if (!show) _editingPlaylist.value = null
    }

    fun setShowAddSongsToPlaylistDialog(show: Boolean) {
        _showAddSongsToPlaylistDialog.value = show
    }

    fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) {
            _userMessage.value = "Please enter a playlist name"
            return
        }
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(name, description)
            _userMessage.value = "Created playlist \"$name\""
            _showCreatePlaylistDialog.value = false
            _selectedPlaylistId.value = id
        }
    }

    fun updatePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.updatePlaylist(playlist)
            _userMessage.value = "Updated playlist \"${playlist.name}\""
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
            _userMessage.value = "Playlist deleted"
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: DownloadedTrack) {
        viewModelScope.launch {
            val added = playlistRepository.addTrackToPlaylist(playlistId, track.id)
            if (added) {
                _userMessage.value = "Added \"${track.title}\" to playlist"
            } else {
                _userMessage.value = "\"${track.title}\" is already in this playlist"
            }
            _trackToAddToPlaylist.value = null
        }
    }

    fun addTracksToCurrentPlaylist(trackIds: List<Long>) {
        val playlistId = _selectedPlaylistId.value ?: return
        viewModelScope.launch {
            playlistRepository.addTracksToPlaylist(playlistId, trackIds)
            _userMessage.value = "Added ${trackIds.size} song(s) to playlist"
            _showAddSongsToPlaylistDialog.value = false
        }
    }

    fun removeTrackFromCurrentPlaylist(trackId: Long) {
        val playlistId = _selectedPlaylistId.value ?: return
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
            _userMessage.value = "Song removed from playlist"
        }
    }

    fun moveTrackInCurrentPlaylist(fromIndex: Int, toIndex: Int) {
        val playlistId = _selectedPlaylistId.value ?: return
        viewModelScope.launch {
            playlistRepository.moveTrack(playlistId, fromIndex, toIndex)
        }
    }

    fun exportPlaylistM3u(playlist: Playlist) {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                db.playlistDao().getTracksForPlaylistSync(playlist.id)
            }
            val result = playlistRepository.exportPlaylistToM3u(playlist, tracks)
            result.onSuccess { path ->
                _userMessage.value = "Exported playlist to Music/Playlists folder"
            }.onFailure { err ->
                _userMessage.value = "Export failed: ${err.message}"
            }
        }
    }

    fun loadEducationalPresetPlaylists() {
        viewModelScope.launch {
            val currentTracks = tracks.value
            if (currentTracks.isEmpty()) {
                _userMessage.value = "Download educational music first from the Catalog tab!"
                return@launch
            }
            playlistRepository.createPresetPlaylistsIfEmpty(currentTracks)
            _userMessage.value = "Loaded educational study playlists"
        }
    }

    fun openAddToPlaylist(track: DownloadedTrack) {
        _trackToAddToPlaylist.value = track
    }

    fun dismissAddToPlaylist() {
        _trackToAddToPlaylist.value = null
    }

    fun clearDownloadProgress() {
        _downloadProgress.value = DownloadProgress()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
