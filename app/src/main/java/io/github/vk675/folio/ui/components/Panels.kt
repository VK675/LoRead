package io.github.vk675.folio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.vk675.folio.ui.theme.BackgroundDark
import io.github.vk675.folio.ui.theme.ButtonDark
import io.github.vk675.folio.ui.theme.ButtonLight
import io.github.vk675.folio.ui.theme.OnButtonLight
import io.github.vk675.folio.ui.theme.PanelBottom
import io.github.vk675.folio.ui.theme.PanelTop
import io.github.vk675.folio.ui.theme.RimBottom
import io.github.vk675.folio.ui.theme.RimTop

// Brushes are created once and reused by every card.
private val PanelFill = Brush.verticalGradient(listOf(PanelTop, PanelBottom))
private val PanelRim = Brush.verticalGradient(listOf(RimTop, RimBottom))

// The near-black backdrop shared by every screen.
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Text and icons take their default color from LocalContentColor.
    // Normally a Material Surface sets it; we draw our own background, so we set it here.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BackgroundDark),
            content = content
        )
    }
}

// A charcoal card with large rounded corners, a soft top-to-bottom gradient
// and a faint highlight along the top edge.
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(PanelFill)
            .border(1.dp, PanelRim, shape),
        content = content
    )
}

// Pill-shaped button.
// primary = true: white with dark text, for the one main action on a screen.
// primary = false: grey, for everything else.
@Composable
fun PanelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    Box(
        // clip comes before clickable so the press ripple stays inside the pill
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .background(if (primary) ButtonLight else ButtonDark)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) OnButtonLight else LocalContentColor.current,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)
        )
    }
}

// Round icon button in the same style as a card, 48dp so it is easy to hit with a thumb.
@Composable
fun PanelIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .background(PanelFill)
            .border(1.dp, PanelRim, CircleShape)
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp)
        )
    }
}
