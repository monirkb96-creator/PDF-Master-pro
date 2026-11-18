package com.example.domain.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

open class PdfRendererHelper(private val unlockedCacheDir: File) {
  private val renderMutex = Mutex()

  open suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
    renderMutex.withLock {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer -> renderer.pageCount }
      }
    }
  }

  open suspend fun renderPage(
    file: File,
    pageIndex: Int,
    targetWidthPx: Int = 1200,
    extraRotationDegrees: Int = 0,
  ): Bitmap? = withContext(Dispatchers.IO) {
    renderMutex.withLock {
      runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
          PdfRenderer(pfd).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
            renderer.openPage(pageIndex).use { page ->
              val w = targetWidthPx.coerceIn(120, 2400)
              val scale = w.toFloat() / page.width.coerceAtLeast(1)
              val h = (page.height * scale).toInt().coerceAtLeast(1)
              val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
              bitmap.eraseColor(Color.WHITE)
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              if (extraRotationDegrees % 360 != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(extraRotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                rotated
              } else {
                bitmap
              }
            }
          }
        }
      }.getOrNull()
    }
  }

  suspend fun renderAllPagesToFiles(
    pdfFile: File,
    outputDir: File,
    baseName: String,
    format: Bitmap.CompressFormat,
    scaleFactor: Float,
    quality: Int,
  ): List<File> = withContext(Dispatchers.IO) {
    outputDir.mkdirs()
    val results = mutableListOf<File>()
    val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
    renderMutex.withLock {
      ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
              val width = (page.width * scaleFactor).toInt().coerceIn(300, 2600)
              val height = (page.height * scaleFactor).toInt().coerceIn(300, 3600)
              val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
              bitmap.eraseColor(Color.WHITE)
              page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
              val outFile = File(outputDir, "${baseName}_page_${i + 1}.$ext")
              FileOutputStream(outFile).use { out ->
                bitmap.compress(format, quality, out)
              }
              bitmap.recycle()
              results.add(outFile)
            }
          }
        }
      }
    }
    results
  }

  /**
   * Unlocks a password-protected PDF into a private temporary file in cacheDir so PdfRenderer can
   * display its pages without storing the password anywhere.
   */
  suspend fun createTempUnlockedCopy(encryptedFile: File, password: String): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        unlockedCacheDir.mkdirs()
        PDDocument.load(encryptedFile, password).use { doc ->
          doc.isAllSecurityToBeRemoved = true
          val tempFile = File(unlockedCacheDir, "view_${System.currentTimeMillis()}.pdf")
          doc.save(tempFile)
          tempFile
        }
      }
    }
}
