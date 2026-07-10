package com.ethiopialibrary.app.ui.screens

import androidx.compose.ui.graphics.Color
import com.ethiopialibrary.app.data.ActivityType
import com.ethiopialibrary.app.ui.theme.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure ActivityType -> color lookup (see [activityTypeColor]). */
class DashboardScreenTest {

    // Stand-ins for the two theme colors the real call site reads from
    // MaterialTheme; arbitrary but distinct from every LibraryStatus constant
    // so a wrong branch can't accidentally match by coincidence.
    private val secondary = Color(0xFF112233)
    private val onSurfaceVariant = Color(0xFF445566)

    @Test
    fun `each activity type maps to its own presentation color`() {
        assertEquals(
            LibraryStatus.onLoan,
            activityTypeColor(ActivityType.CHECKOUT.name, secondary, onSurfaceVariant),
        )
        assertEquals(
            LibraryStatus.available,
            activityTypeColor(ActivityType.RETURN.name, secondary, onSurfaceVariant),
        )
        assertEquals(
            secondary,
            activityTypeColor(ActivityType.RENEW.name, secondary, onSurfaceVariant),
        )
        assertEquals(
            onSurfaceVariant,
            activityTypeColor(ActivityType.UNDO.name, secondary, onSurfaceVariant),
        )
    }

    @Test
    fun `unknown activity type falls back to the neutral color`() {
        assertEquals(onSurfaceVariant, activityTypeColor("unknown", secondary, onSurfaceVariant))
    }
}
