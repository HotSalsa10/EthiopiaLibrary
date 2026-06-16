package com.ethiopialibrary.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ethiopialibrary.app.R

// Palette sampled from the library logo: forest green, gold, warm parchment.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5B3E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE3D2),
    onPrimaryContainer = Color(0xFF0C2C18),
    secondary = Color(0xFFB8902F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E7CC),
    onSecondaryContainer = Color(0xFF4A3A12),
    tertiary = Color(0xFF8C6D3F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEDE6D8),
    onSurfaceVariant = Color(0xFF5B5648),
    background = Color(0xFFF4EEE2),
    onBackground = Color(0xFF1F1B16),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFBE3DF),
    onErrorContainer = Color(0xFF8C1D18),
    outline = Color(0xFFBCAE97),
    outlineVariant = Color(0xFFDED4C0),
)

/** Stat-tile accents, harmonized with the logo (green / gold / bronze; red kept for overdue). */
object LibraryAccents {
    val books = Color(0xFF2E6B43)
    val booksBg = Color(0xFFDDEBE0)
    val members = Color(0xFFB8902F)
    val membersBg = Color(0xFFF4E9CF)
    val loans = Color(0xFF8C6D3F)
    val loansBg = Color(0xFFEDE3D0)
    val overdue = Color(0xFFC62828)
    val overdueBg = Color(0xFFFBE3DF)

    // Tints for soft, "lifted" shadow depth (API 28+ honours these on Modifier.shadow).
    val shadowGreen = Color(0xFF1F4A30)
    val shadowSoft = Color(0x14000000)
    val shadowCard = Color(0x1A000000)
}

/**
 * Gradient fills that give surfaces a tactile, lit-from-above quality instead of
 * flat blocks of colour. Defined once so every premium control stays consistent.
 */
object LibraryBrushes {
    // Rich forest-green for primary actions: lighter at the top-start, deepening
    // toward the bottom-end so the pill reads as a real, pressable surface.
    val primaryButton = Brush.linearGradient(
        colors = listOf(Color(0xFF3C7C52), Color(0xFF234A30)),
    )
}

/**
 * Bundled Noto fonts guarantee identical Ethiopic/Arabic rendering even on
 * cheap tablets that ship with stripped system font sets. The family follows
 * the active app language; missing glyphs fall back to system fonts.
 */
@Composable
fun EthiopiaLibraryTheme(content: @Composable () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val fontFamily = when (locale.language) {
        "am" -> FontFamily(Font(R.font.noto_sans_ethiopic))
        "ar" -> FontFamily(Font(R.font.noto_naskh_arabic))
        else -> FontFamily.Default
    }
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
    // Single locked light theme: paint the background so the device's dark
    // mode never bleeds through, and content colors stay consistent on every
    // tablet (a shared kiosk should look identical everywhere).
    MaterialTheme(
        colorScheme = LightColors,
        typography = typography,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
