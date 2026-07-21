package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.CategoryEntity
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.labels.exportAndShareLabels
import com.ethiopialibrary.app.ui.AppCard
import com.ethiopialibrary.app.ui.AppSearchField
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.BooksViewModel
import com.ethiopialibrary.app.ui.EmptyState
import com.ethiopialibrary.app.ui.PageColumn
import kotlinx.coroutines.launch

@Composable
fun BooksScreen(
    vm: BooksViewModel,
    repo: LibraryRepository,
    onOpenBook: (String) -> Unit,
    onBack: () -> Unit,
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val categoryFilter by vm.categoryFilter.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    PageColumn(scrollable = false) {
        AppTopBar(stringResource(R.string.nav_books), onBack)
        AppSearchField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = stringResource(R.string.search_hint),
            modifier = Modifier.fillMaxWidth(),
            autoFocus = true,
            onSubmit = null,
        )
        Spacer(Modifier.height(12.dp))
        // Category filter: "All" + a chip per category.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = categoryFilter.isEmpty(),
                onClick = { vm.setCategoryFilter("") },
                label = { Text(stringResource(R.string.filter_all)) },
            )
            categories.forEach { c ->
                FilterChip(
                    selected = categoryFilter == c.code,
                    onClick = { vm.setCategoryFilter(c.code) },
                    label = { Text(c.code) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.add_book), Modifier.weight(1f)) { showAdd = true }
            BigOutlinedButton(stringResource(R.string.export_labels), Modifier.weight(1f)) {
                scope.launch {
                    val rows = repo.copyLabelRows()
                    if (rows.isEmpty()) {
                        Toast.makeText(context, R.string.labels_none, Toast.LENGTH_SHORT).show()
                    } else {
                        exportAndShareLabels(context, rows, "book-labels.pdf")
                        Toast.makeText(context, R.string.labels_exported, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f)) {
            if (books.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    message = stringResource(if (query.isBlank() && categoryFilter.isEmpty()) R.string.books_empty else R.string.no_matches),
                    hint = if (query.isBlank() && categoryFilter.isEmpty()) stringResource(R.string.books_empty_hint) else null,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(books, key = { it.book.id }) { row ->
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenBook(row.book.id) },
                        ) {
                            Text(row.book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(row.book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(
                                    R.string.available_of_total,
                                    row.availableCopies,
                                    row.totalCopies,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (row.availableCopies > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddBookDialog(
            categories = categories,
            onAddCategory = vm::addCategory,
            onDismiss = { showAdd = false },
            onSave = { title, author, categoryCode, language, isbn, copies, volumes ->
                vm.addBook(title, author, categoryCode, language, isbn, copies, volumes)
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddBookDialog(
    categories: List<CategoryEntity>,
    onAddCategory: (String, String, (duplicate: Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?, Int, Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var categoryCode by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var copies by remember { mutableStateOf("1") }
    var volumes by remember { mutableStateOf("1") }
    var language by remember { mutableStateOf("am") }
    val titleFocus = remember { FocusRequester() }
    val authorFocus = remember { FocusRequester() }
    val isbnFocus = remember { FocusRequester() }
    val copiesFocus = remember { FocusRequester() }
    val volumesFocus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_book)) },
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
                // The chain stops here: the next control is the CategoryPicker
                // (a dropdown), so author gets Done instead of Next into it.
                OutlinedTextField(
                    author, { author = it },
                    label = { Text(stringResource(R.string.field_author)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(authorFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
                CategoryPicker(categories, categoryCode, { categoryCode = it }, onAddCategory)
                Text(stringResource(R.string.field_language), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(language == "am", { language = "am" }, label = { Text(stringResource(R.string.lang_amharic)) })
                    FilterChip(language == "ar", { language = "ar" }, label = { Text(stringResource(R.string.lang_arabic)) })
                    FilterChip(language == "en", { language = "en" }, label = { Text(stringResource(R.string.lang_english)) })
                }
                OutlinedTextField(
                    isbn, { isbn = it },
                    label = { Text(stringResource(R.string.field_isbn)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(isbnFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { copiesFocus.requestFocus() }),
                )
                OutlinedTextField(
                    copies,
                    { copies = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.field_copies)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(copiesFocus),
                    isError = (copies.toIntOrNull() ?: 0) < 1,
                    supportingText = if ((copies.toIntOrNull() ?: 0) < 1) { { Text(stringResource(R.string.error_min_one)) } } else { null },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { volumesFocus.requestFocus() }),
                )
                OutlinedTextField(
                    volumes,
                    { volumes = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.field_volumes)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(volumesFocus),
                    isError = (volumes.toIntOrNull() ?: 0) < 1,
                    supportingText = if ((volumes.toIntOrNull() ?: 0) < 1) { { Text(stringResource(R.string.error_min_one)) } } else { null },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && author.isNotBlank() && categoryCode.isNotBlank() &&
                    (copies.toIntOrNull() ?: 0) >= 1 && (volumes.toIntOrNull() ?: 0) >= 1,
                onClick = {
                    onSave(
                        title.trim(),
                        author.trim(),
                        categoryCode,
                        language,
                        isbn.trim().ifBlank { null },
                        copies.toIntOrNull() ?: 1,
                        volumes.toIntOrNull() ?: 1,
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
