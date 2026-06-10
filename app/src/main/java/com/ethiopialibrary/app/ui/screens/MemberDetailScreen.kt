package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.MemberEntity
import com.ethiopialibrary.app.data.MemberStatus
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.labels.LabelGenerator
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import kotlinx.coroutines.launch

@Composable
fun MemberDetailScreen(repo: LibraryRepository, memberId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val member by produceState<MemberEntity?>(null, memberId, refresh) {
        value = repo.memberById(memberId)
    }
    val loans by repo.activeLoansForMember(memberId).collectAsStateWithLifecycle(emptyList())
    val locale = LocalConfiguration.current.locales[0]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AppTopBar(member?.fullName.orEmpty(), onBack)
        member?.let { m ->
            Text(m.memberCode, style = MaterialTheme.typography.titleLarge)
            m.phone?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.member_card), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    val qr = remember(m.memberCode) {
                        LabelGenerator.qrBitmap(m.memberCode, 320).asImageBitmap()
                    }
                    Image(qr, contentDescription = m.memberCode, Modifier.size(180.dp))
                    Text(m.memberCode, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (m.status == MemberStatus.ACTIVE) {
                BigOutlinedButton(stringResource(R.string.suspend_member)) {
                    scope.launch {
                        repo.setMemberStatus(m.id, MemberStatus.SUSPENDED)
                        refresh++
                    }
                }
            } else {
                BigButton(stringResource(R.string.activate_member)) {
                    scope.launch {
                        repo.setMemberStatus(m.id, MemberStatus.ACTIVE)
                        refresh++
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.stat_active_loans), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        loans.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${item.bookTitle} — ${item.copyCode}", style = MaterialTheme.typography.titleMedium)
                    val overdue = item.loan.dueAt < System.currentTimeMillis()
                    Text(
                        "${stringResource(R.string.due_date)}: " +
                            DualCalendarFormatter.format(item.loan.dueAt, locale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overdue) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
