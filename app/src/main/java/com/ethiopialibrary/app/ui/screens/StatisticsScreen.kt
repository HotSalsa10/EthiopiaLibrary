package com.ethiopialibrary.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LabelCount
import com.ethiopialibrary.app.data.buildStatisticsCsv
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AppTopBar(stringResource(R.string.nav_statistics), onBack)

        val s = stats
        if (s == null) {
            Text(
                stringResource(R.string.no_data),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
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
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
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
            rows.forEach { StatRow(it.label, it.count) }
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
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
