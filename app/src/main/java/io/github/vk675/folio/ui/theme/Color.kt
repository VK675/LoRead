package io.github.vk675.folio.ui.theme

import androidx.compose.ui.graphics.Color

// Monochrome palette: near-black screen, charcoal cards, white for the main action.

val BackgroundDark = Color(0xFF0A0A0B)

// Cards are a vertical gradient, slightly lighter at the top,
// which makes them look softly lit from above.
val PanelTop = Color(0xFF202023)
val PanelBottom = Color(0xFF141416)

// 1dp rim around cards: a faint highlight on the top edge that fades out.
// The first two hex digits are the alpha.
val RimTop = Color(0x1FFFFFFF)      // 12%
val RimBottom = Color(0x08FFFFFF)   // 3%

// Secondary (grey) and primary (white) pill buttons
val ButtonDark = Color(0xFF2A2A2E)
val ButtonLight = Color(0xFFF4F4F5)
val OnButtonLight = Color(0xFF0A0A0B)

val TextPrimary = Color(0xFFF4F4F5)
val TextSecondary = Color(0x99F4F4F5)   // same white at 60% alpha
