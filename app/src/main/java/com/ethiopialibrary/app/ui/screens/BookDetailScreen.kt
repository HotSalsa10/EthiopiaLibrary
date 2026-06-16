package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.BookEntity
import com.ethiopialibrary.app.data.CopyRow
import com.ethiopialibrary.app.data.CopyStatus
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import kotlinx.coroutines.launch

@Composable
fun BookDetailScreen(repo: LibraryRepository, bookId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val book by produceState<BookEntity?>(null, bookId, refresh) { value = repo.bookById(bookId) }
    val copies by repo.copiesForBook(bookId).collectAsStateWithLifecycle(emptyList())
    val history by repo.bookHistory(bookId).collectAsStateWithLifecycle(emptyList())
    val locale = LocalConfiguration.current.locales[0]
    var showEdit by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        AppTopBar(book?.title.orEmpty(), onBack) {
            IconButton(onClick = { showEdit = true }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_book))
            }
        }
        book?.let { b ->
            Text(b.author, style = MaterialTheme.typography.titleMedium)
            b.isbn?.let { Text("ISBN: $it", style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(12.dp))
        BigButton(stringResource(R.string.add_copy)) {
            scope.launch { repo.addCopy(bookId) }
        }
        Spacer(Modifier.height(16.dp))
        copies.forEach { row ->
            CopyCard(row) { status ->
                scope.launch { repo.setCopyStatus(row.copy.id, status) }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        BorrowingHistorySection(history, locale) { "${it.memberName} (${it.memberCode})" }
    }

    val current = book
    if (showEdit && current != null) {
        EditBookDialog(
            book = current,
            onDismiss = { showEdit = false },
            onSave = { updated ->
                showEdit = false
                scope.launch {
                    repo.updateBook(updated)
                    refresh++
                }
            },
        )
    }
}

@Composable
private fun EditBookDialog(
    book: BookEntity,
    onDismiss: () -> Unit,
    onSave: (BookEntity) -> Unit,
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var category by remember { mutableStateOf(book.category) }
    var isbn by remember { mutableStateOf(book.isbn.orEmpty()) }
    var language by remember { mutableStateOf(book.language) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_book)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text(stringResource(R.string.field_author)) }, singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.field_category)) }, singleLine = true)
                Text(stringResource(R.string.field_language), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(language == "am", { language = "am" }, label = { Text(stringResource(R.string.lang_amharic)) })
                    FilterChip(language == "ar", { language = "ar" }, label = { Text(stringResource(R.string.lang_arabic)) })
                    FilterChip(language == "en", { language = "en" }, label = { Text(stringResource(R.string.lang_english)) })
                }
                OutlinedTextField(isbn, { isbn = it }, label = { Text(stringResource(R.string.field_isbn)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && author.isNotBlank(),
                onClick = {
                    onSave(
                        book.copy(
                            title = title.trim(),
                            author = author.trim(),
                            category = category.trim(),
                            language = language,
                            isbn = isbn.trim().ifBlank { null },
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

@Composable
private fun CopyCard(row: CopyRow, onSetStatus: (CopyStatus) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.copy.copyCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(statusText(row.copy.status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(if (row.onLoan) R.string.on_loan else R.string.available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.onLoan) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    CopyStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(statusText(status)) },
                            onClick = {
                                menuOpen = false
                                onSetStatus(status)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun statusText(status: CopyStatus): String = stringResource(
    when (status) {
        CopyStatus.IN_SERVICE -> R.string.copy_status_in_service
        CopyStatus.LOST -> R.string.copy_status_lost
        CopyStatus.DAMAGED -> R.string.copy_status_damaged
        CopyStatus.RETIRED -> R.string.copy_status_retired
    },
)
