package com.ethiopialibrary.app.labels

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.ethiopialibrary.app.data.LabelRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Writes a label sheet PDF to app storage and opens the system share sheet. */
suspend fun exportAndShareLabels(context: Context, rows: List<LabelRow>, fileName: String) {
    if (rows.isEmpty()) return
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "labels")
        dir.mkdirs()
        val out = File(dir, fileName)
        FileOutputStream(out).use { stream ->
            LabelGenerator.createLabelPdf(rows.map { LabelData(it.code, it.title) }, stream)
        }
        out
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
