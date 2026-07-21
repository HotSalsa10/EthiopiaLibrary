package com.ethiopialibrary.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.CategoryEntity

/** Pick a category from the list, or add a new one inline. */
@Composable
internal fun CategoryPicker(
    categories: List<CategoryEntity>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onAddCategory: (String, String, (duplicate: Boolean) -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var codeDuplicateError by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.code == selectedCode }
    Box {
        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                selected?.let { "${it.name} (${it.code})" }
                    ?: stringResource(R.string.field_category),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.name} (${c.code})") },
                    onClick = { onSelect(c.code); menuOpen = false },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_category)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { menuOpen = false; showAddCategory = true },
            )
        }
    }
    if (showAddCategory) {
        AddCategoryDialog(
            isDuplicate = codeDuplicateError,
            onDismiss = { showAddCategory = false; codeDuplicateError = false },
            onSave = { name, code ->
                codeDuplicateError = false
                onAddCategory(name, code) { duplicate ->
                    if (duplicate) {
                        codeDuplicateError = true
                    } else {
                        onSelect(code.trim().uppercase())
                        showAddCategory = false
                    }
                }
            },
        )
    }
}

@Composable
internal fun AddCategoryDialog(isDuplicate: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(stringResource(R.string.field_category)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(nameFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { codeFocus.requestFocus() }),
                )
                OutlinedTextField(
                    code,
                    { code = it.filter(Char::isLetter).take(2).uppercase() },
                    label = { Text(stringResource(R.string.category_code)) },
                    singleLine = true,
                    isError = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text(stringResource(R.string.error_category_code_exists)) }
                    } else {
                        null
                    },
                    modifier = Modifier.focusRequester(codeFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && code.length == 2,
                onClick = { onSave(name.trim(), code) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
}
