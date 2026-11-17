package com.example.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FileUtils {

  fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format(
      Locale.US,
      "%.1f %s",
      bytes / 1024.0.pow(digitGroups.toDouble()),
      units[digitGroups],
    )
  }

  fun formatDate(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
  }

  fun sanitizeFileName(raw: String, extension: String = "pdf"): String {
    val clean = raw.trim().replace(Regex("[^a-zA-Z0-9._\\-\\s\\u0980-\\u09FF]"), "_").ifBlank { "document" }
    val extSuffix = ".$extension"
    return if (clean.lowercase(Locale.US).endsWith(extSuffix)) clean else "$clean$extSuffix"
  }

  fun resolveDisplayName(context: Context, uri: Uri, fallback: String = "imported.pdf"): String {
    var name: String? = null
    if (uri.scheme == "content") {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) name = cursor.getString(idx)
        }
      }
    }
    if (name.isNullOrBlank()) {
      name = uri.lastPathSegment?.substringAfterLast('/')
    }
    return name?.takeIf { it.isNotBlank() } ?: fallback
  }

  fun getShareUri(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
    val uri = getShareUri(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, chooserTitle).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }

  fun shareFiles(context: Context, files: List<File>, mimeType: String, chooserTitle: String) {
    if (files.isEmpty()) return
    if (files.size == 1) {
      shareFile(context, files.first(), mimeType, chooserTitle)
      return
    }
    val uris = ArrayList(files.map { getShareUri(context, it) })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
      type = mimeType
      putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, chooserTitle).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
  }

  fun copyFileToUri(context: Context, source: File, destinationUri: Uri): Boolean {
    return runCatching {
      context.contentResolver.openOutputStream(destinationUri)?.use { out ->
        FileInputStream(source).use { input -> input.copyTo(out) }
      } ?: return false
      true
    }.getOrDefault(false)
  }

  fun printPdf(context: Context, file: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val adapter = object : PrintDocumentAdapter() {
      override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
      ) {
        if (cancellationSignal?.isCanceled == true) {
          callback.onLayoutCancelled()
          return
        }
        val info = PrintDocumentInfo.Builder(jobName)
          .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
          .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
          .build()
        callback.onLayoutFinished(info, true)
      }

      override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
      ) {
        try {
          FileInputStream(file).use { input ->
            FileOutputStream(destination.fileDescriptor).use { output ->
              input.copyTo(output)
            }
          }
          if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
          } else {
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          }
        } catch (e: Exception) {
          callback.onWriteFailed(e.message)
        }
      }
    }
    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
  }
}
