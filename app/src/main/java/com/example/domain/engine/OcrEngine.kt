package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.R
import com.example.core.util.BitmapPatchUtils
import com.example.domain.model.OcrLanguageChoice
import com.example.domain.model.PdfTextBlock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class OcrPageOutput(
  val pageIndex: Int,
  val text: String,
)

class OcrEngine(
  private val context: Context,
  private val tessdataDir: File,
) {
  private val tessBasePath: String get() = tessdataDir.parentFile?.absolutePath ?: context.filesDir.absolutePath

  fun isTesseractModelAvailable(language: OcrLanguageChoice): Boolean {
    val file = File(tessdataDir, "${language.code}.traineddata")
    return file.exists() && file.length() > 1024L
  }

  fun isLanguageReady(language: OcrLanguageChoice): Boolean {
    return when (language) {
      // English is always available offline via ML Kit (and optionally Tesseract)
      OcrLanguageChoice.ENGLISH -> true
      OcrLanguageChoice.BANGLA -> isTesseractModelAvailable(OcrLanguageChoice.BANGLA)
    }
  }

  suspend fun importTrainedData(uri: Uri, targetLanguage: OcrLanguageChoice): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        tessdataDir.mkdirs()
        val dest = File(tessdataDir, "${targetLanguage.code}.traineddata")
        context.contentResolver.openInputStream(uri)?.use { input ->
          FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: error("Cannot read selected traineddata file")
        require(dest.length() > 1024L) { "Selected traineddata file appears empty or invalid" }
        dest
      }
    }

  suspend fun downloadTrainedData(language: OcrLanguageChoice): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        tessdataDir.mkdirs()
        val urlStr = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/${language.code}.traineddata"
        val dest = File(tessdataDir, "${language.code}.traineddata")
        val tmp = File(tessdataDir, "${language.code}.traineddata.tmp")
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
          connectTimeout = 15000
          readTimeout = 30000
          instanceFollowRedirects = true
        }
        conn.inputStream.use { input ->
          FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        require(tmp.length() > 10_000L) { "Downloaded model file is incomplete" }
        tmp.renameTo(dest)
        dest
      }
    }

  suspend fun recognizePdf(
    pdfFile: File,
    language: OcrLanguageChoice,
    onProgress: (Int, Int) -> Unit = { _, _ -> },
  ): Result<List<OcrPageOutput>> = withContext(Dispatchers.IO) {
    runCatching {
      if (language == OcrLanguageChoice.BANGLA && !isTesseractModelAvailable(OcrLanguageChoice.BANGLA)) {
        error("MISSING_MODEL_BEN")
      }
      val outputs = mutableListOf<OcrPageOutput>()
      ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          val total = renderer.pageCount
          for (i in 0 until total) {
            onProgress(i + 1, total)
            renderer.openPage(i).use { page ->
              val w = (page.width * 2f).toInt().coerceIn(800, 2200)
              val h = (page.height * (w.toFloat() / page.width.coerceAtLeast(1))).toInt().coerceAtLeast(1)
              val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
              bmp.eraseColor(Color.WHITE)
              page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              val text = recognizeBitmapInternal(bmp, language)
              bmp.recycle()
              outputs.add(OcrPageOutput(i, text))
            }
          }
        }
      }
      outputs
    }
  }

  suspend fun recognizeBitmap(bitmap: Bitmap, language: OcrLanguageChoice): Result<String> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (language == OcrLanguageChoice.BANGLA && !isTesseractModelAvailable(OcrLanguageChoice.BANGLA)) {
          error("MISSING_MODEL_BEN")
        }
        recognizeBitmapInternal(bitmap, language)
      }
    }

  private suspend fun recognizeBitmapInternal(bitmap: Bitmap, language: OcrLanguageChoice): String {
    if (language == OcrLanguageChoice.ENGLISH && !isTesseractModelAvailable(OcrLanguageChoice.ENGLISH)) {
      return recognizeWithMlKit(bitmap)
    }
    if (isTesseractModelAvailable(language)) {
      return recognizeWithTesseract(bitmap, language.code)
    }
    return recognizeWithMlKit(bitmap)
  }

  suspend fun detectPageTextBlocks(
    bitmap: Bitmap,
    pageIndex: Int,
    language: OcrLanguageChoice = OcrLanguageChoice.ENGLISH,
    pageHeightPt: Float = 842f,
  ): List<PdfTextBlock> = detectHybridTextBlocks(
    bitmap = bitmap,
    pageIndex = pageIndex,
    language = language,
    existingVectorBlocks = emptyList(),
    pageHeightPt = pageHeightPt,
  )

  suspend fun detectHybridTextBlocks(
    bitmap: Bitmap,
    pageIndex: Int,
    language: OcrLanguageChoice = OcrLanguageChoice.ENGLISH,
    existingVectorBlocks: List<PdfTextBlock> = emptyList(),
    pageHeightPt: Float = 842f,
  ): List<PdfTextBlock> = withContext(Dispatchers.IO) {
    val merged = existingVectorBlocks.toMutableList()
    val safePageH = pageHeightPt.coerceAtLeast(200f)

    fun overlapsAny(candidate: PdfTextBlock, list: List<PdfTextBlock>): Boolean {
      val candArea = ((candidate.rightNorm - candidate.leftNorm) * (candidate.bottomNorm - candidate.topNorm))
        .coerceAtLeast(1e-6f)
      for (existing in list) {
        val il = max(candidate.leftNorm, existing.leftNorm)
        val it = max(candidate.topNorm, existing.topNorm)
        val ir = min(candidate.rightNorm, existing.rightNorm)
        val ib = min(candidate.bottomNorm, existing.bottomNorm)
        if (ir > il && ib > it) {
          val interArea = (ir - il) * (ib - it)
          val existArea = ((existing.rightNorm - existing.leftNorm) * (existing.bottomNorm - existing.topNorm))
            .coerceAtLeast(1e-6f)
          if (interArea / min(candArea, existArea) >= 0.30f) {
            return true
          }
        }
      }
      return false
    }

    // 1. Run Bangla Tesseract if installed (provides pre-filled Bangla characters when model exists)
    if (isTesseractModelAvailable(OcrLanguageChoice.BANGLA)) {
      val bnBlocks = runCatching { detectBlocksWithTesseract(bitmap, pageIndex, OcrLanguageChoice.BANGLA.code, safePageH) }
        .getOrDefault(emptyList())
      for (blk in bnBlocks) {
        if (!overlapsAny(blk, merged)) merged.add(blk)
      }
    }

    // 2. Run ML Kit Latin OCR for scanned English PDFs or English image sections
    val mlKitBlocks = runCatching { detectBlocksWithMlKit(bitmap, pageIndex, safePageH) }
      .getOrDefault(emptyList())
    for (blk in mlKitBlocks) {
      if (!overlapsAny(blk, merged)) merged.add(blk)
    }

    // 3. Offline visual dark-ink line/phrase detector for Bangla vector curves, image glyphs, or mixed forms
    val visualBlocks = runCatching { BitmapPatchUtils.detectVisualTextLines(bitmap, pageIndex, safePageH) }
      .getOrDefault(emptyList())
    for (blk in visualBlocks) {
      if (!overlapsAny(blk, merged)) merged.add(blk)
    }

    merged
  }

  private suspend fun detectBlocksWithMlKit(
    bitmap: Bitmap,
    pageIndex: Int,
    pageHeightPt: Float,
  ): List<PdfTextBlock> =
    suspendCancellableCoroutine { cont ->
      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      val image = InputImage.fromBitmap(bitmap, 0)
      val bw = bitmap.width.coerceAtLeast(1).toFloat()
      val bh = bitmap.height.coerceAtLeast(1).toFloat()
      recognizer.process(image)
        .addOnSuccessListener { result ->
          recognizer.close()
          val blocks = mutableListOf<PdfTextBlock>()
          var seq = 0
          for (block in result.textBlocks) {
            for (line in block.lines) {
              val text = line.text.trim()
              val rect = line.boundingBox ?: continue
              if (text.isEmpty() || rect.width() <= 2 || rect.height() <= 2) continue
              val leftNorm = (rect.left / bw).coerceIn(0f, 0.98f)
              val topNorm = (rect.top / bh).coerceIn(0f, 0.98f)
              val rightNorm = (rect.right / bw).coerceIn(leftNorm + 0.015f, 1f)
              val bottomNorm = (rect.bottom / bh).coerceIn(topNorm + 0.01f, 1f)
              val estFontPt = ((rect.height() / bh) * pageHeightPt * 0.95f).coerceIn(7f, 40f)
              blocks.add(
                PdfTextBlock(
                  id = "ocr_${pageIndex}_${seq++}",
                  pageIndex = pageIndex,
                  text = text,
                  leftNorm = leftNorm,
                  topNorm = topNorm,
                  rightNorm = rightNorm,
                  bottomNorm = bottomNorm,
                  fontSizePt = estFontPt,
                )
              )
            }
          }
          cont.resume(blocks)
        }
        .addOnFailureListener { exc ->
          recognizer.close()
          cont.resumeWithException(exc)
        }
    }

  private fun detectBlocksWithTesseract(
    bitmap: Bitmap,
    pageIndex: Int,
    langCode: String,
    pageHeightPt: Float,
  ): List<PdfTextBlock> {
    val tess = TessBaseAPI()
    val bw = bitmap.width.coerceAtLeast(1).toFloat()
    val bh = bitmap.height.coerceAtLeast(1).toFloat()
    val blocks = mutableListOf<PdfTextBlock>()
    try {
      if (!tess.init(tessBasePath, langCode)) return emptyList()
      tess.setImage(bitmap)
      // Trigger layout & recognition pass
      tess.utF8Text
      val iter = tess.resultIterator ?: return emptyList()
      try {
        iter.begin()
        var seq = 0
        do {
          val lineText = iter.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)?.trim().orEmpty()
          val rect = iter.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
          if (lineText.isNotEmpty() && rect != null && rect.width() > 2 && rect.height() > 2) {
            val leftNorm = (rect.left / bw).coerceIn(0f, 0.98f)
            val topNorm = (rect.top / bh).coerceIn(0f, 0.98f)
            val rightNorm = (rect.right / bw).coerceIn(leftNorm + 0.015f, 1f)
            val bottomNorm = (rect.bottom / bh).coerceIn(topNorm + 0.01f, 1f)
            val estFontPt = ((rect.height() / bh) * pageHeightPt * 0.95f).coerceIn(7f, 40f)
            blocks.add(
              PdfTextBlock(
                id = "ocr_tess_${pageIndex}_${seq++}",
                pageIndex = pageIndex,
                text = lineText,
                leftNorm = leftNorm,
                topNorm = topNorm,
                rightNorm = rightNorm,
                bottomNorm = bottomNorm,
                fontSizePt = estFontPt,
                fontFamily = if (langCode == OcrLanguageChoice.BANGLA.code || lineText.any { it.code > 126 }) {
                  com.example.domain.model.EditorFont.BENGALI
                } else {
                  com.example.domain.model.EditorFont.HELVETICA
                },
              )
            )
          }
        } while (iter.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
      } finally {
        runCatching { iter.delete() }
      }
      return blocks
    } finally {
      runCatching { tess.recycle() }
    }
  }

  private suspend fun recognizeWithMlKit(bitmap: Bitmap): String =
    suspendCancellableCoroutine { cont ->
      val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
      val image = InputImage.fromBitmap(bitmap, 0)
      recognizer.process(image)
        .addOnSuccessListener { result ->
          recognizer.close()
          cont.resume(result.text.trim())
        }
        .addOnFailureListener { exc ->
          recognizer.close()
          cont.resumeWithException(exc)
        }
    }

  private fun recognizeWithTesseract(bitmap: Bitmap, langCode: String): String {
    val tess = TessBaseAPI()
    try {
      val ok = tess.init(tessBasePath, langCode)
      if (!ok) error("Failed to initialize Tesseract for $langCode")
      tess.setImage(bitmap)
      return tess.utF8Text?.trim().orEmpty()
    } finally {
      runCatching { tess.recycle() }
    }
  }

  /**
   * Builds a searchable PDF by embedding each rendered page plus an invisible OCR text layer
   * (RenderingMode.NEITHER) so the text can be searched and selected inside PDF viewers.
   */
  suspend fun createSearchablePdf(
    sourcePdf: File,
    ocrPages: List<OcrPageOutput>,
    outputFile: File,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val textMap = ocrPages.associateBy({ it.pageIndex }, { it.text })
      ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          PDDocument().use { outDoc ->
            val font = PDType1Font.HELVETICA

            for (i in 0 until renderer.pageCount) {
              renderer.openPage(i).use { page ->
                val ptW = page.width.toFloat().coerceAtLeast(200f)
                val ptH = page.height.toFloat().coerceAtLeast(200f)
                val bmpW = (ptW * 1.8f).toInt().coerceIn(600, 1800)
                val bmpH = (ptH * (bmpW / ptW)).toInt().coerceAtLeast(200)
                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val pdPage = PDPage(PDRectangle(ptW, ptH))
                outDoc.addPage(pdPage)
                val pdImg = JPEGFactory.createFromImage(outDoc, bmp, 0.88f)
                bmp.recycle()

                PDPageContentStream(outDoc, pdPage).use { cs ->
                  cs.drawImage(pdImg, 0f, 0f, ptW, ptH)
                  val pageText = textMap[i].orEmpty().trim()
                  if (pageText.isNotEmpty()) {
                    val lines = pageText.lines().filter { it.isNotBlank() }
                    val stepY = (ptH - 48f) / lines.size.coerceAtLeast(1)
                    cs.beginText()
                    cs.setRenderingMode(RenderingMode.NEITHER)
                    cs.setFont(font, 10f)
                    lines.forEachIndexed { idx, line ->
                      val yPos = (ptH - 24f - idx * stepY).coerceAtLeast(16f)
                      val safeLine = line.map { if (it.code in 32..126) it else ' ' }.joinToString("")
                      cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(24f, yPos))
                      cs.showText(safeLine)
                    }
                    cs.endText()
                  }
                }
              }
            }
            outDoc.save(outputFile)
            outputFile
          }
        }
      }
    }
  }
}
