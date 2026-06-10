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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.ethiopialibrary.app.labels.exportAndShareLabels
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.BooksViewModel
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
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        AppTopBar(stringResource(R.string.nav_books), onBack)
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(stringResource(R.string.add_book), Modifier.weight(1f)) { showAdd = true }
            BigOutlinedButton(stringResource(R.string.export_labels), Modifier.weight(1f)) {
                scope.launch {
                    exportAndShareLabels(context, repo.copyLabelRows(), "book-labels.pdf")
                    Toast.makeText(context, R.string.labels_exported, Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(books, key = { it.book.id }) { row ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBook(row.book.id) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(row.book.title, style = MaterialTheme.typography.titleMedium)
                        Text(row.book.author, style = MaterialTheme.typography.bodyMedium)
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

    if (showAdd) {
        AddBookDialog(
            onDismiss = { showAdd = false },
            onSave = { title, author, category, language, isbn, copies ->
                vm.addBook(title, author, category, language, isbn, copies)
                showAdd = false
            },
        )
    }
}

@Composable
private fun AddBookDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?, Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var copies by remember { mutableStateOf("1") }
    var language by remember { mutableStateOf("am") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_book)) },
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
                OutlinedTextField(
                    copies,
                    { copies = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.field_copies)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && author.isNotBlank(),
                onClick = {
                    onSave(
                        title.trim(),
                        author.trim(),
                        category.trim(),
                        language,
                        isbn.trim().ifBlank { null },
                        copies.toIntOrNull() ?: 1,
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
