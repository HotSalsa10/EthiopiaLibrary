package com.ethiopialibrary.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LabelCount
import com.ethiopialibrary.app.data.buildStatisticsCsv
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.PageColumn
import com.ethiopialibrary.app.ui.StatisticsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun StatisticsScreen(vm: StatisticsViewModel, onBack: () -> Unit) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    PageColumn {
        AppTopBar(stringResource(R.string.nav_statistics), onBack)

        val s = stats
        if (s == null) {
            Text(
                stringResource(R.string.no_data),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@PageColumn
        }

        SectionCard(stringResource(R.string.nav_statistics)) {
            StatRow(stringResource(R.string.stat_titles), s.totalTitles)
            StatRow(stringResource(R.string.stat_copies), s.totalCopies)
            StatRow(stringResource(R.string.stat_total_members), s.totalMembers)
            StatRow(stringResource(R.string.stat_active_loans), s.activeLoans)
            StatRow(stringResource(R.string.stat_overdue), s.overdue)
        }
        Spacer(Modifier.height(12.dp))

        SectionCard(stringResource(R.string.activity_title)) {
            StatRow(
                stringResource(R.string.stat_checkouts),
                stringResource(R.string.prev_in_brackets, s.checkoutsLast30, s.checkoutsPrev30),
            )
            StatRow(stringResource(R.string.stat_returns), s.returnsLast30)
            StatRow(stringResource(R.string.stat_new_members), s.newMembersLast30)
        }
        Spacer(Modifier.height(12.dp))

        ListCard(stringResource(R.string.most_borrowed), s.topBooks)
        Spacer(Modifier.height(12.dp))
        ListCard(stringResource(R.string.most_active), s.topMembers)
        Spacer(Modifier.height(12.dp))
        ListCard(stringResource(R.string.by_category), s.byCategory)
        Spacer(Modifier.height(12.dp))
        ListCard(stringResource(R.string.by_language), s.byLanguage)
        Spacer(Modifier.height(12.dp))
        ListCard(stringResource(R.string.monthly_loans), s.monthlyLoans)
        Spacer(Modifier.height(20.dp))

        BigButton(stringResource(R.string.export_csv), icon = Icons.Filled.Download) {
            scope.launch {
                shareCsv(context, buildStatisticsCsv(s))
                Toast.makeText(context, R.string.labels_exported, Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ListCard(title: String, rows: List<LabelCount>) {
    SectionCard(title) {
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val maxCount = rows.maxOfOrNull { it.count } ?: 1
            rows.forEach { BarRow(it, maxCount) }
        }
    }
}

/** One ranked row: label + count, with a proportional bar underneath showing
 * its share of the list's largest count. Dependency-free (plain Box fills). */
@Composable
private fun BarRow(row: LabelCount, maxCount: Int) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.label, style = MaterialTheme.typography.bodyLarge)
            Text(row.count.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = (row.count.toFloat() / maxCount).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) = StatRow(label, value.toString())

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 140.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

private suspend fun shareCsv(context: Context, csv: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "reports")
        dir.mkdirs()
        val out = File(dir, "library-statistics.csv")
        out.writeText(csv)
        out
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
