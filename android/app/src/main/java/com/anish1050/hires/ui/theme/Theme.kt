package com.anish1050.hires.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0B0B0D)
val CardTop = Color(0xFF1B1B1E)
val Card = Color(0xFF161618)
val CardBorder = Color(0xFF232326)
val TextPrimary = Color(0xFFF4F4F5)
val TextMuted = Color(0xFF8E8E93)
val Lavender = Color(0xFFAAB4FF)
val Sand = Color(0xFFE8C98A)
val Mint = Color(0xFFA8D8C4)
val Graphite = Color(0xFF3A3A3E)
val AiGradient = Brush.linearGradient(listOf(Color(0xFF3B2A2E), Color(0xFF6E4943), Color(0xFFC9897B)))
val CardGradient = Brush.verticalGradient(listOf(CardTop, Card))

// ponytail: system sans-serif; switch to Manrope through downloadable fonts
// once the font provider certificates are added from the official sample.
private val typography = Typography(
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 11.sp, color = TextMuted),
)

@Composable
fun HiResTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink, surface = Card, surfaceVariant = Graphite, primary = Lavender, onPrimary = Ink,
            onBackground = TextPrimary, onSurface = TextPrimary, onSurfaceVariant = TextMuted, secondary = Mint, tertiary = Sand,
        ),
        typography = typography,
        content = content,
    )
}
