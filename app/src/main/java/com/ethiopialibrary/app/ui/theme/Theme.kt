package com.ethiopialibrary.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * General loan/copy status vocabulary used across screens (status badges, edge
 * cards, etc.) — kept separate from [LibraryAccents], which is stat-tile-specific.
 * Every value here reuses an existing hex from elsewhere in the theme except
 * [dueSoon]/[dueSoonContainer], promoted from CheckoutScreen's former local
 * `OnWarningContainer`/`WarningContainer` vals — the one genuinely new hue.
 */
object LibraryStatus {
    val available = Color(0xFF2E6B43)
    val availableContainer = Color(0xFFDDEBE0)
    val onLoan = Color(0xFF8C6D3F)
    val onLoanContainer = Color(0xFFEDE3D0)
    val dueSoon = Color(0xFF7A5B00)
    val dueSoonContainer = Color(0xFFFFF3CD)
    val overdue = Color(0xFFC62828)
    val overdueContainer = Color(0xFFFBE3DF)
    val focusRing = Color(0xFF2E5B3E)
    val starGold = Color(0xFFF5A623)
    val disabledContent = Color(0xFF8C8576)
    val disabledContainer = Color(0xFFD8D1C3)
    val hoverPrimary = Color(0xFF234A30)
}

/** Corner radii shared by every card/sheet-like surface; pills stay a literal shape. */
val LibraryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
        // Dashboard "stat number" style: bigger and bolder than the M3 default
        // so key counts read at a glance from across the desk.
        displaySmall = base.displaySmall.copy(
            fontFamily = fontFamily,
            fontSize = 40.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Bold,
        ),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        // Standard screen-title style, replacing the old headlineSmall + manual
        // SemiBold override that AppTopBar used to apply itself.
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        // "Row title" style used across list rows and card headings.
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        // Bumped up from the 16sp M3 default for easier reading on a tablet at
        // arm's length; lineHeight keeps the same +2sp step as the size bump.
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, fontSize = 18.sp, lineHeight = 26.sp),
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
        shapes = LibraryShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
