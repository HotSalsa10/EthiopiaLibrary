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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.BookEntity
import com.ethiopialibrary.app.data.CopyRow
import com.ethiopialibrary.app.data.CopyStatus
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.ui.AppTopBar
import com.ethiopialibrary.app.ui.BigButton
import kotlinx.coroutines.launch

@Composable
fun BookDetailScreen(repo: LibraryRepository, bookId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val book by produceState<BookEntity?>(null, bookId) { value = repo.bookById(bookId) }
    val copies by repo.copiesForBook(bookId).collectAsStateWithLifecycle(emptyList())

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        AppTopBar(book?.title.orEmpty(), onBack)
        book?.let { b ->
            Text(b.author, style = MaterialTheme.typography.titleMedium)
            b.isbn?.let { Text("ISBN: $it", style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(12.dp))
        BigButton(stringResource(R.string.add_copy)) {
            scope.launch { repo.addCopy(bookId) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(copies, key = { it.copy.id }) { row ->
                CopyCard(row) { status ->
                    scope.launch { repo.setCopyStatus(row.copy.id, status) }
                }
            }
        }
    }
}

@Composable
private fun CopyCard(row: CopyRow, onSetStatus: (CopyStatus) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.copy.copyCode, style = MaterialTheme.typography.titleMedium)
                Text(statusText(row.copy.status), style = MaterialTheme.typography.bodyMedium)
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
