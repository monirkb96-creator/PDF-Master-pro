package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.R
import com.example.domain.model.CompressionLevel
import com.example.domain.model.CompressionResult
import com.example.domain.model.EditorFont
import com.example.domain.model.HeaderFooterConfig
import com.example.domain.model.OrganizerPageItem
import com.example.domain.model.PdfDocInfo
import com.example.domain.model.PdfEditAction
import com.example.domain.model.PdfTextBlock
import com.example.domain.model.ShapeKind
import com.example.domain.model.SplitMode
import com.example.domain.model.WatermarkPosition
import com.example.core.util.BitmapPatchUtils
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfManipulationEngine(private val context: Context) {

  suspend fun getDocumentInfo(file: File, password: String? = null): Result<PdfDocInfo> =
    withContext(Dispatchers.IO) {
      runCatching {
        val doc = if (password != null) PDDocument.load(file, password) else PDDocument.load(file)
        doc.use {
          val info = it.documentInformation
          PdfDocInfo(
            fileName = file.name,
            sizeBytes = file.length(),
            pageCount = it.numberOfPages,
            lastModified = file.lastModified(),
            isEncrypted = it.isEncrypted,
            title = info?.title?.takeIf { s -> s.isNotBlank() },
            author = info?.author?.takeIf { s -> s.isNotBlank() },
            creator = info?.creator?.takeIf { s -> s.isNotBlank() },
          )
        }
      }
    }

  suspend fun searchTextPages(file: File, query: String): List<Int> = withContext(Dispatchers.IO) {
    val q = query.trim()
    if (q.isEmpty()) return@withContext emptyList()
    runCatching {
      PDDocument.load(file).use { doc ->
        val stripper = PDFTextStripper()
        val matched = mutableListOf<Int>()
        for (pageNum in 1..doc.numberOfPages) {
          stripper.startPage = pageNum
          stripper.endPage = pageNum
          val text = stripper.getText(doc)
          if (text.contains(q, ignoreCase = true)) {
            matched.add(pageNum - 1)
          }
        }
        matched
      }
    }.getOrDefault(emptyList())
  }

  suspend fun extractTextByPage(file: File): List<String> = withContext(Dispatchers.IO) {
    runCatching {
      PDDocument.load(file).use { doc ->
        val stripper = PDFTextStripper()
        (1..doc.numberOfPages).map { pageNum ->
          stripper.startPage = pageNum
          stripper.endPage = pageNum
          stripper.getText(doc).trim()
        }
      }
    }.getOrDefault(emptyList())
  }

  suspend fun extractPageTextBlocks(file: File, pageIndex: Int): List<PdfTextBlock> =
    withContext(Dispatchers.IO) {
      runCatching {
        PDDocument.load(file).use { doc ->
          if (pageIndex !in 0 until doc.numberOfPages) return@use emptyList()
          val blocks = mutableListOf<PdfTextBlock>()
          var seq = 0
          val stripper = object : PDFTextStripper() {
            override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
              if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return
              val groups = mutableListOf<MutableList<TextPosition>>()
              var current = mutableListOf<TextPosition>()
              for (tp in textPositions) {
                if (current.isEmpty()) {
                  current.add(tp)
                } else {
                  val prev = current.last()
                  val prevFont = prev.fontSizeInPt.coerceAtLeast(8f)
                  val gapX = tp.xDirAdj - (prev.xDirAdj + prev.widthDirAdj)
                  val diffY = abs(tp.yDirAdj - prev.yDirAdj)
                  if (diffY > 4.5f || gapX > max(14f, prevFont * 2.2f)) {
                    groups.add(current)
                    current = mutableListOf(tp)
                  } else {
                    current.add(tp)
                  }
                }
              }
              if (current.isNotEmpty()) groups.add(current)

              for (grp in groups) {
                val sb = StringBuilder()
                for (idx in grp.indices) {
                  val tp = grp[idx]
                  if (idx > 0) {
                    val prev = grp[idx - 1]
                    val gap = tp.xDirAdj - (prev.xDirAdj + prev.widthDirAdj)
                    val spaceThreshold = (prev.fontSizeInPt.coerceAtLeast(8f)) * 0.22f
                    if (gap > spaceThreshold && !sb.endsWith(" ") && !tp.unicode.startsWith(" ")) {
                      sb.append(' ')
                    }
                  }
                  sb.append(tp.unicode)
                }
                val blockText = sb.toString().trim()
                if (blockText.isEmpty()) continue
                val pageW = grp.first().pageWidth.coerceAtLeast(1f)
                val pageH = grp.first().pageHeight.coerceAtLeast(1f)
                val minX = grp.minOf { it.xDirAdj }
                val maxX = grp.maxOf { it.xDirAdj + it.widthDirAdj }
                val baselineY = grp.map { it.yDirAdj }.average().toFloat()
                val fontSize = grp.map { it.fontSizeInPt }.filter { it > 0f }.average().toFloat()
                  .takeIf { !it.isNaN() } ?: 12f
                val maxGlyphH = grp.maxOf { it.heightDir.coerceAtLeast(fontSize * 0.72f) }

                val topNorm = ((baselineY - maxGlyphH * 1.08f) / pageH).coerceIn(0f, 0.985f)
                val bottomNorm = ((baselineY + fontSize * 0.28f) / pageH).coerceIn(topNorm + 0.01f, 1f)
                val leftNorm = (minX / pageW).coerceIn(0f, 0.98f)
                val rightNorm = (maxX / pageW).coerceIn(leftNorm + 0.015f, 1f)

                blocks.add(
                  PdfTextBlock(
                    id = "blk_${pageIndex}_${seq++}",
                    pageIndex = pageIndex,
                    text = blockText,
                    leftNorm = leftNorm,
                    topNorm = topNorm,
                    rightNorm = rightNorm,
                    bottomNorm = bottomNorm,
                    fontSizePt = fontSize.coerceIn(7f, 48f),
                  )
                )
              }
            }
          }
          stripper.sortByPosition = true
          stripper.startPage = pageIndex + 1
          stripper.endPage = pageIndex + 1
          stripper.getText(doc)
          blocks
        }
      }.getOrDefault(emptyList())
    }

  suspend fun applyEdits(
    sourceFile: File,
    outputFile: File,
    actions: List<PdfEditAction>,
    headerFooter: HeaderFooterConfig? = null,
    pageRotations: Map<Int, Int> = emptyMap(),
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      PDDocument.load(sourceFile).use { doc ->
        fun loadFont(fontChoice: EditorFont): PDFont {
          return when (fontChoice) {
            EditorFont.HELVETICA -> PDType1Font.HELVETICA
            EditorFont.TIMES -> PDType1Font.TIMES_ROMAN
            EditorFont.COURIER -> PDType1Font.COURIER
            EditorFont.BENGALI -> PDType1Font.HELVETICA
          }
        }

        // Apply per-page rotation updates if any
        for ((pageIdx, extraRot) in pageRotations) {
          if (pageIdx in 0 until doc.numberOfPages && extraRot % 360 != 0) {
            val page = doc.getPage(pageIdx)
            page.rotation = (page.rotation + extraRot) % 360
          }
        }

        val grouped = actions.groupBy { it.pageIndex }
        for ((pageIdx, pageActions) in grouped) {
          if (pageIdx !in 0 until doc.numberOfPages) continue
          val page = doc.getPage(pageIdx)
          val mediaBox = page.cropBox ?: page.mediaBox
          val pageW = mediaBox.width
          val pageH = mediaBox.height

          val preserveReplaceActions = pageActions
            .filterIsInstance<PdfEditAction.RedactAndReplace>()
            .filter { it.preserveBackground }
          val vectorCleanedIds = if (preserveReplaceActions.isNotEmpty()) {
            removeVectorTextInBoxes(doc, pageIdx, page, preserveReplaceActions)
          } else {
            emptySet()
          }

          val needsScannedBgPatch = preserveReplaceActions.any { it.id !in vectorCleanedIds }
          val scannedPageBmp = if (needsScannedBgPatch) {
            renderPageBitmapBestEffort(sourceFile, pageIdx)
          } else {
            null
          }

          try {
            PDPageContentStream(
              doc,
              page,
              PDPageContentStream.AppendMode.APPEND,
              true,
              true,
            ).use { cs ->
              for (action in pageActions) {
                when (action) {
                  is PdfEditAction.AddText -> {
                    val pdfX = mediaBox.lowerLeftX + action.xNorm.coerceIn(0f, 0.95f) * pageW
                    val pdfY = mediaBox.upperRightY - action.yNorm.coerceIn(0.02f, 0.98f) * pageH
                    if (action.text.any { it.code > 126 }) {
                      drawShapedUnicodeText(doc, cs, action.text, pdfX, pdfY, action.fontSizeSp, action.colorArgb)
                    } else {
                      val pdfFont = loadFont(action.font)
                      val (r, g, b) = rgbComponents(action.colorArgb)
                      cs.beginText()
                      cs.setFont(pdfFont, action.fontSizeSp)
                      cs.setNonStrokingColor(r, g, b)
                      cs.newLineAtOffset(pdfX, pdfY)
                      cs.showText(safePdfText(action.text, pdfFont))
                      cs.endText()
                    }
                  }

                  is PdfEditAction.RedactAndReplace -> {
                    val left = mediaBox.lowerLeftX + min(action.leftNorm, action.rightNorm) * pageW
                    val right = mediaBox.lowerLeftX + max(action.leftNorm, action.rightNorm) * pageW
                    val top = mediaBox.upperRightY - min(action.topNorm, action.bottomNorm) * pageH
                    val bottom = mediaBox.upperRightY - max(action.topNorm, action.bottomNorm) * pageH
                    val boxW = (right - left).coerceAtLeast(12f)
                    val boxH = (top - bottom).coerceAtLeast(12f)

                    if (action.preserveBackground && action.id in vectorCleanedIds) {
                      // Vector text already stripped from content stream; background watermark/lines stay untouched.
                    } else if (action.preserveBackground && scannedPageBmp != null) {
                      val patch = BitmapPatchUtils.createCleanBackgroundPatch(
                        scannedPageBmp,
                        action.leftNorm,
                        action.topNorm,
                        action.rightNorm,
                        action.bottomNorm,
                      )
                      if (patch != null) {
                        val pdPatch = LosslessFactory.createFromImage(doc, patch.bitmap)
                        patch.bitmap.recycle()
                        val pLeft = mediaBox.lowerLeftX + patch.leftNorm * pageW
                        val pW = ((patch.rightNorm - patch.leftNorm) * pageW).coerceAtLeast(4f)
                        val pH = ((patch.bottomNorm - patch.topNorm) * pageH).coerceAtLeast(4f)
                        val pBottom = mediaBox.upperRightY - patch.bottomNorm * pageH
                        cs.drawImage(pdPatch, pLeft, pBottom, pW, pH)
                      } else {
                        val (bgR, bgG, bgB) = rgbComponents(action.backgroundArgb)
                        cs.setNonStrokingColor(bgR, bgG, bgB)
                        cs.addRect(left - 2f, bottom - 2f, boxW + 4f, boxH + 4f)
                        cs.fill()
                      }
                    } else {
                      val (bgR, bgG, bgB) = rgbComponents(action.backgroundArgb)
                      cs.setNonStrokingColor(bgR, bgG, bgB)
                      cs.addRect(left - 2f, bottom - 2f, boxW + 4f, boxH + 4f)
                      cs.fill()
                    }

                    if (action.replacementText.isNotBlank()) {
                      val lines = action.replacementText.replace("\r", "").split('\n')
                      val lineStep = (action.fontSizeSp * 1.25f).coerceAtLeast(10f)
                      val firstBaselineY = if (lines.size <= 1) {
                        bottom + (boxH * 0.22f)
                      } else {
                        (top - action.fontSizeSp * 0.92f).coerceAtLeast(bottom + lineStep * (lines.size - 1))
                      }
                      lines.forEachIndexed { idx, line ->
                        if (line.isBlank()) return@forEachIndexed
                        val lineY = firstBaselineY - idx * lineStep
                        if (line.any { it.code > 126 }) {
                          drawShapedUnicodeText(doc, cs, line, left, lineY, action.fontSizeSp, action.textColorArgb)
                        } else {
                          val pdfFont = loadFont(action.font)
                          val (r, g, b) = rgbComponents(action.textColorArgb)
                          cs.beginText()
                          cs.setFont(pdfFont, action.fontSizeSp)
                          cs.setNonStrokingColor(r, g, b)
                          cs.newLineAtOffset(left, lineY)
                          cs.showText(safePdfText(line, pdfFont))
                          cs.endText()
                        }
                      }
                    }
                  }

                is PdfEditAction.AddImage -> {
                  val imgFile = File(action.imagePath)
                  if (imgFile.exists()) {
                    val pdImage = PDImageXObject.createFromFileByContent(imgFile, doc)
                    val imgX = mediaBox.lowerLeftX + action.leftNorm * pageW
                    val imgW = action.widthNorm * pageW
                    val imgH = action.heightNorm * pageH
                    val imgY = mediaBox.upperRightY - (action.topNorm * pageH) - imgH
                    cs.drawImage(pdImage, imgX, imgY, imgW, imgH)
                  }
                }

                is PdfEditAction.AddShape -> {
                  val x1 = mediaBox.lowerLeftX + action.leftNorm * pageW
                  val x2 = mediaBox.lowerLeftX + action.rightNorm * pageW
                  val y1 = mediaBox.upperRightY - action.topNorm * pageH
                  val y2 = mediaBox.upperRightY - action.bottomNorm * pageH
                  val (r, g, b) = rgbComponents(action.strokeColorArgb)
                  cs.setStrokingColor(r, g, b)
                  cs.setLineWidth(action.strokeWidthPt)
                  when (action.shapeKind) {
                    ShapeKind.RECTANGLE -> {
                      cs.addRect(min(x1, x2), min(y1, y2), kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1))
                      cs.stroke()
                    }
                    ShapeKind.CIRCLE -> {
                      val cx = (x1 + x2) / 2f
                      val cy = (y1 + y2) / 2f
                      val rx = kotlin.math.abs(x2 - x1) / 2f
                      val ry = kotlin.math.abs(y2 - y1) / 2f
                      drawEllipse(cs, cx, cy, rx.coerceAtLeast(4f), ry.coerceAtLeast(4f))
                      cs.stroke()
                    }
                    ShapeKind.LINE -> {
                      cs.moveTo(x1, y1)
                      cs.lineTo(x2, y2)
                      cs.stroke()
                    }
                    ShapeKind.ARROW -> {
                      cs.moveTo(x1, y1)
                      cs.lineTo(x2, y2)
                      val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                      val headLen = 12.0
                      val a1 = angle + Math.toRadians(150.0)
                      val a2 = angle - Math.toRadians(150.0)
                      cs.moveTo(x2, y2)
                      cs.lineTo((x2 + headLen * cos(a1)).toFloat(), (y2 + headLen * sin(a1)).toFloat())
                      cs.moveTo(x2, y2)
                      cs.lineTo((x2 + headLen * cos(a2)).toFloat(), (y2 + headLen * sin(a2)).toFloat())
                      cs.stroke()
                    }
                  }
                }

                is PdfEditAction.AddPenStroke -> {
                  if (action.pointsNorm.size >= 2) {
                    val (r, g, b) = rgbComponents(action.colorArgb)
                    cs.setStrokingColor(r, g, b)
                    cs.setLineWidth(action.strokeWidthPt)
                    cs.setLineCapStyle(1)
                    cs.setLineJoinStyle(1)
                    val first = action.pointsNorm.first()
                    cs.moveTo(mediaBox.lowerLeftX + first.x * pageW, mediaBox.upperRightY - first.y * pageH)
                    for (pt in action.pointsNorm.drop(1)) {
                      cs.lineTo(mediaBox.lowerLeftX + pt.x * pageW, mediaBox.upperRightY - pt.y * pageH)
                    }
                    cs.stroke()
                  }
                }

                is PdfEditAction.AddHighlight -> {
                  val left = mediaBox.lowerLeftX + min(action.leftNorm, action.rightNorm) * pageW
                  val right = mediaBox.lowerLeftX + max(action.leftNorm, action.rightNorm) * pageW
                  val top = mediaBox.upperRightY - min(action.topNorm, action.bottomNorm) * pageH
                  val bottom = mediaBox.upperRightY - max(action.topNorm, action.bottomNorm) * pageH
                  val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f }
                  cs.saveGraphicsState()
                  cs.setGraphicsStateParameters(gs)
                  val (r, g, b) = rgbComponents(action.colorArgb)
                  cs.setNonStrokingColor(r, g, b)
                  cs.addRect(left, bottom, (right - left).coerceAtLeast(10f), (top - bottom).coerceAtLeast(10f))
                  cs.fill()
                  cs.restoreGraphicsState()
                }

                is PdfEditAction.AddUnderline -> {
                  val x1 = mediaBox.lowerLeftX + action.startXNorm * pageW
                  val x2 = mediaBox.lowerLeftX + action.endXNorm * pageW
                  val y = mediaBox.upperRightY - action.yNorm * pageH
                  val (r, g, b) = rgbComponents(action.colorArgb)
                  cs.setStrokingColor(r, g, b)
                  cs.setLineWidth(2f)
                  cs.moveTo(x1, y)
                  cs.lineTo(x2, y)
                  cs.stroke()
                }

                is PdfEditAction.AddComment -> {
                  val pdfX = mediaBox.lowerLeftX + action.xNorm * pageW
                  val pdfY = mediaBox.upperRightY - action.yNorm * pageH
                  val note = PDAnnotationText().apply {
                    contents = action.comment
                    titlePopup = action.author
                    rectangle = PDRectangle(pdfX - 10f, pdfY - 10f, 20f, 20f)
                    name = PDAnnotationText.NAME_COMMENT
                  }
                  page.annotations.add(note)
                  // Also draw a crisp callout badge so it is visibly rendered in standard viewers
                  val (r, g, b) = rgbComponents(action.colorArgb)
                  cs.setNonStrokingColor(r, g, b)
                  cs.addRect(pdfX - 8f, pdfY - 8f, 16f, 16f)
                  cs.fill()
                }
              }
            }
            }
          } finally {
            scannedPageBmp?.recycle()
          }
        }

        // Apply Headers / Footers / Page numbers across all pages if configured
        if (headerFooter != null &&
          (headerFooter.headerText.isNotBlank() ||
            headerFooter.footerText.isNotBlank() ||
            headerFooter.includePageNumbers)
        ) {
          for (i in 0 until doc.numberOfPages) {
            val page = doc.getPage(i)
            val box = page.mediaBox
            PDPageContentStream(
              doc,
              page,
              PDPageContentStream.AppendMode.APPEND,
              true,
              true,
            ).use { cs ->
              if (headerFooter.headerText.isNotBlank()) {
                val font = loadFont(EditorFont.HELVETICA)
                if (headerFooter.headerText.any { it.code > 126 }) {
                  drawShapedUnicodeText(doc, cs, headerFooter.headerText, box.lowerLeftX + 36f, box.upperRightY - 28f, 10f, Color.rgb(71, 85, 105))
                } else {
                  cs.beginText()
                  cs.setFont(font, 10f)
                  cs.setNonStrokingColor(71, 85, 105)
                  cs.newLineAtOffset(box.lowerLeftX + 36f, box.upperRightY - 28f)
                  cs.showText(safePdfText(headerFooter.headerText, font))
                  cs.endText()
                }
              }
              val footerStr = buildString {
                if (headerFooter.footerText.isNotBlank()) append(headerFooter.footerText)
                if (headerFooter.includePageNumbers) {
                  if (isNotEmpty()) append("   |   ")
                  append("${headerFooter.startNumber + i} / ${doc.numberOfPages}")
                }
              }
              if (footerStr.isNotBlank()) {
                val font = loadFont(EditorFont.HELVETICA)
                if (footerStr.any { it.code > 126 }) {
                  drawShapedUnicodeText(doc, cs, footerStr, box.lowerLeftX + 36f, box.lowerLeftY + 20f, 10f, Color.rgb(71, 85, 105))
                } else {
                  cs.beginText()
                  cs.setFont(font, 10f)
                  cs.setNonStrokingColor(71, 85, 105)
                  cs.newLineAtOffset(box.lowerLeftX + 36f, box.lowerLeftY + 20f)
                  cs.showText(safePdfText(footerStr, font))
                  cs.endText()
                }
              }
            }
          }
        }

        doc.save(outputFile)
        outputFile
      }
    }
  }

  suspend fun mergePdfs(sourceFiles: List<File>, outputFile: File): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(sourceFiles.size >= 2) { "At least 2 PDF files are required" }
        val merger = PDFMergerUtility()
        merger.destinationFileName = outputFile.absolutePath
        sourceFiles.forEach { merger.addSource(it) }
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        outputFile
      }
    }

  suspend fun splitPdf(
    sourceFile: File,
    mode: SplitMode,
    selectedPagesZeroBased: Set<Int>,
    rangeStartOneBased: Int,
    rangeEndOneBased: Int,
    chunkSize: Int,
    outputFileFactory: (suffix: String) -> File,
  ): Result<List<File>> = withContext(Dispatchers.IO) {
    runCatching {
      PDDocument.load(sourceFile).use { sourceDoc ->
        val total = sourceDoc.numberOfPages
        val created = mutableListOf<File>()
        when (mode) {
          SplitMode.EVERY_PAGE -> {
            for (i in 0 until total) {
              val out = outputFileFactory("page_${i + 1}")
              PDDocument().use { single ->
                single.importPage(sourceDoc.getPage(i))
                single.save(out)
              }
              created.add(out)
            }
          }
          SplitMode.SELECTED_PAGES -> {
            val indices = selectedPagesZeroBased.filter { it in 0 until total }.sorted()
            require(indices.isNotEmpty()) { "Select at least one page" }
            val out = outputFileFactory("extracted")
            PDDocument().use { sub ->
              indices.forEach { sub.importPage(sourceDoc.getPage(it)) }
              sub.save(out)
            }
            created.add(out)
          }
          SplitMode.PAGE_RANGE -> {
            val start = (rangeStartOneBased - 1).coerceIn(0, total - 1)
            val end = (rangeEndOneBased - 1).coerceIn(start, total - 1)
            val out = outputFileFactory("pages_${start + 1}_to_${end + 1}")
            PDDocument().use { sub ->
              for (i in start..end) {
                sub.importPage(sourceDoc.getPage(i))
              }
              sub.save(out)
            }
            created.add(out)
          }
          SplitMode.CHUNKS -> {
            val size = chunkSize.coerceAtLeast(1)
            var part = 1
            var i = 0
            while (i < total) {
              val end = min(i + size, total)
              val out = outputFileFactory("part_$part")
              PDDocument().use { sub ->
                for (p in i until end) {
                  sub.importPage(sourceDoc.getPage(p))
                }
                sub.save(out)
              }
              created.add(out)
              part++
              i = end
            }
          }
        }
        created
      }
    }
  }

  suspend fun compressPdf(
    sourceFile: File,
    outputFile: File,
    level: CompressionLevel,
  ): Result<CompressionResult> = withContext(Dispatchers.IO) {
    runCatching {
      val originalBytes = sourceFile.length()
      // Strategy 1: Re-compress embedded raster images inside PDFBox while preserving text/vectors
      val tempImagesOptimized = File(outputFile.parentFile, "tmp_opt_${System.currentTimeMillis()}.pdf")
      PDDocument.load(sourceFile).use { doc ->
        doc.documentInformation = com.tom_roush.pdfbox.pdmodel.PDDocumentInformation()
        for (page in doc.pages) {
          val resources = page.resources ?: continue
          for (name in resources.xObjectNames) {
            val xObj = runCatching { resources.getXObject(name) }.getOrNull()
            if (xObj is PDImageXObject) {
              val bmp = runCatching { xObj.image }.getOrNull() ?: continue
              val scaled = scaleBitmapIfLarger(bmp, level.maxDimPx)
              val compressedImg = JPEGFactory.createFromImage(doc, scaled, level.jpegQuality / 100f)
              resources.put(name, compressedImg)
              if (scaled !== bmp) scaled.recycle()
              bmp.recycle()
            }
          }
        }
        doc.save(tempImagesOptimized)
      }

      // If high compression didn't reduce size (e.g. heavy vector/uncompressed content), try rasterizing pages at target DPI
      val bestCandidate = if (level == CompressionLevel.HIGH && tempImagesOptimized.length() >= originalBytes) {
        val rasterCandidate = File(outputFile.parentFile, "tmp_rast_${System.currentTimeMillis()}.pdf")
        rasterizePdfCompressed(sourceFile, rasterCandidate, level)
        if (rasterCandidate.length() < tempImagesOptimized.length()) {
          tempImagesOptimized.delete()
          rasterCandidate
        } else {
          rasterCandidate.delete()
          tempImagesOptimized
        }
      } else {
        tempImagesOptimized
      }

      bestCandidate.copyTo(outputFile, overwrite = true)
      bestCandidate.delete()
      CompressionResult(
        outputFile = outputFile,
        originalBytes = originalBytes,
        compressedBytes = outputFile.length(),
      )
    }
  }

  private fun rasterizePdfCompressed(sourceFile: File, destFile: File, level: CompressionLevel) {
    val renderedWithFramework = runCatching {
      ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          PDDocument().use { outDoc ->
            for (i in 0 until renderer.pageCount) {
              renderer.openPage(i).use { page ->
                val ptW = page.width.toFloat().coerceAtLeast(100f)
                val ptH = page.height.toFloat().coerceAtLeast(100f)
                val bmpW = (ptW * level.quality.renderScale).toInt().coerceIn(300, level.maxDimPx)
                val bmpH = (ptH * (bmpW / ptW)).toInt().coerceAtLeast(100)
                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val pdPage = PDPage(PDRectangle(ptW, ptH))
                outDoc.addPage(pdPage)
                val pdImg = JPEGFactory.createFromImage(outDoc, bmp, level.jpegQuality / 100f)
                PDPageContentStream(outDoc, pdPage).use { cs ->
                  cs.drawImage(pdImg, 0f, 0f, ptW, ptH)
                }
                bmp.recycle()
              }
            }
            outDoc.save(destFile)
          }
        }
      }
      true
    }.getOrDefault(false)

    if (!renderedWithFramework) {
      runCatching {
        PDDocument.load(sourceFile).use { srcDoc ->
          val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(srcDoc)
          PDDocument().use { outDoc ->
            val dpi = 72f * level.quality.renderScale
            for (i in 0 until srcDoc.numberOfPages) {
              val srcPage = srcDoc.getPage(i)
              val box = srcPage.mediaBox
              val bmp = renderer.renderImageWithDPI(i, dpi)
              val pdPage = PDPage(box)
              outDoc.addPage(pdPage)
              val pdImg = JPEGFactory.createFromImage(outDoc, bmp, level.jpegQuality / 100f)
              PDPageContentStream(outDoc, pdPage).use { cs ->
                cs.drawImage(pdImg, 0f, 0f, box.width, box.height)
              }
              bmp.recycle()
            }
            outDoc.save(destFile)
          }
        }
      }
    }
  }

  suspend fun imagesToPdf(imageFiles: List<File>, outputFile: File): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        require(imageFiles.isNotEmpty()) { "No images selected" }
        PDDocument().use { doc ->
          for (imgFile in imageFiles) {
            val bmp = BitmapFactory.decodeFile(imgFile.absolutePath) ?: continue
            val a4 = PDRectangle.A4
            val landscape = bmp.width > bmp.height
            val pageRect = if (landscape) PDRectangle(a4.height, a4.width) else a4
            val page = PDPage(pageRect)
            doc.addPage(page)

            val margin = 24f
            val availW = pageRect.width - margin * 2
            val availH = pageRect.height - margin * 2
            val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
            val drawW = bmp.width * scale
            val drawH = bmp.height * scale
            val x = (pageRect.width - drawW) / 2f
            val y = (pageRect.height - drawH) / 2f

            val pdImage = JPEGFactory.createFromImage(doc, bmp, 0.90f)
            PDPageContentStream(doc, page).use { cs ->
              cs.drawImage(pdImage, x, y, drawW, drawH)
            }
            bmp.recycle()
          }
          require(doc.numberOfPages > 0) { "Failed to decode selected images" }
          doc.save(outputFile)
          outputFile
        }
      }
    }

  suspend fun extractEmbeddedImages(
    sourceFile: File,
    outputDir: File,
    baseName: String,
  ): Result<List<File>> = withContext(Dispatchers.IO) {
    runCatching {
      outputDir.mkdirs()
      val extracted = mutableListOf<File>()
      PDDocument.load(sourceFile).use { doc ->
        var index = 1
        for (page in doc.pages) {
          val resources = page.resources ?: continue
          for (name in resources.xObjectNames) {
            val xObj = runCatching { resources.getXObject(name) }.getOrNull()
            if (xObj is PDImageXObject) {
              val bmp = runCatching { xObj.image }.getOrNull() ?: continue
              val outFile = File(outputDir, "${baseName}_img_${index}.png")
              FileOutputStream(outFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
              }
              bmp.recycle()
              extracted.add(outFile)
              index++
            }
          }
        }
      }
      extracted
    }
  }

  suspend fun applySignature(
    sourceFile: File,
    outputFile: File,
    signatureBitmap: Bitmap,
    selectedPagesZeroBased: Set<Int>,
    xNorm: Float,
    yNorm: Float,
    widthNorm: Float,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      PDDocument.load(sourceFile).use { doc ->
        val sigImg = LosslessFactory.createFromImage(doc, signatureBitmap)
        val aspect = signatureBitmap.height.toFloat() / signatureBitmap.width.coerceAtLeast(1)
        val targets = if (selectedPagesZeroBased.isEmpty()) {
          (0 until doc.numberOfPages).toSet()
        } else {
          selectedPagesZeroBased
        }
        for (pageIdx in targets) {
          if (pageIdx !in 0 until doc.numberOfPages) continue
          val page = doc.getPage(pageIdx)
          val box = page.mediaBox
          val drawW = (widthNorm * box.width).coerceIn(40f, box.width * 0.85f)
          val drawH = (drawW * aspect).coerceIn(20f, box.height * 0.5f)
          val drawX = (box.lowerLeftX + xNorm * box.width).coerceIn(box.lowerLeftX, box.upperRightX - drawW)
          val drawY = (box.upperRightY - yNorm * box.height - drawH).coerceIn(box.lowerLeftY, box.upperRightY - drawH)
          PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.drawImage(sigImg, drawX, drawY, drawW, drawH)
          }
        }
        doc.save(outputFile)
        outputFile
      }
    }
  }

  suspend fun applyWatermark(
    sourceFile: File,
    outputFile: File,
    textWatermark: String?,
    imageWatermarkFile: File?,
    opacity: Float,
    sizeScale: Float,
    rotationDeg: Float,
    position: WatermarkPosition,
    selectedPagesZeroBased: Set<Int>,
    colorArgb: Int,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      PDDocument.load(sourceFile).use { doc ->
        val pdImage = imageWatermarkFile?.takeIf { it.exists() }?.let {
          PDImageXObject.createFromFileByContent(it, doc)
        }
        val text = textWatermark?.trim().orEmpty()
        val font: PDFont = PDType1Font.HELVETICA_BOLD
        val targets = if (selectedPagesZeroBased.isEmpty()) {
          (0 until doc.numberOfPages).toSet()
        } else {
          selectedPagesZeroBased
        }

        for (pageIdx in targets) {
          if (pageIdx !in 0 until doc.numberOfPages) continue
          val page = doc.getPage(pageIdx)
          val box = page.mediaBox
          val cx = box.lowerLeftX + box.width / 2f
          val cy = when (position) {
            WatermarkPosition.TOP -> box.upperRightY - box.height * 0.16f
            WatermarkPosition.BOTTOM -> box.lowerLeftY + box.height * 0.16f
            WatermarkPosition.CENTER, WatermarkPosition.DIAGONAL -> box.lowerLeftY + box.height / 2f
          }
          val angleRad = Math.toRadians(
            if (position == WatermarkPosition.DIAGONAL && rotationDeg == 0f) 45.0 else rotationDeg.toDouble()
          )

          PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            val gs = PDExtendedGraphicsState().apply {
              nonStrokingAlphaConstant = opacity.coerceIn(0.05f, 1f)
              strokingAlphaConstant = opacity.coerceIn(0.05f, 1f)
            }
            cs.saveGraphicsState()
            cs.setGraphicsStateParameters(gs)

            if (pdImage != null) {
              val w = box.width * 0.45f * sizeScale.coerceIn(0.3f, 1.8f)
              val h = w * (pdImage.height.toFloat() / pdImage.width.coerceAtLeast(1))
              val matrix = Matrix.getRotateInstance(angleRad, cx, cy)
              cs.transform(matrix)
              cs.drawImage(pdImage, -w / 2f, -h / 2f, w, h)
            } else if (text.isNotBlank()) {
              val fontSize = 42f * sizeScale.coerceIn(0.4f, 2.2f)
              val safeText = safePdfText(text, font)
              val textWidth = runCatching { font.getStringWidth(safeText) / 1000f * fontSize }.getOrDefault(120f)
              val (r, g, b) = rgbComponents(colorArgb)
              cs.beginText()
              cs.setFont(font, fontSize)
              cs.setNonStrokingColor(r, g, b)
              val matrix = Matrix.getRotateInstance(angleRad, cx, cy)
              matrix.translate(-textWidth / 2f, -fontSize / 3f)
              cs.setTextMatrix(matrix)
              cs.showText(safeText)
              cs.endText()
            }

            cs.restoreGraphicsState()
          }
        }
        doc.save(outputFile)
        outputFile
      }
    }
  }

  suspend fun protectPdf(
    sourceFile: File,
    outputFile: File,
    userPassword: String,
    ownerPassword: String,
    allowPrinting: Boolean,
    allowCopying: Boolean,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      require(userPassword.isNotBlank()) { "Password cannot be empty" }
      PDDocument.load(sourceFile).use { doc ->
        val ap = AccessPermission().apply {
          setCanPrint(allowPrinting)
          setCanExtractContent(allowCopying)
          setCanModify(false)
        }
        val effectiveOwner = ownerPassword.ifBlank { userPassword }
        val policy = StandardProtectionPolicy(effectiveOwner, userPassword, ap).apply {
          encryptionKeyLength = 128
          permissions = ap
        }
        doc.protect(policy)
        doc.save(outputFile)
        outputFile
      }
    }
  }

  suspend fun unlockPdf(
    sourceFile: File,
    outputFile: File,
    password: String,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      PDDocument.load(sourceFile, password).use { doc ->
        require(doc.isEncrypted) { "Document is not encrypted" }
        doc.isAllSecurityToBeRemoved = true
        doc.save(outputFile)
        outputFile
      }
    }
  }

  suspend fun commitPageOrganization(
    items: List<OrganizerPageItem>,
    outputFile: File,
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      require(items.isNotEmpty()) { "At least one page is required" }
      val openedDocs = mutableMapOf<String, PDDocument>()
      try {
        PDDocument().use { targetDoc ->
          for (item in items) {
            if (item.isBlank || item.sourcePageIndex < 0) {
              val blankPage = PDPage(PDRectangle.A4)
              if (item.rotationDegrees % 360 != 0) blankPage.rotation = item.rotationDegrees % 360
              targetDoc.addPage(blankPage)
            } else {
              val srcDoc = openedDocs.getOrPut(item.sourceFilePath) {
                PDDocument.load(File(item.sourceFilePath))
              }
              if (item.sourcePageIndex in 0 until srcDoc.numberOfPages) {
                val imported = targetDoc.importPage(srcDoc.getPage(item.sourcePageIndex))
                if (item.rotationDegrees % 360 != 0) {
                  imported.rotation = (imported.rotation + item.rotationDegrees) % 360
                }
              }
            }
          }
          targetDoc.save(outputFile)
          outputFile
        }
      } finally {
        openedDocs.values.forEach { runCatching { it.close() } }
      }
    }
  }

  fun renderSignatureStrokesToBitmap(
    strokes: List<List<androidx.compose.ui.geometry.Offset>>,
    canvasWidth: Float,
    canvasHeight: Float,
    strokeColorArgb: Int,
  ): Bitmap {
    val w = 720
    val h = 320
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.TRANSPARENT)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = strokeColorArgb
      style = Paint.Style.STROKE
      strokeWidth = 7f
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
    }
    val sx = w / canvasWidth.coerceAtLeast(1f)
    val sy = h / canvasHeight.coerceAtLeast(1f)
    for (stroke in strokes) {
      if (stroke.size < 2) continue
      val path = Path()
      path.moveTo(stroke.first().x * sx, stroke.first().y * sy)
      for (pt in stroke.drop(1)) {
        path.lineTo(pt.x * sx, pt.y * sy)
      }
      canvas.drawPath(path, paint)
    }
    return bmp
  }

  private fun scaleBitmapIfLarger(bmp: Bitmap, maxDim: Int): Bitmap {
    val maxCurrent = max(bmp.width, bmp.height)
    if (maxCurrent <= maxDim) return bmp
    val ratio = maxDim.toFloat() / maxCurrent.toFloat()
    val nw = (bmp.width * ratio).toInt().coerceAtLeast(1)
    val nh = (bmp.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bmp, nw, nh, true)
  }

  private fun drawEllipse(cs: PDPageContentStream, cx: Float, cy: Float, rx: Float, ry: Float) {
    val k = 0.55228475f
    val ox = rx * k
    val oy = ry * k
    cs.moveTo(cx - rx, cy)
    cs.curveTo(cx - rx, cy + oy, cx - ox, cy + ry, cx, cy + ry)
    cs.curveTo(cx + ox, cy + ry, cx + rx, cy + oy, cx + rx, cy)
    cs.curveTo(cx + rx, cy - oy, cx + ox, cy - ry, cx, cy - ry)
    cs.curveTo(cx - ox, cy - ry, cx - rx, cy - oy, cx - rx, cy)
  }

  private fun rgbComponents(argb: Int): Triple<Int, Int, Int> {
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    return Triple(r, g, b)
  }

  private fun drawShapedUnicodeText(
    doc: PDDocument,
    cs: PDPageContentStream,
    text: String,
    pdfX: Float,
    pdfY: Float,
    fontSizePt: Float,
    colorArgb: Int,
  ) {
    val scale = 3f
    val paint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colorArgb
      textSize = fontSizePt * scale
      typeface = android.graphics.Typeface.DEFAULT
    }
    val textW = paint.measureText(text).coerceAtLeast(12f)
    val fm = paint.fontMetrics
    val textH = (fm.descent - fm.ascent).coerceAtLeast(fontSizePt * scale)
    val bmpW = (textW + 8f).toInt().coerceAtLeast(16)
    val bmpH = (textH + 8f).toInt().coerceAtLeast(16)
    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.TRANSPARENT)
    val canvas = Canvas(bmp)
    canvas.drawText(text, 4f, -fm.ascent + 4f, paint)
    val pdImg = LosslessFactory.createFromImage(doc, bmp)
    cs.drawImage(pdImg, pdfX, pdfY - (fm.descent + 4f) / scale, bmpW / scale, bmpH / scale)
    bmp.recycle()
  }

  private fun renderPageBitmapBestEffort(pdfFile: File, pageIndex: Int): Bitmap? {
    return runCatching {
      ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
          if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
          renderer.openPage(pageIndex).use { page ->
            val w = 1500
            val h = (page.height * (w.toFloat() / page.width.coerceAtLeast(1))).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
          }
        }
      }
    }.getOrNull()
  }

  private fun removeVectorTextInBoxes(
    doc: PDDocument,
    pageIndex: Int,
    page: PDPage,
    targets: List<PdfEditAction.RedactAndReplace>,
  ): Set<String> {
    if (targets.isEmpty()) return emptySet()
    val matchedTargetIds = mutableSetOf<String>()

    data class StreamBuffer(
      val pageTarget: PDPage? = null,
      val formTarget: PDFormXObject? = null,
      val tokens: MutableList<Any> = mutableListOf(),
      var modified: Boolean = false,
    )

    val allBuffers = mutableListOf<StreamBuffer>()
    val bufferStack = ArrayDeque<StreamBuffer>()
    val depthStack = ArrayDeque<Int>()
    val opPositions = mutableListOf<TextPosition>()

    fun matchTargetsForPosition(tp: TextPosition): List<String> {
      val pw = tp.pageWidth.coerceAtLeast(1f)
      val ph = tp.pageHeight.coerceAtLeast(1f)
      val cx = (tp.xDirAdj + tp.widthDirAdj * 0.5f) / pw
      val cy = (tp.yDirAdj - tp.heightDir.coerceAtLeast(tp.fontSizeInPt * 0.7f) * 0.4f) / ph
      return targets.mapNotNull { t ->
        val l = min(t.leftNorm, t.rightNorm) - 0.004f
        val r = max(t.leftNorm, t.rightNorm) + 0.004f
        val top = min(t.topNorm, t.bottomNorm) - 0.006f
        val bot = max(t.topNorm, t.bottomNorm) + 0.006f
        if (cx in l..r && cy in top..bot) t.id else null
      }
    }

    fun computeAdvanceThousandths(tmBefore: Matrix?, tmAfter: Matrix?, tfs: Float, th: Float): Float {
      if (tmBefore == null || tmAfter == null || abs(tfs) < 0.0001f || abs(th) < 0.0001f) return 0f
      return runCatching {
        val mBefore = tmBefore.createAffineTransform()
        val mAfter = tmAfter.createAffineTransform()
        if (abs(mBefore.determinant) < 1e-9) return 0f
        val delta = mBefore.createInverse().apply { concatenate(mAfter) }
        val tx = delta.translateX.toFloat()
        -(tx / (tfs * th)) * 1000f
      }.getOrDefault(0f)
    }

    fun glyphAdvanceThousandths(tp: TextPosition, tfs: Float, tc: Float, tw: Float): Float {
      val codes = tp.characterCodes
      val font = tp.font
      val w0 = if (codes != null && codes.isNotEmpty() && font != null) {
        runCatching { font.getWidth(codes[0]) }.getOrDefault(0f)
      } else {
        0f
      }
      var total = w0
      if (abs(tfs) > 0.0001f) {
        total += (tc / tfs) * 1000f
        if (codes != null && codes.isNotEmpty() && codes[0] == 32) {
          total += (tw / tfs) * 1000f
        }
      }
      return total
    }

    fun splitCosStringIntoTjArray(
      cosStr: COSString,
      positions: List<TextPosition>,
      hits: List<Boolean>,
      tfs: Float,
      tc: Float,
      tw: Float,
      outArray: COSArray,
    ): Boolean {
      val raw = cosStr.bytes ?: return false
      val n = positions.size
      if (n == 0) return false
      val bytesPerGlyph = when (raw.size) {
        n -> 1
        n * 2 -> 2
        else -> return false
      }
      var i = 0
      while (i < n) {
        if (!hits[i]) {
          val keptOut = ByteArrayOutputStream()
          while (i < n && !hits[i]) {
            val start = i * bytesPerGlyph
            keptOut.write(raw, start, bytesPerGlyph)
            i++
          }
          outArray.add(COSString(keptOut.toByteArray()))
        } else {
          var removedAdv = 0f
          while (i < n && hits[i]) {
            removedAdv += glyphAdvanceThousandths(positions[i], tfs, tc, tw)
            i++
          }
          if (abs(removedAdv) > 0.5f) {
            outArray.add(COSFloat(-removedAdv))
          }
        }
      }
      return true
    }

    val stripper = object : PDFTextStripper() {
      override fun processPage(p: PDPage) {
        val buf = StreamBuffer(pageTarget = p)
        allBuffers.add(buf)
        bufferStack.addLast(buf)
        depthStack.addLast(0)
        try {
          super.processPage(p)
        } finally {
          depthStack.removeLast()
          bufferStack.removeLast()
        }
      }

      override fun showForm(form: PDFormXObject) {
        val existing = allBuffers.firstOrNull { it.formTarget?.cosObject === form.cosObject }
        val buf = if (existing == null) StreamBuffer(formTarget = form).also { allBuffers.add(it) } else null
        if (buf != null) bufferStack.addLast(buf)
        depthStack.addLast(0)
        try {
          super.showForm(form)
        } finally {
          depthStack.removeLast()
          if (buf != null) bufferStack.removeLast()
        }
      }

      override fun processTextPosition(text: TextPosition) {
        opPositions.add(text)
        super.processTextPosition(text)
      }

      override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        val buf = bufferStack.lastOrNull()
        val depth = depthStack.lastOrNull() ?: -1
        if (buf == null || depth != 0) {
          super.processOperator(operator, operands)
          return
        }
        depthStack[depthStack.lastIndex] = 1
        try {
          val opName = operator.name
          if (opName !in setOf("Tj", "TJ", "'", "\"")) {
            super.processOperator(operator, operands)
            buf.tokens.addAll(operands)
            buf.tokens.add(operator)
            return
          }

          opPositions.clear()
          val tmBefore = textMatrix?.clone()
          val textState = graphicsState.textState
          val tfs = textState.fontSize
          val th = textState.horizontalScaling / 100f
          val tc = textState.characterSpacing
          val tw = textState.wordSpacing

          super.processOperator(operator, operands)

          if (opPositions.isEmpty()) {
            buf.tokens.addAll(operands)
            buf.tokens.add(operator)
            return
          }

          val perGlyphHits = opPositions.map { tp ->
            val ids = matchTargetsForPosition(tp)
            matchedTargetIds.addAll(ids)
            ids.isNotEmpty()
          }
          val hitCount = perGlyphHits.count { it }

          if (hitCount == 0) {
            buf.tokens.addAll(operands)
            buf.tokens.add(operator)
            return
          }

          if (hitCount == opPositions.size) {
            buf.modified = true
            if (opName == "\"" && operands.size >= 2) {
              buf.tokens.add(operands[0])
              buf.tokens.add(Operator.getOperator("Tw"))
              buf.tokens.add(operands[1])
              buf.tokens.add(Operator.getOperator("Tc"))
              buf.tokens.add(Operator.getOperator("T*"))
            } else if (opName == "'") {
              buf.tokens.add(Operator.getOperator("T*"))
            }
            val adv = computeAdvanceThousandths(tmBefore, textMatrix, tfs, th)
            if (abs(adv) > 0.5f) {
              buf.tokens.add(COSArray().apply { add(COSFloat(adv)) })
              buf.tokens.add(Operator.getOperator("TJ"))
            }
            return
          }

          // Partial match within a single Tj or TJ operator: replace only matched glyphs
          if (opName == "Tj" && operands.firstOrNull() is COSString) {
            val cosStr = operands[0] as COSString
            val arr = COSArray()
            if (splitCosStringIntoTjArray(cosStr, opPositions, perGlyphHits, tfs, tc, tw, arr)) {
              buf.modified = true
              if (arr.size() > 0) {
                buf.tokens.add(arr)
                buf.tokens.add(Operator.getOperator("TJ"))
              }
              return
            }
          } else if (opName == "TJ" && operands.firstOrNull() is COSArray) {
            val origArr = operands[0] as COSArray
            val newArr = COSArray()
            var posCursor = 0
            var splitOk = true
            for (elem in origArr) {
              if (elem is COSString) {
                val raw = elem.bytes ?: byteArrayOf()
                // Approximate byte slice matching if remaining positions fit 1x or 2x
                val remaining = opPositions.size - posCursor
                val segCount = when {
                  raw.isEmpty() -> 0
                  raw.size <= remaining && opPositions.subList(posCursor, posCursor + raw.size).all { it.characterCodes?.getOrNull(0)?.let { c -> c <= 0xFF } != false } -> raw.size
                  raw.size % 2 == 0 && (raw.size / 2) <= remaining -> raw.size / 2
                  else -> {
                    splitOk = false
                    break
                  }
                }
                if (segCount > 0) {
                  val subPos = opPositions.subList(posCursor, posCursor + segCount)
                  val subHit = perGlyphHits.subList(posCursor, posCursor + segCount)
                  if (subHit.none { it }) {
                    newArr.add(elem)
                  } else if (!splitCosStringIntoTjArray(elem, subPos, subHit, tfs, tc, tw, newArr)) {
                    splitOk = false
                    break
                  }
                  posCursor += segCount
                }
              } else {
                newArr.add(elem)
              }
            }
            if (splitOk && posCursor == opPositions.size) {
              buf.modified = true
              if (newArr.size() > 0) {
                buf.tokens.add(newArr)
                buf.tokens.add(Operator.getOperator("TJ"))
              }
              return
            }
          }

          // Safe fallback if a single mixed CID ligature cannot be sliced byte-for-byte
          buf.tokens.addAll(operands)
          buf.tokens.add(operator)
        } finally {
          depthStack[depthStack.lastIndex] = 0
        }
      }
    }

    runCatching {
      stripper.sortByPosition = false
      stripper.startPage = pageIndex + 1
      stripper.endPage = pageIndex + 1
      stripper.getText(doc)

      for (buf in allBuffers) {
        if (!buf.modified) continue
        if (buf.pageTarget != null) {
          val newStream = PDStream(doc)
          newStream.createOutputStream(COSName.FLATE_DECODE).use { out ->
            ContentStreamWriter(out).writeTokens(buf.tokens)
          }
          buf.pageTarget.setContents(newStream)
        } else if (buf.formTarget != null) {
          buf.formTarget.cosObject.createOutputStream(COSName.FLATE_DECODE).use { out ->
            ContentStreamWriter(out).writeTokens(buf.tokens)
          }
        }
      }
    }

    return matchedTargetIds
  }

  private fun safePdfText(input: String, font: PDFont): String {
    val normalized = input.replace("\r", "").replace("\n", " ")
    if (font is PDType0Font) return normalized
    return normalized.map { ch -> if (ch.code in 32..126) ch else '?' }.joinToString("")
  }
}
