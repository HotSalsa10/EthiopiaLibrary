package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.data.LoanWithDetails
import com.ethiopialibrary.app.data.MemberEntity
import com.ethiopialibrary.app.data.MemberStatus
import com.ethiopialibrary.app.dates.DualCalendarFormatter
import com.ethiopialibrary.app.labels.LabelGenerator
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.EmptyState
import com.ethiopialibrary.app.ui.LocalCalendarMode
import com.ethiopialibrary.app.ui.PageColumn
import com.ethiopialibrary.app.ui.RenewConfirmDialog
import com.ethiopialibrary.app.ui.SectionHeader
import com.ethiopialibrary.app.ui.StarRatingDisplay
import com.ethiopialibrary.app.ui.TwoPaneRow
import com.ethiopialibrary.app.ui.renewResultMessageRes
import com.ethiopialibrary.app.ui.safeLaunch
import java.util.Locale

@Composable
fun MemberDetailScreen(repo: LibraryRepository, memberId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val member by produceState<MemberEntity?>(null, memberId, refresh) {
        value = repo.memberById(memberId)
    }
    val loans by repo.activeLoansForMember(memberId).collectAsStateWithLifecycle(emptyList())
    val history by repo.memberHistory(memberId).collectAsStateWithLifecycle(emptyList())
    // Recomputes when history changes (a new return+rating re-emits the flow).
    val avgRating by produceState<Double?>(null, memberId, history) {
        value = repo.memberAverageRating(memberId)
    }
    var renewTarget by remember { mutableStateOf<LoanWithDetails?>(null) }

    renewTarget?.let { target ->
        val preview by produceState<Long?>(null, target) { value = repo.renewalPreviewDueAt(target.loan.id) }
        RenewConfirmDialog(
            bookTitle = target.bookTitle,
            memberName = target.memberName,
            newDueAt = preview,
            locale = LocalConfiguration.current.locales[0],
            onConfirm = {
                scope.safeLaunch {
                    val result = repo.renewLoan(target.loan.id)
                    Toast.makeText(context, renewResultMessageRes(result), Toast.LENGTH_SHORT).show()
                }
                renewTarget = null
            },
            onDismiss = { renewTarget = null },
        )
    }
    val locale = LocalConfiguration.current.locales[0]

    var showEdit by remember { mutableStateOf(false) }
    // Portrait-only: the QR card starts collapsed so staff scroll past a small button,
    // not a full card, to reach the loans/history sections below (see MemberQrCard).
    var showCard by remember { mutableStateOf(false) }
    val onSetStatus: (MemberStatus) -> Unit = { status ->
        member?.let { m ->
            scope.safeLaunch {
                repo.setMemberStatus(m.id, status)
                refresh++
            }
        }
    }

    // BoxWithConstraints is the density-independent way to tell a wider-than-tall
    // tablet orientation from a taller-than-wide one, without a WindowSizeClass
    // dependency: landscape gets a dedicated QR pane, portrait a collapsible card.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            TwoPaneRow(
                left = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        MemberIdentityHeader(member, avgRating, onBack) { showEdit = true }
                        member?.let { m ->
                            Spacer(Modifier.height(16.dp))
                            MemberLoansSection(loans, locale) { renewTarget = it }
                            Spacer(Modifier.height(16.dp))
                            BorrowingHistorySection(history, locale) { "${it.bookTitle} — ${it.copyCode}" }
                            Spacer(Modifier.height(16.dp))
                            MemberStatusButton(m, onSetStatus)
                        }
                    }
                },
                right = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        member?.let { m -> MemberQrCard(m) }
                    }
                },
            )
        } else {
            PageColumn {
                MemberIdentityHeader(member, avgRating, onBack) { showEdit = true }
                member?.let { m ->
                    Spacer(Modifier.height(12.dp))
                    BigOutlinedButton(
                        stringResource(if (showCard) R.string.hide_member_card else R.string.show_member_card),
                    ) { showCard = !showCard }
                    if (showCard) {
                        Spacer(Modifier.height(12.dp))
                        MemberQrCard(m)
                    }
                    Spacer(Modifier.height(16.dp))
                    MemberLoansSection(loans, locale) { renewTarget = it }
                    Spacer(Modifier.height(16.dp))
                    BorrowingHistorySection(history, locale) { "${it.bookTitle} — ${it.copyCode}" }
                    Spacer(Modifier.height(16.dp))
                    MemberStatusButton(m, onSetStatus)
                }
            }
        }
    }

    val current = member
    if (showEdit && current != null) {
        EditMemberDialog(
            member = current,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                showEdit = false
                scope.safeLaunch {
                    repo.updateMember(updated)
                    refresh++
                }
            },
        )
    }
}

