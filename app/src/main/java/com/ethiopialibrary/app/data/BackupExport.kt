package com.ethiopialibrary.app.data

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/**
 * Creates a timestamped copy of the database and opens the system share
 * sheet - the operator's off-device insurance (Drive/WhatsApp/USB stick).
 * The file is plain SQLite, readable with standard tools; the in-app
 * recovery path remains the cloud restore.
 */
suspend fun exportAndShareBackup(context: Context, repo: LibraryRepository) {
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "backup")
    val file = repo.createBackupFile(dir)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
