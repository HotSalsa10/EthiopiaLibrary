package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.MemberEntity
import com.ethiopialibrary.app.data.MemberStatus
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.labels.LabelGenerator
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun MemberDetailScreen(repo: LibraryRepository, memberId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val member by produceState<MemberEntity?>(null, memberId, refresh) {
        value = repo.memberById(memberId)
    }
    val loans by repo.activeLoansForMember(memberId).collectAsStateWithLifecycle(emptyList())
    val history by repo.memberHistory(memberId).collectAsStateWithLifecycle(emptyList())
    val locale = LocalConfiguration.current.locales[0]

    var showEdit by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        AppTopBar(member?.fullName.orEmpty(), onBack) {
            IconButton(onClick = { showEdit = true }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_member))
            }
        }
        member?.let { m ->
            Text(m.memberCode, style = MaterialTheme.typography.titleLarge)
            m.phone?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.height(12.dp))

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.member_card), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    val qr = remember(m.memberCode) {
                        LabelGenerator.qrBitmap(m.memberCode, 320).asImageBitmap()
                    }
                    Image(qr, contentDescription = m.memberCode, Modifier.size(180.dp))
                    Spacer(Modifier.height(8.dp))
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

        SectionHeader(stringResource(R.string.stat_active_loans))
        Spacer(Modifier.height(8.dp))
        loans.forEach { item ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("${item.bookTitle} — ${item.copyCode}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val overdue = item.loan.dueAt < System.currentTimeMillis()
                Text(
                    "${stringResource(R.string.due_date)}: " +
                        DualCalendarFormatter.format(item.loan.dueAt, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                TextButton(onClick = {
                    scope.launch {
                        repo.renewLoan(item.loan.id)
                        Toast.makeText(context, R.string.renew_done, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.renew)) }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        BorrowingHistorySection(history, locale) { "${it.bookTitle} — ${it.copyCode}" }
    }

    val current = member
    if (showEdit && current != null) {
        EditMemberDialog(
            member = current,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                showEdit = false
                scope.launch {
                    repo.updateMember(updated)
                    refresh++
                }
            },
        )
    }
}

@Composable
private fun EditMemberDialog(
    member: MemberEntity,
    onDismiss: () -> Unit,
    onSave: (MemberEntity) -> Unit,
) {
    var name by remember { mutableStateOf(member.fullName) }
    var phone by remember { mutableStateOf(member.phone.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_full_name)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.field_phone)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        member.copy(
                            fullName = name.trim(),
                            phone = phone.trim().ifBlank { null },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
