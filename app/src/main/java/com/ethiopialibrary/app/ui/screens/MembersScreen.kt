package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.MemberStatus
import com.ethiopialibrary.app.labels.exportAndShareLabels
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.MembersViewModel
import kotlinx.coroutines.launch

@Composable
fun MembersScreen(
    vm: MembersViewModel,
    repo: LibraryRepository,
    onOpenMember: (String) -> Unit,
    onBack: () -> Unit,
) {
    val members by vm.members.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        AppTopBar(stringResource(R.string.nav_members), onBack)
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.add_member), Modifier.weight(1f)) { showAdd = true }
            BigOutlinedButton(stringResource(R.string.export_member_cards), Modifier.weight(1f)) {
                scope.launch {
                    exportAndShareLabels(context, repo.memberLabelRows(), "member-cards.pdf")
                    Toast.makeText(context, R.string.labels_exported, Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(members, key = { it.member.id }) { row ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMember(row.member.id) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(row.member.fullName, style = MaterialTheme.typography.titleMedium)
                        Text(row.member.memberCode, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.member_active_loans, row.activeLoans),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (row.member.status == MemberStatus.SUSPENDED) {
                            Text(
                                stringResource(R.string.error_member_not_active),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddMemberDialog(
            onDismiss = { showAdd = false },
            onSave = { name, phone ->
                vm.addMember(name, phone)
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddMemberDialog(onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_full_name)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.field_phone)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), phone.trim().ifBlank { null }) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
