package com.ethiopialibrary.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethiopialibrary.app.R
import com.ethiopialibrary.app.data.LibraryRepository
import com.ethiopialibrary.app.sync.SyncEngine
import com.ethiopialibrary.app.sync.SyncLocator
import com.ethiopialibrary.app.sync.SyncWorker
import com.ethiopialibrary.app.sync.restoreFailureMessageRes
import com.ethiopialibrary.app.ui.BigButton
import com.ethiopialibrary.app.ui.BigOutlinedButton
import com.ethiopialibrary.app.ui.SectionHeader
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Cloud state machine: not configured -> signed out -> signed in.
 * Thin glue over FirebaseAuth/WorkManager; the engine itself is unit-tested.
 */
@Composable
fun CloudBackupSection(repo: LibraryRepository) {
    val context = LocalContext.current

    SectionHeader(stringResource(R.string.cloud_backup))
    Spacer(Modifier.height(12.dp))

    if (FirebaseApp.getApps(context).isEmpty()) {
        Text(
            stringResource(R.string.cloud_not_configured),
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }

    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    if (user == null) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.email)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            BigButton(stringResource(R.string.sign_in)) {
                if (email.isNotBlank() && password.isNotBlank()) {
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { SyncWorker.backupNow(context) }
                        .addOnFailureListener {
                            Toast.makeText(context, R.string.sign_in_failed, Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
    } else {
        val scope = rememberCoroutineScope()
        var showRestoreConfirm by remember { mutableStateOf(false) }
        val lastResult by repo.lastSyncResult().collectAsStateWithLifecycle(null)
        val backingUp by remember { SyncWorker.isBackupRunning(context) }
            .collectAsStateWithLifecycle(false)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            BackupStatusLine(lastResult, backingUp)
            BigButton(stringResource(R.string.backup_now)) {
                SyncWorker.backupNow(context)
                Toast.makeText(context, R.string.backup_started, Toast.LENGTH_SHORT).show()
            }
            BigOutlinedButton(stringResource(R.string.restore_from_cloud)) {
                showRestoreConfirm = true
            }
            Text(
                stringResource(R.string.auto_backup_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { auth.signOut() }) {
                Text(stringResource(R.string.sign_out))
            }
        }
        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false },
                title = { Text(stringResource(R.string.restore_from_cloud)) },
                text = { Text(stringResource(R.string.restore_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreConfirm = false
                        scope.launch {
                            val message = runCatching {
                                SyncLocator.engine(context)?.restore()
                            }.fold(
                                onSuccess = { restored ->
                                    when {
                                        restored == null -> context.getString(R.string.restore_failed)
                                        restored == 0 -> context.getString(R.string.restore_cloud_empty)
                                        else -> context.getString(R.string.restore_done)
                                    }
                                },
                                onFailure = { context.getString(restoreFailureMessageRes(it)) },
                            )
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }) { Text(stringResource(R.string.restore_from_cloud)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

/** Truthful outcome of the most recent backup, or a live "backing up" indicator. */
@Composable
private fun BackupStatusLine(lastResult: String?, backingUp: Boolean) {
    val statusRes = when {
        backingUp -> R.string.backup_in_progress
        lastResult == SyncEngine.RESULT_OK -> R.string.backup_status_ok
        lastResult != null -> R.string.backup_status_failed
        else -> return
    }
    Text(
        stringResource(statusRes),
        style = MaterialTheme.typography.bodyMedium,
        color = if (statusRes == R.string.backup_status_failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
