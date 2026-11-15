package io.github.vk675.folio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3 defaults with thin, large titles: big size, light weight.
val Typography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 24.sp,
        lineHeight = 30.sp
    )
)
