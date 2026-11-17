package io.github.vk675.folio.player

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Everything the player screen needs to draw itself. Times are in milliseconds.
data class PlayerUiState(
    val title: String? = null,      // null = nothing loaded yet
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0
)

// Owns the ExoPlayer and turns its events into PlayerUiState.
// AndroidViewModel (not plain ViewModel) because ExoPlayer needs a Context;
// the Application context is safe to keep, an Activity one would leak.
//
// Stage 1 limit: the player lives as long as this ViewModel, so leaving the
// player screen stops playback. A MediaSessionService (next step) fixes that.
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val player: ExoPlayer = ExoPlayer.Builder(application)
        // Audio focus: pause when a call or another app starts playing, resume after.
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true
        )
        // Pause when headphones are unplugged instead of blasting the speaker.
        .setHandleAudioBecomingNoisy(true)
        // How far the skip buttons jump.
        .setSeekBackIncrementMs(SKIP_MS)
        .setSeekForwardIncrementMs(SKIP_MS)
        .build()

    // Same pattern as in the reader: a private mutable flow, a public read-only view.
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var ticker: Job? = null

    init {
        // ExoPlayer reports changes through a listener (like a C# event handler).
        // It does NOT report the position as it moves; the ticker below polls it.
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startTicker() else stopTicker()
                syncPosition()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Duration is unknown until the file is prepared (STATE_READY).
                syncPosition()
            }
        })
    }

    fun open(uri: Uri) {
        _state.update { PlayerUiState(title = displayName(uri)) }
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()   // starts reading the file in the background
        player.play()
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun rewind() { player.seekBack(); syncPosition() }
    fun forward() { player.seekForward(); syncPosition() }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        syncPosition()
    }

    // Twice a second while playing, copy the position into the state.
    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                syncPosition()
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun syncPosition() {
        _state.update {
            it.copy(
                positionMs = player.currentPosition,
                // C.TIME_UNSET is a big negative number meaning "not known yet".
                durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: 0
            )
        }
    }

    // The file name as the system file picker shows it, e.g. "Chapter 01.mp3".
    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
        }
        return uri.lastPathSegment ?: "Audio"
    }

    // Called when the screen is gone for good. ExoPlayer holds decoders and
    // audio output, so it must be released by hand (like IDisposable.Dispose in C#).
    override fun onCleared() {
        player.release()
    }

    private companion object {
        const val SKIP_MS = 30_000L
    }
}
