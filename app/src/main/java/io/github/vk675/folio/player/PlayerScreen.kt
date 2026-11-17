package io.github.vk675.folio.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.vk675.folio.R
import io.github.vk675.folio.ui.components.AppBackground
import io.github.vk675.folio.ui.components.Panel
import io.github.vk675.folio.ui.components.PanelButton
import io.github.vk675.folio.ui.components.PanelIconButton
import io.github.vk675.folio.ui.theme.ButtonDark
import io.github.vk675.folio.ui.theme.ButtonLight
import io.github.vk675.folio.ui.theme.FolioTheme
import io.github.vk675.folio.ui.theme.OnButtonLight

// Stateful wrapper: gets the ViewModel and the file picker, then hands plain
// values and callbacks to PlayerContent (which is what the preview draws).
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // System file picker (SAF). The MIME filter shows audio files only.
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.open(uri) }

    PlayerContent(
        state = state,
        onBack = onBack,
        onPick = { pickAudio.launch(arrayOf("audio/*")) },
        onTogglePlay = viewModel::togglePlay,
        onRewind = viewModel::rewind,
        onForward = viewModel::forward,
        onSeek = viewModel::seekTo,
        modifier = modifier
    )
}

@Composable
private fun PlayerContent(
    state: PlayerUiState,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onTogglePlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        // Top row: back button on the left, title centered over the full width.
        Box(modifier = Modifier.fillMaxWidth()) {
            PanelIconButton(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = stringResource(R.string.player_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(Modifier.weight(1f))

        // Cover square. Real covers come with the audio library (Stage 2).
        Panel(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f),
            shape = RoundedCornerShape(36.dp)
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = state.title ?: stringResource(R.string.player_nothing),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (state.title == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.player_pick_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            PanelButton(
                text = stringResource(R.string.player_pick),
                onClick = onPick,
                primary = true
            )
        } else {
            Spacer(Modifier.height(20.dp))
            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = onSeek
            )
            Spacer(Modifier.height(16.dp))
            Controls(
                isPlaying = state.isPlaying,
                onTogglePlay = onTogglePlay,
                onRewind = onRewind,
                onForward = onForward
            )
            Spacer(Modifier.height(20.dp))
            PanelButton(text = stringResource(R.string.player_pick), onClick = onPick)
        }

        Spacer(Modifier.weight(1.3f))
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit
) {
    // While the finger is on the slider, show where it is, not where the player is.
    // Otherwise the ticker would yank the thumb back every half second.
    // null = not dragging.
    var dragMs by remember { mutableStateOf<Float?>(null) }
    val max = durationMs.coerceAtLeast(1).toFloat()
    val shown = dragMs ?: positionMs.toFloat()

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown.coerceIn(0f, max),
            valueRange = 0f..max,
            onValueChange = { dragMs = it },
            onValueChangeFinished = {
                dragMs?.let { onSeek(it.toLong()) }
                dragMs = null
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = ButtonLight,
                activeTrackColor = ButtonLight,
                inactiveTrackColor = ButtonDark
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val timeStyle = MaterialTheme.typography.labelMedium
            val timeColor = MaterialTheme.colorScheme.onSurfaceVariant
            Text(formatTime(shown.toLong()), style = timeStyle, color = timeColor)
            Text(formatTime(durationMs), style = timeStyle, color = timeColor)
        }
    }
}

@Composable
private fun Controls(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        PanelIconButton(
            painter = painterResource(R.drawable.ic_rewind),
            contentDescription = stringResource(R.string.player_rewind),
            onClick = onRewind
        )
        // The one primary action: a big white circle, like the white pill buttons.
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onTogglePlay)
                .background(ButtonLight)
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(
                    if (isPlaying) R.string.player_pause else R.string.player_play
                ),
                tint = OnButtonLight,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
            )
        }
        PanelIconButton(
            painter = painterResource(R.drawable.ic_forward),
            contentDescription = stringResource(R.string.player_forward),
            onClick = onForward
        )
    }
}

// 75_000 -> "1:15", 3_725_000 -> "1:02:05". Audiobooks often run past an hour.
private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Preview
@Composable
private fun PlayerEmptyPreview() {
    FolioTheme {
        AppBackground {
            PlayerContent(PlayerUiState(), {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview
@Composable
private fun PlayerPlayingPreview() {
    FolioTheme {
        AppBackground {
            PlayerContent(
                PlayerUiState(
                    title = "Shadow Slave — Chapter 1",
                    isPlaying = true,
                    positionMs = 754_000,
                    durationMs = 2_400_000
                ),
                {}, {}, {}, {}, {}, {}
            )
        }
    }
}
