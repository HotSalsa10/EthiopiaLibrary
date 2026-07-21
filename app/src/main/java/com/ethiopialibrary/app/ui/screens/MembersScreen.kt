package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.MemberStatus
import com.ethiopialibrary.app.labels.exportAndShareLabels
import com.ethiopialibrary.app.ui.AddMemberDialog
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppSearchField
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.EmptyState
import com.ethiopialibrary.app.ui.MembersViewModel
import com.ethiopialibrary.app.ui.PageColumn
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

    PageColumn(scrollable = false) {
        AppTopBar(stringResource(R.string.nav_members), onBack)
        AppSearchField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = stringResource(R.string.search_hint),
            modifier = Modifier.fillMaxWidth(),
            autoFocus = true,
            onSubmit = null,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.add_member), Modifier.weight(1f)) { showAdd = true }
            BigOutlinedButton(stringResource(R.string.export_member_cards), Modifier.weight(1f)) {
                scope.launch {
                    val rows = repo.memberLabelRows()
                    if (rows.isEmpty()) {
                        Toast.makeText(context, R.string.labels_none, Toast.LENGTH_SHORT).show()
                    } else {
                        exportAndShareLabels(context, rows, "member-cards.pdf")
                        Toast.makeText(context, R.string.labels_exported, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f)) {
            if (members.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.People,
                    message = stringResource(if (query.isBlank()) R.string.members_empty else R.string.no_matches),
                    hint = if (query.isBlank()) stringResource(R.string.members_empty_hint) else null,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(members, key = { it.member.id }) { row ->
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenMember(row.member.id) },
                        ) {
                            Text(row.member.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(row.member.memberCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(R.string.member_active_loans, row.activeLoans),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }

    if (showAdd) {
        AddMemberDialog(
            onDismiss = { showAdd = false },
            onSave = { name, phone, nationalId, address ->
                vm.addMember(name, phone, nationalId, address)
                showAdd = false
            },
        )
    }
}
