package com.example.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Xml
import com.example.R
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

class DocxEngine(private val context: Context) {

  /**
   * Extracts per-page text and embedded images from a PDF and packages them into a genuine
   * OpenXML .docx archive readable by Microsoft Word, Google Docs, and LibreOffice.
   */
  suspend fun pdfToDocx(pdfFile: File, outputDocxFile: File): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val pageTexts = mutableListOf<String>()
        val mediaItems = mutableListOf<Pair<Int, ByteArray>>() // pageIndex -> pngBytes
        PDDocument.load(pdfFile).use { doc ->
          val stripper = PDFTextStripper()
          for (p in 1..doc.numberOfPages) {
            stripper.startPage = p
            stripper.endPage = p
            pageTexts.add(stripper.getText(doc))

            val page = doc.getPage(p - 1)
            val resources = page.resources
            if (resources != null) {
              for (name in resources.xObjectNames) {
                val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                if (xObj is PDImageXObject) {
                  val bmp = runCatching { xObj.image }.getOrNull() ?: continue
                  val baos = ByteArrayOutputStream()
                  bmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
                  bmp.recycle()
                  mediaItems.add((p - 1) to baos.toByteArray())
                }
              }
            }
          }
        }

        val mediaByPage = mediaItems.withIndex().groupBy({ it.value.first }, { it.index + 1 to it.value.second })
        val relsXml = buildString {
          append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
          append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
          mediaItems.forEachIndexed { idx, _ ->
            val rId = "rId${idx + 1}"
            append("""<Relationship Id="$rId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image${idx + 1}.png"/>""")
          }
          append("""</Relationships>""")
        }

