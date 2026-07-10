package com.ethiopialibrary.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.dates.CalendarMode
import com.ethiopialibrary.app.ui.theme.LibraryAccents
import com.ethiopialibrary.app.ui.theme.LibraryStatus

/**
 * The calendar(s) dates are shown in, provided once at the navigation root from
 * the saved setting so every screen formats dates consistently. Changing the
 * setting updates this and all visible dates recompose.
 */
val LocalCalendarMode = staticCompositionLocalOf { CalendarMode.DUAL }

/**
 * Tactile press feedback for touch devices (no hover): the surface dips slightly
 * while a finger is down, then springs back. Drive it from the same
 * [interactionSource] the control passes to `clickable` so press and scale align.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Top bar with logical (RTL-safe) back arrow and an optional actions slot. */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A bold section heading used to separate content groups on every screen. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * Standard content card: generously rounded, soft shadow depth (no hard border),
 * and a single content padding. Pass [onClick] to make the whole card pressable.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 5.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elevation) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * Primary action: a tall, full-width pill with a solid primary fill, a soft
 * lifted shadow, and press-scale feedback. The 64dp height keeps it a comfortable
 * touch target on a shared tablet. On a mouse (or while pressed) the fill tints
 * to [LibraryStatus.hoverPrimary]; keyboard (Tab) focus draws a visible 2dp ring
 * in [MaterialTheme.colorScheme.onPrimary] around the pill (white, not
 * [LibraryStatus.focusRing] — that green would be invisible against this
 * button's own green fill).
 *
 * [shortcutHint] renders a small, low-emphasis badge (e.g. "Ctrl+O") near the
 * bottom-trailing corner; leave it null for no visual change.
 */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shortcutHint: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val contentColor = if (enabled) Color.White else LibraryStatus.disabledContent
    val fillColor = when {
        !enabled -> LibraryStatus.disabledContainer
        hovered || pressed -> LibraryStatus.hoverPrimary
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressScale(interaction)
            .shadow(
                elevation = if (enabled) 6.dp else 0.dp,
                shape = shape,
                spotColor = LibraryAccents.shadowGreen,
                ambientColor = LibraryAccents.shadowGreen,
            )
            .clip(shape)
            .background(fillColor)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.onPrimary, shape) else Modifier)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
        if (shortcutHint != null) {
            Text(
                shortcutHint,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 6.dp),
            )
        }
    }
}

/**
 * Secondary action: same tactile pill shape as [BigButton] but a clean white
 * surface with a hairline outline — green is reserved for the icon and label so
 * it reads as an accent, not a second primary button. On a mouse hover the
 * surface tints to [MaterialTheme.colorScheme.surfaceVariant] and the border
 * tints to [MaterialTheme.colorScheme.primary]; keyboard (Tab) focus draws a
 * visible 2dp ring in [LibraryStatus.focusRing], which reads clearly here
 * against this button's light fill (unlike [BigButton], which uses
 * [MaterialTheme.colorScheme.onPrimary] instead since its fill is green),
 * taking precedence over the hover border when both states are true.
 *
 * [shortcutHint] renders the same small bottom-trailing badge as [BigButton];
 * leave it null for no visual change.
 */
@Composable
fun BigOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shortcutHint: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    val accent = MaterialTheme.colorScheme.primary
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val fillColor = if (hovered) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val borderWidth = if (focused) 2.dp else 1.dp
    val borderColor = when {
        focused -> LibraryStatus.focusRing
        hovered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressScale(interaction)
            .shadow(2.dp, shape, spotColor = LibraryAccents.shadowSoft, ambientColor = LibraryAccents.shadowSoft)
            .clip(shape)
            .background(fillColor)
            .border(borderWidth, borderColor, shape)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = accent),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = accent,
            )
        }
        if (shortcutHint != null) {
            Text(
                shortcutHint,
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 6.dp),
            )
        }
    }
}

/**
 * Tappable 1–5 star row. Each star is a generous touch target sized for a shared
 * tablet; tapping star N reports N. Used at the return desk to rate a member.
 */
@Composable
fun StarRatingInput(onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { star ->
            val interaction = remember { MutableInteractionSource() }
            Icon(
                Icons.Filled.Star,
                contentDescription = star.toString(),
                tint = LibraryStatus.starGold,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(color = LibraryStatus.starGold),
                        onClick = { onRate(star) },
                    ),
            )
        }
    }
}

/** Read-only rating: one gold star plus the value (e.g. "4.2"). */
@Composable
fun StarRatingDisplay(rating: Double, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = LibraryStatus.starGold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            String.format(locale, "%.1f", rating),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
