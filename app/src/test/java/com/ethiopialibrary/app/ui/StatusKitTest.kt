package com.ethiopialibrary.app.ui

import com.ethiopialibrary.app.ui.theme.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure status -> color / container-color lookups (see [LoanStatus.statusColor]). */
class StatusKitTest {

    @Test
    fun `each status maps to its own theme color`() {
        assertEquals(LibraryStatus.available, LoanStatus.AVAILABLE.statusColor())
        assertEquals(LibraryStatus.onLoan, LoanStatus.ON_LOAN.statusColor())
        assertEquals(LibraryStatus.dueSoon, LoanStatus.DUE_SOON.statusColor())
        assertEquals(LibraryStatus.overdue, LoanStatus.OVERDUE.statusColor())
    }

    @Test
    fun `each status maps to its own theme container color`() {
        assertEquals(LibraryStatus.availableContainer, LoanStatus.AVAILABLE.statusContainerColor())
        assertEquals(LibraryStatus.onLoanContainer, LoanStatus.ON_LOAN.statusContainerColor())
        assertEquals(LibraryStatus.dueSoonContainer, LoanStatus.DUE_SOON.statusContainerColor())
        assertEquals(LibraryStatus.overdueContainer, LoanStatus.OVERDUE.statusContainerColor())
    }
}
