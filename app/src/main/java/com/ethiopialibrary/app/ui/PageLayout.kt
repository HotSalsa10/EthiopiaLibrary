package com.ethiopialibrary.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centers content in a max-width column so it doesn't stretch full-bleed on
 * the wide tablet screen. [maxWidth] defaults to a comfortable reading/entry
 * width; pass [scrollable] = false when the caller already provides scrolling.
 */
@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 760.dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(20.dp),
            content = content,
        )
    }
}

/**
 * Two side-by-side columns for landscape two-pane screens. Callers decide
 * when to use this vs [PageColumn] based on available width; that
 * orientation-detection logic lives with each screen, not here.
 */
@Composable
fun TwoPaneRow(
    modifier: Modifier = Modifier,
    leftWeight: Float = 1f,
    rightWeight: Float = 1f,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier.fillMaxSize()) {
        Column(Modifier.weight(leftWeight), content = left)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(rightWeight), content = right)
    }
}
