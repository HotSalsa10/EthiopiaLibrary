package com.ethiopialibrary.app.ui.screens

import com.ethiopialibrary.app.ui.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure due-date -> [LoanStatus] classification (see [classify]). */
class CurrentlyOutScreenTest {

    private val now = 1_700_000_000_000L // arbitrary fixed instant
    private val millisPerDay = 86_400_000L

    @Test
    fun `overdue when due date is before now`() {
        assertEquals(LoanStatus.OVERDUE, classify(dueAt = now - 1, now = now, dueSoonDays = 3))
    }

    @Test
    fun `due soon when comfortably inside the configured window`() {
        val dueAt = now + 2 * millisPerDay
        assertEquals(LoanStatus.DUE_SOON, classify(dueAt = dueAt, now = now, dueSoonDays = 3))
    }

    @Test
    fun `on loan when comfortably beyond the due-soon window`() {
        val dueAt = now + 10 * millisPerDay
        assertEquals(LoanStatus.ON_LOAN, classify(dueAt = dueAt, now = now, dueSoonDays = 3))
    }

    @Test
    fun `on loan when due-soon window is not configured yet`() {
        // Even a due date one day out must not read as due-soon while the
        // setting hasn't loaded - classify() must not guess a default window.
        val dueAt = now + 1 * millisPerDay
        assertEquals(LoanStatus.ON_LOAN, classify(dueAt = dueAt, now = now, dueSoonDays = null))
    }

    @Test
    fun `due date exactly at the due-soon boundary counts as due soon`() {
        val dueAt = now + 3 * millisPerDay
        assertEquals(LoanStatus.DUE_SOON, classify(dueAt = dueAt, now = now, dueSoonDays = 3))
    }
}
