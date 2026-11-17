package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentDao
import com.example.data.local.PdfDocumentEntity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class StorageStats(
  val vaultBytes: Long,
  val exportsBytes: Long,
  val cacheBytes: Long,
  val tessdataBytes: Long,
  val documentCount: Int,
) {
  val totalBytes: Long get() = vaultBytes + exportsBytes + cacheBytes + tessdataBytes
}

class DocumentRepository(
  private val context: Context,
  private val dao: PdfDocumentDao,
) {
  val documentsDir: File = File(context.filesDir, "documents").apply { mkdirs() }
  val thumbnailsDir: File = File(context.filesDir, "thumbnails").apply { mkdirs() }
  val exportsDir: File = File(context.filesDir, "exports").apply { mkdirs() }
  val tessdataDir: File = File(context.filesDir, "tessdata").apply { mkdirs() }
  val unlockedCacheDir: File = File(context.cacheDir, "unlocked").apply { mkdirs() }

  val allDocuments: Flow<List<PdfDocumentEntity>> = dao.observeAllDocuments()
  val recentDocuments: Flow<List<PdfDocumentEntity>> = dao.observeRecentDocuments(10)
  val favoriteDocuments: Flow<List<PdfDocumentEntity>> = dao.observeFavoriteDocuments()

  suspend fun getDocumentById(id: Long): PdfDocumentEntity? = withContext(Dispatchers.IO) {
    dao.getDocumentById(id)
  }

  suspend fun touchOpened(id: Long) = withContext(Dispatchers.IO) {
    dao.touchOpened(id)
  }

  suspend fun toggleFavorite(doc: PdfDocumentEntity) = withContext(Dispatchers.IO) {
    dao.setFavorite(doc.id, !doc.isFavorite)
  }

  suspend fun importPdfFromUri(uri: Uri): Result<PdfDocumentEntity> = withContext(Dispatchers.IO) {
    runCatching {
      val displayName = FileUtils.sanitizeFileName(FileUtils.resolveDisplayName(context, uri, "document.pdf"), "pdf")
      val targetFile = uniqueFile(documentsDir, displayName)
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(targetFile).use { output -> input.copyTo(output) }
      } ?: error("Cannot open selected PDF stream")
      registerPdfFile(targetFile, displayName.removeSuffix(".pdf"))
    }
  }

  suspend fun registerPdfFile(file: File, baseDisplayName: String? = null): PdfDocumentEntity =
    withContext(Dispatchers.IO) {
      val inspection = inspectPdf(file)
      val existing = dao.getDocumentByPath(file.absolutePath)
      val name = baseDisplayName?.let { FileUtils.sanitizeFileName(it, "pdf") } ?: file.name
      val thumbPath = if (!inspection.isEncrypted && inspection.pageCount > 0) {
        generateThumbnail(file, file.nameWithoutExtension)
      } else {
        null
      }
      val now = System.currentTimeMillis()
      if (existing != null) {
        val updated = existing.copy(
          displayName = name,
          thumbnailPath = thumbPath ?: existing.thumbnailPath,
          sizeBytes = file.length(),
          pageCount = inspection.pageCount,
          lastModified = now,
          lastOpened = now,
          isPasswordProtected = inspection.isEncrypted,
        )
        dao.update(updated)
        updated
      } else {
        val entity = PdfDocumentEntity(
          displayName = name,
          filePath = file.absolutePath,
          thumbnailPath = thumbPath,
          sizeBytes = file.length(),
          pageCount = inspection.pageCount,
          lastModified = now,
          lastOpened = now,
          isFavorite = false,
          isPasswordProtected = inspection.isEncrypted,
        )
        val id = dao.insert(entity)
        entity.copy(id = id)
      }
    }

  suspend fun refreshMetadata(doc: PdfDocumentEntity): PdfDocumentEntity = withContext(Dispatchers.IO) {
    val file = File(doc.filePath)
    if (!file.exists()) return@withContext doc
    val inspection = inspectPdf(file)
    val thumbPath = if (!inspection.isEncrypted && inspection.pageCount > 0) {
      generateThumbnail(file, file.nameWithoutExtension)
    } else {
      null
    }
    val updated = doc.copy(
      thumbnailPath = thumbPath,
      sizeBytes = file.length(),
      pageCount = inspection.pageCount,
      lastModified = System.currentTimeMillis(),
      isPasswordProtected = inspection.isEncrypted,
    )
    dao.update(updated)
    updated
  }

  suspend fun renameDocument(doc: PdfDocumentEntity, newName: String): Result<PdfDocumentEntity> =
    withContext(Dispatchers.IO) {
      runCatching {
        val sanitized = FileUtils.sanitizeFileName(newName, "pdf")
        val oldFile = File(doc.filePath)
        val newFile = uniqueFile(documentsDir, sanitized)
        if (oldFile.exists() && oldFile.absolutePath != newFile.absolutePath) {
          oldFile.renameTo(newFile)
        }
        val updated = doc.copy(
          displayName = sanitized,
          filePath = newFile.absolutePath,
          lastModified = System.currentTimeMillis(),
        )
        dao.update(updated)
        updated
      }
    }

  suspend fun deleteDocument(doc: PdfDocumentEntity) = withContext(Dispatchers.IO) {
    File(doc.filePath).delete()
    doc.thumbnailPath?.let { File(it).delete() }
    dao.deleteById(doc.id)
  }

  fun createVaultFile(desiredName: String): File {
    return uniqueFile(documentsDir, FileUtils.sanitizeFileName(desiredName, "pdf"))
  }

  fun createExportFile(desiredName: String, extension: String): File {
    return uniqueFile(exportsDir, FileUtils.sanitizeFileName(desiredName, extension))
  }

  suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
    val count = documentsDir.listFiles()?.size ?: 0
    StorageStats(
      vaultBytes = folderSize(documentsDir) + folderSize(thumbnailsDir),
      exportsBytes = folderSize(exportsDir),
      cacheBytes = folderSize(context.cacheDir),
      tessdataBytes = folderSize(tessdataDir),
      documentCount = count,
    )
  }

  suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
    val before = folderSize(context.cacheDir)
    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    unlockedCacheDir.mkdirs()
    before
  }

  data class PdfInspection(val pageCount: Int, val isEncrypted: Boolean)

  fun inspectPdf(file: File): PdfInspection {
    // First try Android PdfRenderer for speed and compatibility
    try {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          return PdfInspection(pageCount = renderer.pageCount, isEncrypted = false)
        }
      }
    } catch (_: SecurityException) {
      return PdfInspection(pageCount = 0, isEncrypted = true)
    } catch (_: Throwable) {
      // Fallback to PDFBox inspection
    }
    return try {
      PDDocument.load(file).use { doc ->
        val encrypted = doc.isEncrypted
        PdfInspection(pageCount = doc.numberOfPages, isEncrypted = encrypted)
      }
    } catch (e: Throwable) {
      val msg = e.message.orEmpty().lowercase()
      if (msg.contains("password") || msg.contains("encrypted") || msg.contains("decrypt")) {
        PdfInspection(pageCount = 0, isEncrypted = true)
      } else {
        throw IllegalArgumentException("Invalid or corrupted PDF file", e)
      }
    }
  }

  private fun generateThumbnail(pdfFile: File, key: String): String? {
    return runCatching {
      ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          if (renderer.pageCount <= 0) return null
          renderer.openPage(0).use { page ->
            val targetWidth = 320
            val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
            val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val thumbFile = File(thumbnailsDir, "${key}_${System.currentTimeMillis()}.png")
            FileOutputStream(thumbFile).use { out ->
              bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            bitmap.recycle()
            thumbFile.absolutePath
          }
        }
      }
    }.getOrNull()
  }

  private fun uniqueFile(dir: File, fileName: String): File {
    var candidate = File(dir, fileName)
    if (!candidate.exists()) return candidate
    val base = fileName.substringBeforeLast('.')
    val ext = fileName.substringAfterLast('.', "")
    var index = 1
    while (candidate.exists()) {
      val suffix = if (ext.isNotEmpty()) ".$ext" else ""
      candidate = File(dir, "${base}_$index$suffix")
      index++
    }
    return candidate
  }

  private fun folderSize(dir: File): Long {
    if (!dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    return dir.listFiles()?.sumOf { folderSize(it) } ?: 0L
  }
}
