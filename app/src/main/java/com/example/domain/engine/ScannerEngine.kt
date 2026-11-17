package com.example.domain.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.example.domain.model.ScannedPageItem
import com.example.domain.model.ScannerFilter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScannerEngine {

  suspend fun processScannedBitmap(item: ScannedPageItem, maxDimension: Int = 1800): Bitmap? =
    withContext(Dispatchers.Default) {
      val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(item.rawImagePath, opts)
      if (opts.outWidth <= 0 || opts.outHeight <= 0) return@withContext null

      var sample = 1
      while (opts.outWidth / sample > maxDimension || opts.outHeight / sample > maxDimension) {
        sample *= 2
      }
      val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
      var bmp = BitmapFactory.decodeFile(item.rawImagePath, decodeOpts) ?: return@withContext null

      // 1. Rotate if requested
      if (item.rotationDegrees % 360 != 0) {
        val m = Matrix().apply { postRotate(item.rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated !== bmp) bmp.recycle()
        bmp = rotated
      }

      // 2. Crop normalized edges
      val left = (item.cropLeft.coerceIn(0f, 0.85f) * bmp.width).toInt()
      val top = (item.cropTop.coerceIn(0f, 0.85f) * bmp.height).toInt()
      val right = (item.cropRight.coerceIn(item.cropLeft + 0.1f, 1f) * bmp.width).toInt().coerceAtMost(bmp.width)
      val bottom = (item.cropBottom.coerceIn(item.cropTop + 0.1f, 1f) * bmp.height).toInt().coerceAtMost(bmp.height)
      val cropW = (right - left).coerceAtLeast(20)
      val cropH = (bottom - top).coerceAtLeast(20)
      if (left > 0 || top > 0 || cropW < bmp.width || cropH < bmp.height) {
        val cropped = Bitmap.createBitmap(bmp, left, top, cropW, cropH)
        if (cropped !== bmp) bmp.recycle()
        bmp = cropped
      }

      // 3. Apply Brightness, Contrast, and Scanner Filter
      val outBmp = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(outBmp)
      val cm = ColorMatrix()

      when (item.filter) {
        ScannerFilter.ORIGINAL -> Unit
        ScannerFilter.GRAYSCALE -> cm.setSaturation(0f)
        ScannerFilter.BLACK_AND_WHITE -> {
          cm.setSaturation(0f)
          // High contrast crisp document B&W matrix
          val contrastBoost = ColorMatrix(
            floatArrayOf(
              2.6f, 0f, 0f, 0f, -190f,
              0f, 2.6f, 0f, 0f, -190f,
              0f, 0f, 2.6f, 0f, -190f,
              0f, 0f, 0f, 1f, 0f,
            )
          )
          cm.postConcat(contrastBoost)
        }
      }

      val c = item.contrast
      val b = item.brightness
      val translate = (1f - c) * 128f + b
      val bcMatrix = ColorMatrix(
        floatArrayOf(
          c, 0f, 0f, 0f, translate,
          0f, c, 0f, 0f, translate,
          0f, 0f, c, 0f, translate,
          0f, 0f, 0f, 1f, 0f,
        )
      )
      cm.postConcat(bcMatrix)

      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(cm)
      }
      canvas.drawBitmap(bmp, 0f, 0f, paint)
      bmp.recycle()
      outBmp
    }

  suspend fun exportScannedPagesToPdf(
    pages: List<ScannedPageItem>,
    outputFile: File,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      require(pages.isNotEmpty()) { "No scanned pages to export" }
      PDDocument().use { doc ->
        for (item in pages) {
          val processed = processScannedBitmap(item, maxDimension = 1800) ?: continue
          val a4 = PDRectangle.A4
          val pageRect = if (processed.width > processed.height) PDRectangle(a4.height, a4.width) else a4
          val page = PDPage(pageRect)
          doc.addPage(page)

          val margin = 16f
          val availW = pageRect.width - margin * 2
          val availH = pageRect.height - margin * 2
          val scale = min(availW / processed.width, availH / processed.height)
          val drawW = processed.width * scale
          val drawH = processed.height * scale
          val x = (pageRect.width - drawW) / 2f
          val y = (pageRect.height - drawH) / 2f

          val pdImg = JPEGFactory.createFromImage(doc, processed, 0.90f)
          PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(pdImg, x, y, drawW, drawH)
          }
          processed.recycle()
        }
        require(doc.numberOfPages > 0) { "Failed to render scanned pages" }
        doc.save(outputFile)
        outputFile
      }
    }
  }
}
