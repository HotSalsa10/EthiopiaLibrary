package com.ethiopialibrary.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontal step/progress indicator: one track segment per step, the segment
 * at [current] (0-based) highlighted in the primary color, the rest muted.
 * Relies on [Row]'s automatic layout-direction mirroring for RTL — segment
 * order is never hardcoded left-to-right.
 */
@Composable
fun StepHeader(current: Int, total: Int, labels: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(total) { index ->
            val active = index == current
            val trackColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            val labelColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(modifier = Modifier.weight(1f)) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 4.dp,
                    color = trackColor,
                )
                Spacer(Modifier.height(6.dp))
                labels.getOrNull(index)?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor,
                    )
                }
            }
        }
    }
}
