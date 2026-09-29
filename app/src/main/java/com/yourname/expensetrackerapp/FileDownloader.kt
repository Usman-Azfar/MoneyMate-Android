package com.yourname.expensetrackerapp

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/** Shared "write a file into the device's Downloads folder" logic, used by both
 *  [PdfReportGenerator] and [CsvReportGenerator] so the MediaStore-vs-legacy-file-API split
 *  (API 29+ vs below) only needs to be handled correctly once. */
object FileDownloader {
    fun writeToDownloads(context: Context, fileName: String, mimeType: String, write: (OutputStream) -> Unit): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                context.contentResolver.openOutputStream(uri)?.use { write(it) } ?: return false
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                File(downloadsDir, fileName).outputStream().use { write(it) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
