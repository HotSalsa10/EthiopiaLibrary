package com.ethiopialibrary.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ethiopialibrary.app.R

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9E4BB),
    onPrimaryContainer = Color(0xFF0A3D0C),
    secondary = Color(0xFF795548),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7D6CF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF8F6F2),
    surface = Color(0xFFFFFFFF),
)

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
    MaterialTheme(
        colorScheme = LightColors,
        typography = typography,
        content = content,
    )
}
