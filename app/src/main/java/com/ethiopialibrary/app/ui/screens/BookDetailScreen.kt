package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.AddCategoryResult
import com.ethiopialibrary.app.data.BookEntity
import com.ethiopialibrary.app.data.CategoryEntity
import com.ethiopialibrary.app.data.ChangeCategoryResult
import com.ethiopialibrary.app.data.CopyRow
import com.ethiopialibrary.app.data.CopyStatus
import com.ethiopialibrary.app.data.DeleteBookResult
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.labels.exportAndShareLabels
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.PageColumn
import com.ethiopialibrary.app.ui.safeLaunch
import kotlinx.coroutines.launch

@Composable
fun BookDetailScreen(repo: LibraryRepository, bookId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val book by produceState<BookEntity?>(null, bookId, refresh) { value = repo.bookById(bookId) }
    val copies by repo.copiesForBook(bookId).collectAsStateWithLifecycle(emptyList())
    val history by repo.bookHistory(bookId).collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories().collectAsStateWithLifecycle(emptyList())
    val locale = LocalConfiguration.current.locales[0]
    var showEdit by remember { mutableStateOf(false) }
    var showAddCopy by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteBlockedCount by remember { mutableStateOf<Int?>(null) }
    // The edited book + the new category code, held while the category-change
    // warning dialog is up - the whole edit is one atomic user decision.
    var pendingCategoryChange by remember { mutableStateOf<Pair<BookEntity, String>?>(null) }
    var showReprintOffer by remember { mutableStateOf(false) }

    PageColumn {
        AppTopBar(book?.title.orEmpty(), onBack) {
            IconButton(onClick = { showEdit = true }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_book))
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_book))
            }
        }
        book?.let { b ->
            Text(b.author, style = MaterialTheme.typography.titleMedium)
            b.isbn?.let { Text(stringResource(R.string.isbn_label, it), style = MaterialTheme.typography.bodyMedium) }
            if (b.volumeCount > 1) {
                Text(stringResource(R.string.volumes_per_copy, b.volumeCount), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        BigButton(stringResource(R.string.add_copy)) { showAddCopy = true }
        Spacer(Modifier.height(16.dp))
        copies.forEach { row ->
            CopyCard(row) { status ->
                scope.safeLaunch { repo.setCopyStatus(row.copy.id, status) }
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
            categories = categories,
            onAddCategory = { name, code, onResult ->
                scope.safeLaunch { onResult(repo.addCategory(name, code) is AddCategoryResult.DuplicateCode) }
            },
            onDismiss = { showEdit = false },
            onSave = { updatedBook, newCode ->
                showEdit = false
                if (newCode != null) {
                    pendingCategoryChange = updatedBook to newCode
                } else {
                    scope.safeLaunch {
                        repo.updateBook(updatedBook)
                        refresh++
                    }
                }
            },
        )
    }

    pendingCategoryChange?.let { (updatedBook, newCode) ->
        AlertDialog(
            onDismissRequest = { pendingCategoryChange = null },
            title = { Text(stringResource(R.string.change_category_warning_title)) },
            text = { Text(stringResource(R.string.change_category_warning_body, copies.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.safeLaunch {
                            val result = repo.changeBookCategory(bookId, newCode, fieldEdits = updatedBook)
                            pendingCategoryChange = null
                            when (result) {
                                is ChangeCategoryResult.Success -> {
                                    refresh++
                                    showReprintOffer = true
                                }
                                ChangeCategoryResult.BookNotFound -> {
                                    Toast.makeText(context, context.getString(R.string.error_book_not_found), Toast.LENGTH_SHORT).show()
                                }
                                ChangeCategoryResult.CategoryNotFound -> {
                                    Toast.makeText(context, context.getString(R.string.error_category_not_found), Toast.LENGTH_SHORT).show()
                                }
                                ChangeCategoryResult.SameCategory -> {
                                    // Not reachable via this dialog (only shown when newCode differs from the
                                    // book's current category), but handled for exhaustiveness: no-op, no toast.
                                }
                            }
                        }
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryChange = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showReprintOffer) {
        AlertDialog(
            onDismissRequest = { showReprintOffer = false },
            text = { Text(stringResource(R.string.reprint_labels_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            exportAndShareLabels(context, repo.labelRowsForBook(bookId), "book-labels.pdf")
                            showReprintOffer = false
                        }
                    },
                ) { Text(stringResource(R.string.print_new_labels)) }
            },
            dismissButton = {
                TextButton(onClick = { showReprintOffer = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showAddCopy) {
        AddCopyDialog(
            onDismiss = { showAddCopy = false },
            onSave = { volume ->
                showAddCopy = false
                scope.safeLaunch { repo.addCopy(bookId, volumeNumber = volume) }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_book)) },
            text = { Text(stringResource(R.string.delete_book_confirm, copies.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.safeLaunch {
                            showDeleteConfirm = false
                            when (val r = repo.deleteBook(bookId)) {
                                is DeleteBookResult.Success -> {
                                    Toast.makeText(context, context.getString(R.string.book_deleted), Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                                is DeleteBookResult.BlockedByActiveLoans -> deleteBlockedCount = r.activeLoans
                                DeleteBookResult.NotFound -> {
                                    Toast.makeText(context, context.getString(R.string.error_book_not_found), Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            }
                        }
                    },
                ) { Text(stringResource(R.string.delete_book), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleteBlockedCount?.let { count ->
        AlertDialog(
            onDismissRequest = { deleteBlockedCount = null },
            title = { Text(stringResource(R.string.delete_book)) },
            text = { Text(stringResource(R.string.delete_book_blocked, count)) },
            confirmButton = {
                TextButton(onClick = { deleteBlockedCount = null }) { Text(stringResource(R.string.ok)) }
            },
        )
    }
}

@Composable
private fun AddCopyDialog(onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var volume by remember { mutableStateOf("0") }
    val volumeFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_copy)) },
        text = {
            OutlinedTextField(
                value = volume,
                onValueChange = { volume = it.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.field_volume)) },
                singleLine = true,
                modifier = Modifier.focusRequester(volumeFocus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(volume.toIntOrNull() ?: 0) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(Unit) { volumeFocus.requestFocus() }
}

@Composable
private fun EditBookDialog(
    book: BookEntity,
    categories: List<CategoryEntity>,
    onAddCategory: (String, String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (BookEntity, String?) -> Unit,
) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var isbn by remember { mutableStateOf(book.isbn.orEmpty()) }
    var language by remember { mutableStateOf(book.language) }
    var categoryCode by remember { mutableStateOf(book.categoryCode) }
    val titleFocus = remember { FocusRequester() }
    val authorFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_book)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    title, { title = it },
                    label = { Text(stringResource(R.string.field_title)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(titleFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { authorFocus.requestFocus() }),
                )
                // The chain stops here: the next control is the language FilterChip
                // row, so author gets Done instead of Next into it.
                OutlinedTextField(
                    author, { author = it },
                    label = { Text(stringResource(R.string.field_author)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(authorFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Text(stringResource(R.string.field_language), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(language == "am", { language = "am" }, label = { Text(stringResource(R.string.lang_amharic)) })
                    FilterChip(language == "ar", { language = "ar" }, label = { Text(stringResource(R.string.lang_arabic)) })
                    FilterChip(language == "en", { language = "en" }, label = { Text(stringResource(R.string.lang_english)) })
                }
                CategoryPicker(categories, categoryCode, { categoryCode = it }, onAddCategory)
                if (categoryCode != book.categoryCode) {
                    Text(
                        stringResource(R.string.change_category_warning_inline),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    isbn, { isbn = it },
                    label = { Text(stringResource(R.string.field_isbn)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
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
                            language = language,
                            isbn = isbn.trim().ifBlank { null },
                        ),
                        categoryCode.takeIf { it != book.categoryCode },
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
}

@Composable
private fun CopyCard(row: CopyRow, onSetStatus: (CopyStatus) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var pendingStatus by remember { mutableStateOf<CopyStatus?>(null) }
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
                    CopyStatus.entries.filter { it != row.copy.status }.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(statusText(status)) },
                            onClick = {
                                menuOpen = false
                                if (status == CopyStatus.LOST || status == CopyStatus.RETIRED) {
                                    pendingStatus = status
                                } else {
                                    onSetStatus(status)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    if (pendingStatus != null) {
        AlertDialog(
            onDismissRequest = { pendingStatus = null },
            title = { Text(stringResource(R.string.copy_status_confirm_title)) },
            text = { Text(stringResource(R.string.copy_status_confirm, row.copy.copyCode, statusText(pendingStatus!!))) },
            confirmButton = {
                TextButton(onClick = { onSetStatus(pendingStatus!!); pendingStatus = null }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pendingStatus = null }) { Text(stringResource(R.string.cancel)) } },
        )
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
