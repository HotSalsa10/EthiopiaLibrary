package com.ethiopialibrary.app.data

/** A labelled count row, used by grouped statistics queries (Room maps by column name). */
data class LabelCount(val label: String, val count: Int)

/** Snapshot of library activity for the statistics screen and CSV export. */
data class LibraryStatistics(
    val totalTitles: Int,
    val totalCopies: Int,
    val totalMembers: Int,
    val activeLoans: Int,
    val overdue: Int,
    val checkoutsLast30: Int,
    val checkoutsPrev30: Int,
    val returnsLast30: Int,
    val newMembersLast30: Int,
    val topBooks: List<LabelCount>,
    val topMembers: List<LabelCount>,
    val byCategory: List<LabelCount>,
    val byLanguage: List<LabelCount>,
    val monthlyLoans: List<LabelCount>,
)

/** Renders statistics as CSV for export/sharing. */
fun buildStatisticsCsv(s: LibraryStatistics): String = buildString {
    appendLine("Metric,Value")
    appendLine("Total titles,${s.totalTitles}")
    appendLine("Total copies,${s.totalCopies}")
    appendLine("Total members,${s.totalMembers}")
    appendLine("Active loans,${s.activeLoans}")
    appendLine("Overdue,${s.overdue}")
    appendLine("Checkouts last 30 days,${s.checkoutsLast30}")
    appendLine("Checkouts previous 30 days,${s.checkoutsPrev30}")
    appendLine("Returns last 30 days,${s.returnsLast30}")
    appendLine("New members last 30 days,${s.newMembersLast30}")
    appendLine()
    appendLine("Most borrowed books,Loans")
    s.topBooks.forEach { appendLine("${csvCell(it.label)},${it.count}") }
    appendLine()
    appendLine("Most active members,Loans")
    s.topMembers.forEach { appendLine("${csvCell(it.label)},${it.count}") }
    appendLine()
    appendLine("Books by category,Count")
    s.byCategory.forEach { appendLine("${csvCell(it.label)},${it.count}") }
    appendLine()
    appendLine("Books by language,Count")
    s.byLanguage.forEach { appendLine("${csvCell(it.label)},${it.count}") }
    appendLine()
    appendLine("Loans by month,Count")
    s.monthlyLoans.forEach { appendLine("${csvCell(it.label)},${it.count}") }
}

private fun csvCell(v: String): String =
    if (v.contains(',') || v.contains('"') || v.contains('\n')) {
        "\"" + v.replace("\"", "\"\"") + "\""
    } else {
        v
    }
