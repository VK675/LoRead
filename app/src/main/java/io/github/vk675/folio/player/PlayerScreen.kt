package io.github.vk675.folio.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.vk675.folio.R
import io.github.vk675.folio.ui.components.AppBackground
import io.github.vk675.folio.ui.components.PanelIconButton
import io.github.vk675.folio.ui.components.Panel
import io.github.vk675.folio.ui.theme.FolioTheme

// Placeholder for Stage 1: file picker, play/pause, skip and seek bar go here.
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
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

        // Empty square where the book cover will be shown.
        Panel(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f),
            shape = RoundedCornerShape(36.dp)
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.player_nothing),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.player_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1.3f))
    }
}

@Preview
@Composable
private fun PlayerScreenPreview() {
    FolioTheme {
        AppBackground {
            PlayerScreen(onBack = {})
        }
    }
}
