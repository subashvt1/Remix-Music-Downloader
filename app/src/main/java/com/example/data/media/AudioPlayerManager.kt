package com.example.data.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.example.data.model.DownloadedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val currentTrack: DownloadedTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLooping: Boolean = false,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
    val queue: List<DownloadedTrack> = emptyList(),
    val queueIndex: Int = -1,
    val playlistName: String? = null
) {
    val hasNext: Boolean get() = queue.isNotEmpty() && queueIndex < queue.size - 1
    val hasPrevious: Boolean get() = queue.isNotEmpty() && queueIndex > 0
}

class AudioPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun playQueue(tracks: List<DownloadedTrack>, startIndex: Int = 0, playlistName: String? = null) {
        if (tracks.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, tracks.lastIndex)
        _playbackState.update {
            it.copy(
                queue = tracks,
                queueIndex = validIndex,
                playlistName = playlistName
            )
        }
        playTrackInternal(tracks[validIndex])
    }

    fun playTrack(track: DownloadedTrack) {
        if (_playbackState.value.currentTrack?.id == track.id && mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == true) {
                pause()
            } else {
                resume()
            }
            return
        }

        // Single track play resets queue or keeps single item
        _playbackState.update {
            it.copy(
                queue = listOf(track),
                queueIndex = 0,
                playlistName = null
            )
        }
        playTrackInternal(track)
    }

    private fun playTrackInternal(track: DownloadedTrack) {
        stopCurrentPlayerOnly()

        try {
            _playbackState.update {
                it.copy(
                    currentTrack = track,
                    isPlaying = false,
                    isBuffering = true,
                    errorMessage = null
                )
            }

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                val uri = when {
                    !track.contentUri.isNullOrBlank() -> Uri.parse(track.contentUri)
                    track.filePath.isNotBlank() -> Uri.fromFile(File(track.filePath))
                    else -> null
                }

                if (uri != null) {
                    setDataSource(context, uri)
                } else {
                    throw IllegalArgumentException("No valid URI or path for track")
                }

                setOnPreparedListener { mp ->
                    mp.start()
                    _playbackState.update {
                        it.copy(
                            isPlaying = true,
                            isBuffering = false,
                            durationMs = mp.duration.toLong(),
                            currentPositionMs = 0L
                        )
                    }
                    startProgressTracker()
                }

                setOnCompletionListener {
                    _playbackState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                    stopProgressTracker()

                    // Auto advance in playlist queue
                    val currentState = _playbackState.value
                    if (currentState.hasNext) {
                        playNext()
                    } else if (currentState.isLooping && currentState.queue.isNotEmpty()) {
                        // Loop entire queue if looping enabled
                        playQueue(currentState.queue, 0, currentState.playlistName)
                    }
                }

                setOnErrorListener { _, what, extra ->
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = "Playback error ($what, $extra)"
                        )
                    }
                    true
                }

                prepareAsync()
            }
            mediaPlayer = player

        } catch (e: Exception) {
            e.printStackTrace()
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "Cannot play audio: ${e.message}"
                )
            }
        }
    }

    fun playNext(): Boolean {
        val s = _playbackState.value
        if (s.queue.isNotEmpty() && s.queueIndex < s.queue.lastIndex) {
            val nextIndex = s.queueIndex + 1
            val nextTrack = s.queue[nextIndex]
            _playbackState.update { it.copy(queueIndex = nextIndex) }
            playTrackInternal(nextTrack)
            return true
        }
        return false
    }

    fun playPrevious(): Boolean {
        val s = _playbackState.value
        // If > 3 seconds in, restart current track
        if (s.currentPositionMs > 3000L) {
            seekTo(0)
            return true
        }
        if (s.queue.isNotEmpty() && s.queueIndex > 0) {
            val prevIndex = s.queueIndex - 1
            val prevTrack = s.queue[prevIndex]
            _playbackState.update { it.copy(queueIndex = prevIndex) }
            playTrackInternal(prevTrack)
            return true
        } else {
            seekTo(0)
            return false
        }
    }

    private fun stopCurrentPlayerOnly() {
        stopProgressTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playbackState.update { s -> s.copy(isPlaying = false) }
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _playbackState.update { s -> s.copy(isPlaying = true) }
            startProgressTracker()
        }
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            it.seekTo(positionMs.toInt())
            _playbackState.update { s -> s.copy(currentPositionMs = positionMs) }
        }
    }

    fun skipForward(seconds: Int = 10) {
        mediaPlayer?.let {
            val newPos = (it.currentPosition + seconds * 1000).coerceAtMost(it.duration)
            seekTo(newPos.toLong())
        }
    }

    fun skipBackward(seconds: Int = 10) {
        mediaPlayer?.let {
            val newPos = (it.currentPosition - seconds * 1000).coerceAtLeast(0)
            seekTo(newPos.toLong())
        }
    }

    fun toggleLoop() {
        val nextLoop = !_playbackState.value.isLooping
        mediaPlayer?.isLooping = nextLoop
        _playbackState.update { it.copy(isLooping = nextLoop) }
    }

    fun stop() {
        stopProgressTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null
        _playbackState.update {
            it.copy(
                isPlaying = false,
                isBuffering = false,
                currentPositionMs = 0L
            )
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _playbackState.update {
                            it.copy(
                                currentPositionMs = mp.currentPosition.toLong(),
                                durationMs = mp.duration.toLong()
                            )
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stop()
    }
}