/**
 * Member identity block: the top bar (name as its title, edit action), then the
 * code/rating/contact info as secondary metadata beneath it. The code is demoted to
 * titleMedium + onSurfaceVariant so it clearly reads as secondary to the name (already
 * headlineMedium via [AppTopBar]).
 */
@Composable
private fun MemberIdentityHeader(
    member: MemberEntity?,
    avgRating: Double?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    AppTopBar(member?.fullName.orEmpty(), onBack) {
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_member))
        }
    }
    member?.let { m ->
        Text(
            m.memberCode,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        avgRating?.let {
            Spacer(Modifier.height(4.dp))
            StarRatingDisplay(it)
        }
        m.phone?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        m.nationalId?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        m.address?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The QR membership card: always visible in the landscape right pane, toggled in portrait. */
@Composable
private fun MemberQrCard(member: MemberEntity) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.member_card), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val qr = remember(member.memberCode) {
                LabelGenerator.qrBitmap(member.memberCode, 320).asImageBitmap()
            }
            Image(qr, contentDescription = member.memberCode, Modifier.size(180.dp))
            Spacer(Modifier.height(8.dp))
            Text(member.memberCode, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** "Books on loan": the member's active loans, or an [EmptyState] when there are none. */
@Composable
private fun MemberLoansSection(
    loans: List<LoanWithDetails>,
    locale: Locale,
    onRenew: (LoanWithDetails) -> Unit,
) {
    SectionHeader(stringResource(R.string.stat_active_loans))
    Spacer(Modifier.height(8.dp))
    if (loans.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            message = stringResource(R.string.member_no_active_loans),
        )
    } else {
        loans.forEach { item ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("${item.bookTitle} — ${item.copyCode}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val overdue = item.loan.dueAt < System.currentTimeMillis()
                Text(
                    "${stringResource(R.string.due_date)}: " +
                        DualCalendarFormatter.format(item.loan.dueAt, locale, LocalCalendarMode.current),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                TextButton(onClick = { onRenew(item) }) { Text(stringResource(R.string.renew)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Suspend/Activate control, demoted to the very bottom of the screen's content (after
 * loans/history) since it's a rare, semi-destructive action staff don't come here for.
 */
@Composable
private fun MemberStatusButton(member: MemberEntity, onSetStatus: (MemberStatus) -> Unit) {
    if (member.status == MemberStatus.ACTIVE) {
        BigOutlinedButton(stringResource(R.string.suspend_member)) { onSetStatus(MemberStatus.SUSPENDED) }
    } else {
        BigButton(stringResource(R.string.activate_member)) { onSetStatus(MemberStatus.ACTIVE) }
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
    var nationalId by remember { mutableStateOf(member.nationalId.orEmpty()) }
    var address by remember { mutableStateOf(member.address.orEmpty()) }
    val nameFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }
    val idFocus = remember { FocusRequester() }
    val addressFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(stringResource(R.string.field_full_name)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(nameFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { phoneFocus.requestFocus() }),
                )
                OutlinedTextField(
                    phone, { phone = it },
                    label = { Text(stringResource(R.string.field_phone)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(phoneFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { idFocus.requestFocus() }),
                )
                OutlinedTextField(
                    nationalId, { nationalId = it },
                    label = { Text(stringResource(R.string.field_national_id)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(idFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { addressFocus.requestFocus() }),
                )
                OutlinedTextField(
                    address, { address = it },
                    label = { Text(stringResource(R.string.field_address)) },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.focusRequester(addressFocus),
                    // Multi-line field: Next doesn't apply the same way - Done is fine,
                    // and this is the dialog's last field either way.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
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
                            nationalId = nationalId.trim().ifBlank { null },
                            address = address.trim().ifBlank { null },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
}
