package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.AddSongsToPlaylistDialog
import com.example.ui.components.AlbumArtUpdaterDialog
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.DownloadDialog
import com.example.ui.components.EditMetadataDialog
import com.example.ui.components.FullPlayerDialog
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.EducationalCatalogScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val tracks by viewModel.tracks.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
                val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
                val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
                val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
                val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

                val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
                val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsStateWithLifecycle()
                val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsStateWithLifecycle()
                val editingPlaylist by viewModel.editingPlaylist.collectAsStateWithLifecycle()
                val trackToAddToPlaylist by viewModel.trackToAddToPlaylist.collectAsStateWithLifecycle()
                val showAddSongsToPlaylistDialog by viewModel.showAddSongsToPlaylistDialog.collectAsStateWithLifecycle()

                val artUpdateTrack by viewModel.artUpdateTrack.collectAsStateWithLifecycle()
                val artCandidates by viewModel.artCandidates.collectAsStateWithLifecycle()
                val isSearchingArt by viewModel.isSearchingArt.collectAsStateWithLifecycle()

                val editTrack by viewModel.editTrack.collectAsStateWithLifecycle()
                val showDownloadDialog by viewModel.showDownloadDialog.collectAsStateWithLifecycle()
                val showFullPlayer by viewModel.showFullPlayer.collectAsStateWithLifecycle()
                val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(userMessage) {
                    userMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearUserMessage()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (!(selectedTab == 1 && selectedPlaylist != null)) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Music Downloader",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Educational Music & Metadata Lab",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { viewModel.setShowDownloadDialog(true) },
                                        modifier = Modifier.testTag("top_bar_download_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download Music via URL",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    },
                    floatingActionButton = {
                        if (selectedTab == 0) {
                            FloatingActionButton(
                                onClick = { viewModel.setShowDownloadDialog(true) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(bottom = if (playbackState.currentTrack != null) 60.dp else 0.dp)
                                    .testTag("fab_download_music")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Download Music")
                            }
                        }
                    },
                    bottomBar = {
                        Column {
                            // Mini Player docked directly above bottom nav
                            MiniPlayer(
                                playbackState = playbackState,
                                onPlayPause = { viewModel.togglePlayPause() },
                                onSkipForward = { viewModel.skipForward() },
                                onNext = { viewModel.skipNext() },
                                onPrevious = { viewModel.skipPrevious() },
                                onClick = { viewModel.setShowFullPlayer(true) }
                            )

                            NavigationBar(
                                modifier = Modifier.navigationBarsPadding(),
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { viewModel.setSelectedTab(0) },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (tracks.isNotEmpty()) {
                                                    Badge { Text("${tracks.size}") }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selectedTab == 0) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic,
                                                contentDescription = "My Library"
                                            )
                                        }
                                    },
                                    label = { Text("Library") }
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { viewModel.setSelectedTab(1) },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (playlists.isNotEmpty()) {
                                                    Badge { Text("${playlists.size}") }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selectedTab == 1) Icons.Filled.QueueMusic else Icons.Outlined.QueueMusic,
                                                contentDescription = "Playlists"
                                            )
                                        }
                                    },
                                    label = { Text("Playlists") }
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { viewModel.setSelectedTab(2) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 2) Icons.Filled.School else Icons.Outlined.School,
                                            contentDescription = "Educational Catalog"
                                        )
                                    },
                                    label = { Text("Catalog") }
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> LibraryScreen(
                                tracks = tracks,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                sortOption = sortOption,
                                downloadProgress = downloadProgress,
                                playbackState = playbackState,
                                onSearchChange = { viewModel.setSearchQuery(it) },
                                onCategorySelect = { viewModel.setSelectedCategory(it) },
                                onSortSelect = { viewModel.setSortOption(it) },
                                onTrackPlay = { viewModel.playTrack(it) },
                                onTrackClick = {
                                    viewModel.playTrack(it)
                                    viewModel.setShowFullPlayer(true)
                                },
                                onArtUpdateClick = { viewModel.startArtUpdate(it) },
                                onEditClick = { viewModel.openEditMetadata(it) },
                                onDeleteClick = { viewModel.deleteTrack(it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onOpenDownloadDialog = { viewModel.setShowDownloadDialog(true) },
                                onOpenCatalogTab = { viewModel.setSelectedTab(2) },
                                onAddToPlaylist = { viewModel.openAddToPlaylist(it) },
                                onDismissDownloadProgress = { viewModel.clearDownloadProgress() }
                            )
                            1 -> {
                                val currentPlaylist = selectedPlaylist
                                if (currentPlaylist != null) {
                                    PlaylistDetailScreen(
                                        playlist = currentPlaylist,
                                        tracks = selectedPlaylistTracks,
                                        currentPlayingTrackId = playbackState.currentTrack?.id,
                                        isPlaying = playbackState.isPlaying,
                                        onBackClick = { viewModel.selectPlaylist(null) },
                                        onPlayTrackAt = { idx -> viewModel.playPlaylist(currentPlaylist, idx) },
                                        onPlayAll = { viewModel.playPlaylist(currentPlaylist, 0) },
                                        onShufflePlay = { viewModel.shufflePlayPlaylist(currentPlaylist) },
                                        onAddSongsClick = { viewModel.setShowAddSongsToPlaylistDialog(true) },
                                        onMoveTrack = { from, to -> viewModel.moveTrackInCurrentPlaylist(from, to) },
                                        onRemoveTrack = { trackId -> viewModel.removeTrackFromCurrentPlaylist(trackId) },
                                        onEditPlaylistClick = { viewModel.openEditPlaylistDialog(currentPlaylist) },
                                        onExportM3uClick = { viewModel.exportPlaylistM3u(currentPlaylist) },
                                        onDeletePlaylistClick = { viewModel.deletePlaylist(currentPlaylist.id) }
                                    )
                                } else {
                                    PlaylistsScreen(
                                        playlists = playlists,
                                        onPlaylistClick = { playlistId -> viewModel.selectPlaylist(playlistId) },
                                        onPlayPlaylist = { playlist -> viewModel.playPlaylist(playlist) },
                                        onCreatePlaylistClick = { viewModel.openCreatePlaylistDialog() },
                                        onEditPlaylistClick = { playlist -> viewModel.openEditPlaylistDialog(playlist) },
                                        onDeletePlaylistClick = { playlistId -> viewModel.deletePlaylist(playlistId) },
                                        onExportM3uClick = { playlist -> viewModel.exportPlaylistM3u(playlist) },
                                        onLoadStudyPresetsClick = { viewModel.loadEducationalPresetPlaylists() }
                                    )
                                }
                            }
                            2 -> EducationalCatalogScreen(
                                downloadedTracks = tracks,
                                onDownloadTrack = { catalogItem ->
                                    viewModel.downloadTrack(
                                        url = catalogItem.directDownloadUrl,
                                        title = catalogItem.title,
                                        artist = catalogItem.artist,
                                        category = catalogItem.category,
                                        autoAccurateArt = true
                                    )
                                    viewModel.setSelectedTab(0)
                                }
                            )
                        }
                    }
                }

                // Full player dialog
                if (showFullPlayer && playbackState.currentTrack != null) {
                    FullPlayerDialog(
                        playbackState = playbackState,
                        onDismiss = { viewModel.setShowFullPlayer(false) },
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeek = { viewModel.seekTo(it) },
                        onSkipForward = { viewModel.skipForward() },
                        onSkipBackward = { viewModel.skipBackward() },
                        onToggleLoop = { viewModel.toggleLoop() },
                        onUpdateArtClick = {
                            playbackState.currentTrack?.let {
                                viewModel.startArtUpdate(it)
                            }
                        },
                        onNext = { viewModel.skipNext() },
                        onPrevious = { viewModel.skipPrevious() }
                    )
                }

                // Create or Edit Playlist Dialog
                if (showCreatePlaylistDialog) {
                    CreatePlaylistDialog(
                        initialName = editingPlaylist?.name ?: "",
                        initialDescription = editingPlaylist?.description ?: "",
                        isEditing = editingPlaylist != null,
                        onDismiss = { viewModel.closeCreatePlaylistDialog() },
                        onConfirm = { name, desc ->
                            val current = editingPlaylist
                            if (current != null) {
                                viewModel.updatePlaylist(current.copy(name = name, description = desc))
                                viewModel.closeCreatePlaylistDialog()
                            } else {
                                viewModel.createPlaylist(name, desc)
                            }
                        }
                    )
                }

                // Add Single Track To Playlist Dialog
                trackToAddToPlaylist?.let { track ->
                    AddToPlaylistDialog(
                        track = track,
                        playlists = playlists,
                        onSelectPlaylist = { playlistId ->
                            viewModel.addTrackToPlaylist(playlistId, track)
                        },
                        onCreateNewPlaylistClick = {
                            viewModel.openCreatePlaylistDialog()
                        },
                        onDismiss = { viewModel.dismissAddToPlaylist() }
                    )
                }

                // Add Songs to Active Playlist Dialog
                if (showAddSongsToPlaylistDialog && selectedPlaylist != null) {
                    val currentPlaylist = selectedPlaylist!!
                    val existingTrackIds = selectedPlaylistTracks.map { it.id }.toSet()
                    AddSongsToPlaylistDialog(
                        allTracks = tracks,
                        alreadyInPlaylistTrackIds = existingTrackIds,
                        playlistName = currentPlaylist.name,
                        onDismiss = { viewModel.setShowAddSongsToPlaylistDialog(false) },
                        onAddSelected = { trackIds ->
                            viewModel.addTracksToCurrentPlaylist(trackIds)
                        }
                    )
                }

                // Album Art Matcher Dialog
                artUpdateTrack?.let { track ->
                    AlbumArtUpdaterDialog(
                        track = track,
                        candidates = artCandidates,
                        isLoading = isSearchingArt,
                        onDismiss = { viewModel.dismissArtUpdate() },
                        onSelectCandidate = { candidate ->
                            viewModel.applyCandidateArtwork(candidate)
                        },
                        onRestoreOriginal = {
                            viewModel.restoreOriginalArt()
                        }
                    )
                }

                // Edit Metadata Dialog
                editTrack?.let { track ->
                    EditMetadataDialog(
                        track = track,
                        onDismiss = { viewModel.dismissEditMetadata() },
                        onSave = { trackId, title, artist, album, category ->
                            viewModel.saveEditedMetadata(trackId, title, artist, album, category)
                        }
                    )
                }

                // Download Dialog
                if (showDownloadDialog) {
                    DownloadDialog(
                        onDismiss = { viewModel.setShowDownloadDialog(false) },
                        onDownload = { url, title, artist, category, autoAccurateArt ->
                            viewModel.downloadTrack(url, title, artist, category, autoAccurateArt)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Android") }
}