        val documentXml = buildString {
          append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
          append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""")
          append("""<w:body>""")
          pageTexts.forEachIndexed { pageIdx, rawText ->
            val lines = rawText.replace("\r\n", "\n").split("\n")
            for (line in lines) {
              val trimmed = line.trimEnd()
              append("""<w:p><w:r><w:t xml:space="preserve">${escapeXml(trimmed)}</w:t></w:r></w:p>""")
            }
            mediaByPage[pageIdx].orEmpty().forEach { (imgNum, bytes) ->
              val dims = decodePngBounds(bytes)
              val cx = (dims.first.coerceIn(100, 520)) * 9525L
              val cy = (dims.second.coerceIn(80, 650)) * 9525L
              append("""<w:p><w:r><w:drawing><wp:inline><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$imgNum" name="Picture $imgNum"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$imgNum" name="image$imgNum.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId$imgNum"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>""")
            }
            if (pageIdx < pageTexts.lastIndex) {
              append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            }
          }
          append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>""")
          append("""</w:body></w:document>""")
        }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        ZipOutputStream(FileOutputStream(outputDocxFile)).use { zip ->
          writeZipEntry(zip, "[Content_Types].xml", contentTypesXml.toByteArray(Charsets.UTF_8))
          writeZipEntry(zip, "_rels/.rels", rootRelsXml.toByteArray(Charsets.UTF_8))
          writeZipEntry(zip, "word/document.xml", documentXml.toByteArray(Charsets.UTF_8))
          writeZipEntry(zip, "word/_rels/document.xml.rels", relsXml.toByteArray(Charsets.UTF_8))
          mediaItems.forEachIndexed { idx, (_, bytes) ->
            writeZipEntry(zip, "word/media/image${idx + 1}.png", bytes)
          }
        }
        outputDocxFile
      }
    }

  /**
   * Parses a real .docx archive (word/document.xml) and lays out paragraphs into a paginated A4 PDF.
   */
  suspend fun docxToPdf(docxFile: File, outputPdfFile: File): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        val paragraphs = extractParagraphsFromDocx(docxFile)
        require(paragraphs.isNotEmpty()) { "No readable text paragraphs found in DOCX file" }

        PDDocument().use { doc ->
          val pageRect = PDRectangle.A4
          val margin = 48f
          val maxLineWidth = pageRect.width - margin * 2
          val fontSize = 11.5f
          val lineLeading = 16.5f

          val activeFont = PDType1Font.HELVETICA

          val wrappedLines = mutableListOf<String>()
          for (para in paragraphs) {
            if (para.isBlank()) {
              wrappedLines.add("")
              continue
            }
            val words = para.split(Regex("\\s+"))
            var current = StringBuilder()
            for (word in words) {
              val candidate = if (current.isEmpty()) word else "$current $word"
              val width = candidate.length * fontSize * 0.52f
              if (width > maxLineWidth && current.isNotEmpty()) {
                wrappedLines.add(current.toString())
                current = StringBuilder(word)
              } else {
                current = StringBuilder(candidate)
              }
            }
            if (current.isNotEmpty()) wrappedLines.add(current.toString())
          }

          var lineIdx = 0
          while (lineIdx < wrappedLines.size) {
            val page = PDPage(pageRect)
            doc.addPage(page)
            var y = pageRect.upperRightY - margin
            PDPageContentStream(doc, page).use { cs ->
              while (lineIdx < wrappedLines.size && y > margin + lineLeading) {
                val line = wrappedLines[lineIdx]
                if (line.isNotEmpty()) {
                  if (line.any { it.code > 126 }) {
                    val scale = 2.5f
                    val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                      color = android.graphics.Color.rgb(15, 23, 42)
                      textSize = fontSize * scale
                    }
                    val tw = paint.measureText(line).coerceAtLeast(10f)
                    val fm = paint.fontMetrics
                    val th = (fm.descent - fm.ascent).coerceAtLeast(fontSize * scale)
                    val bmp = android.graphics.Bitmap.createBitmap((tw + 6).toInt(), (th + 6).toInt(), android.graphics.Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.TRANSPARENT)
                    android.graphics.Canvas(bmp).drawText(line, 2f, -fm.ascent + 2f, paint)
                    val pdImg = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bmp)
                    cs.drawImage(pdImg, margin, y, bmp.width / scale, bmp.height / scale)
                    bmp.recycle()
                  } else {
                    cs.beginText()
                    cs.setFont(activeFont, fontSize)
                    cs.setNonStrokingColor(15, 23, 42)
                    cs.newLineAtOffset(margin, y)
                    cs.showText(line.map { if (it.code in 32..126) it else '?' }.joinToString(""))
                    cs.endText()
                  }
                }
                y -= lineLeading
                lineIdx++
              }
            }
          }
          doc.save(outputPdfFile)
          outputPdfFile
        }
      }
    }

  private fun extractParagraphsFromDocx(docxFile: File): List<String> {
    var docXmlBytes: ByteArray? = null
    ZipInputStream(FileInputStream(docxFile)).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        if (entry.name == "word/document.xml") {
          docXmlBytes = zis.readBytes()
          break
        }
        entry = zis.nextEntry
      }
    }
    val xml = docXmlBytes ?: error("Invalid DOCX: missing word/document.xml")
    val paragraphs = mutableListOf<String>()
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(xml.inputStream(), "UTF-8")

    var inParagraph = false
    var inText = false
    val currentPara = StringBuilder()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      val name = parser.name.orEmpty()
      when (event) {
        XmlPullParser.START_TAG -> {
          when {
            name == "w:p" || name.endsWith(":p") -> {
              inParagraph = true
              currentPara.clear()
            }
            name == "w:t" || name.endsWith(":t") -> inText = true
            name == "w:tab" || name.endsWith(":tab") -> if (inParagraph) currentPara.append("    ")
            name == "w:br" || name.endsWith(":br") -> if (inParagraph) {
              paragraphs.add(currentPara.toString())
              currentPara.clear()
            }
          }
        }
        XmlPullParser.TEXT -> {
          if (inParagraph && inText) {
            currentPara.append(parser.text.orEmpty())
          }
        }
        XmlPullParser.END_TAG -> {
          when {
            name == "w:t" || name.endsWith(":t") -> inText = false
            name == "w:p" || name.endsWith(":p") -> {
              paragraphs.add(currentPara.toString())
              inParagraph = false
            }
          }
        }
      }
      event = parser.next()
    }
    return paragraphs
  }

  private fun writeZipEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
    zip.putNextEntry(ZipEntry(name))
    zip.write(bytes)
    zip.closeEntry()
  }

  private fun decodePngBounds(bytes: ByteArray): Pair<Int, Int> {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    return opts.outWidth.coerceAtLeast(120) to opts.outHeight.coerceAtLeast(100)
  }

  private fun escapeXml(input: String): String {
    return input
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
  }
}
