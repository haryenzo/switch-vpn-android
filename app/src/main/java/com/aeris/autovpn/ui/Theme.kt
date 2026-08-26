package com.aeris.autovpn.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.aeris.autovpn.R

// Palette lifted directly from the user-supplied "AutoVPN — Claude style" HTML/CSS mockup.
val MockBg = Color(0xFFF7F5F0)
val MockSurface = Color(0xFFFFFDFA)
val MockSurface2 = Color(0xFFF1EEE8)
val MockInk = Color(0xFF292725)
val MockMuted = Color(0xFF78736D)
val MockLine = Color(0xFFDED9D0)
val MockAccent = Color(0xFFC96F52)
val MockAccent2 = Color(0xFFA9553D)
val MockAccentSoft = Color(0xFFF3DDD5)
val MockGreen = Color(0xFF5F7F68)
val MockGreenSoft = Color(0xFFE6EEE8)

private val LightColors = lightColorScheme(
    primary = MockAccent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = MockAccentSoft,
    onPrimaryContainer = MockAccent2,
    secondary = MockGreen,
    secondaryContainer = MockGreenSoft,
    onSecondaryContainer = MockGreen,
    background = MockBg,
    onBackground = MockInk,
    surface = MockSurface,
    onSurface = MockInk,
    surfaceVariant = MockSurface2,
    onSurfaceVariant = MockMuted,
    outline = MockLine,
    outlineVariant = MockLine,
    error = Color(0xFFAE4023),
)

// Dark variant of the same warm palette — same accent hue, brightened for contrast on a dark
// background, rather than the old unrelated teal/navy scheme this app started with.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0895F),
    onPrimary = Color(0xFF3A1B0E),
    primaryContainer = Color(0xFF4A2C1E),
    onPrimaryContainer = Color(0xFFF3DDD5),
    secondary = Color(0xFF8FB39C),
    secondaryContainer = Color(0xFF21362A),
    onSecondaryContainer = Color(0xFF8FB39C),
    background = Color(0xFF1C1917),
    onBackground = Color(0xFFF2EDE7),
    surface = Color(0xFF262220),
    onSurface = Color(0xFFF2EDE7),
    surfaceVariant = Color(0xFF322D2A),
    onSurfaceVariant = Color(0xFFA79E93),
    outline = Color(0xFF433D38),
    outlineVariant = Color(0xFF433D38),
    error = Color(0xFFFF8A65),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jakartaFamily() = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

// Mockup uses a single sans family throughout — big page titles are just heavy-weight,
// tight-tracking Jakarta Sans, not a separate serif face.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jakartaHeavy() = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(760))),
)

private fun buildTypography(): Typography {
    val family = jakartaFamily()
    val heading = jakartaHeavy()
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = heading, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.9).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = heading, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
        titleLarge = base.titleLarge.copy(fontFamily = heading, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        bodyMedium = base.bodyMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        bodySmall = base.bodySmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    )
}

// Radius copied straight from the mockup's --radius:22px, used for cards/sheets; smaller
// components (chips, buttons) step down from there.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AutoVpnTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = buildTypography(),
        shapes = AppShapes,
        content = content,
    )
}
