package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.example.core.i18n.StringsBn
import com.example.core.i18n.StringsEn
import com.example.core.util.BitmapPatchUtils
import com.example.domain.engine.DocxEngine
import com.example.domain.engine.PdfManipulationEngine
import com.example.domain.model.CompressionLevel
import com.example.domain.model.EditorFont
import com.example.domain.model.HeaderFooterConfig
import com.example.domain.model.OrganizerPageItem
import com.example.domain.model.PdfEditAction
import com.example.domain.model.SplitMode
import com.example.domain.model.WatermarkPosition
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfEngineRobolectricTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private lateinit var context: Context
  private lateinit var pdfEngine: PdfManipulationEngine
  private lateinit var docxEngine: DocxEngine

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    PDFBoxResourceLoader.init(context)
    pdfEngine = PdfManipulationEngine(context)
    docxEngine = DocxEngine(context)
  }

  private fun createSamplePdf(name: String, pages: Int = 2, textPrefix: String = "Hello PDF"): File {
    val file = tempFolder.newFile(name)
    PDDocument().use { doc ->
      for (i in 1..pages) {
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
          cs.beginText()
          cs.setFont(PDType1Font.HELVETICA, 14f)
          cs.newLineAtOffset(50f, 700f)
          cs.showText("$textPrefix Page $i")
          cs.endText()
        }
      }
      doc.save(file)
    }
    return file
  }

  private fun createSampleImage(name: String): File {
    val file = tempFolder.newFile(name)
    val bmp = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.BLUE)
    FileOutputStream(file).use { out ->
      bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    bmp.recycle()
    return file
  }

  @Test
  fun `edit pdf with text image shapes and header footer`() = runBlocking {
    val src = createSamplePdf("edit_src.pdf", pages = 2)
    val img = createSampleImage("stamp.jpg")
    val out = tempFolder.newFile("edit_out.pdf")

    val actions = listOf(
      PdfEditAction.AddText("t1", 0, 0.2f, 0.2f, "Added Label", 16f, EditorFont.HELVETICA, Color.BLUE),
      PdfEditAction.AddText("t2", 0, 0.2f, 0.3f, "বাংলা শিরোনাম", 16f, EditorFont.BENGALI, Color.BLACK),
      PdfEditAction.AddImage("i1", 1, 0.1f, 0.1f, 0.25f, 0.2f, img.absolutePath),
      PdfEditAction.AddPenStroke("p1", 0, listOf(Offset(0.1f, 0.5f), Offset(0.4f, 0.6f)), Color.RED, 2f),
    )

    val res = pdfEngine.applyEdits(
      sourceFile = src,
      outputFile = out,
      actions = actions,
      headerFooter = HeaderFooterConfig(headerText = "Draft", footerText = "Footer", includePageNumbers = true),
    )
    assertTrue(res.exceptionOrNull()?.stackTraceToString() ?: "failed", res.isSuccess)
    val textByPage = pdfEngine.extractTextByPage(out)
    assertEquals(2, textByPage.size)
    assertTrue(textByPage[0].contains("Added Label"))
    assertTrue(textByPage[0].contains("Draft"))
  }

  @Test
  fun `merge and split pdfs`() = runBlocking {
    val a = createSamplePdf("a.pdf", pages = 2, textPrefix = "DocA")
    val b = createSamplePdf("b.pdf", pages = 3, textPrefix = "DocB")
    val merged = tempFolder.newFile("merged.pdf")

    val mergeRes = pdfEngine.mergePdfs(listOf(a, b), merged)
    assertTrue(mergeRes.isSuccess)
    assertEquals(5, pdfEngine.getDocumentInfo(merged).getOrThrow().pageCount)

    var splitIdx = 0
    val splitRes = pdfEngine.splitPdf(
      sourceFile = merged,
      mode = SplitMode.PAGE_RANGE,
      selectedPagesZeroBased = emptySet(),
      rangeStartOneBased = 2,
      rangeEndOneBased = 4,
      chunkSize = 2,
      outputFileFactory = { suffix -> tempFolder.newFile("split_${splitIdx++}_$suffix.pdf") },
    )
    assertTrue(splitRes.isSuccess)
    val splitFiles = splitRes.getOrThrow()
    assertEquals(1, splitFiles.size)
    assertEquals(3, pdfEngine.getDocumentInfo(splitFiles.first()).getOrThrow().pageCount)
  }

  @Test
  fun `images to pdf and extract embedded images`() = runBlocking {
    val img1 = createSampleImage("p1.jpg")
    val img2 = createSampleImage("p2.jpg")
    val outPdf = tempFolder.newFile("from_imgs.pdf")

    val res = pdfEngine.imagesToPdf(listOf(img1, img2), outPdf)
    assertTrue(res.isSuccess)
    assertEquals(2, pdfEngine.getDocumentInfo(outPdf).getOrThrow().pageCount)

    val extractDir = tempFolder.newFolder("extracted_imgs")
    val extracted = pdfEngine.extractEmbeddedImages(outPdf, extractDir, "test").getOrThrow()
    assertEquals(2, extracted.size)

    val compressed = tempFolder.newFile("compressed.pdf")
    val compRes = pdfEngine.compressPdf(outPdf, compressed, CompressionLevel.HIGH).getOrThrow()
    assertTrue(compRes.compressedBytes > 0)
  }

  @Test
  fun `signature watermark and page organizer`() = runBlocking {
    val src = createSamplePdf("org_src.pdf", pages = 2)
    val sigBmp = pdfEngine.renderSignatureStrokesToBitmap(
      listOf(listOf(Offset(10f, 10f), Offset(100f, 80f))),
      200f,
      100f,
      Color.BLUE,
    )
    val signed = tempFolder.newFile("signed.pdf")
    assertTrue(pdfEngine.applySignature(src, signed, sigBmp, setOf(0), 0.5f, 0.7f, 0.3f).isSuccess)

    val watermarked = tempFolder.newFile("watermarked.pdf")
    assertTrue(
      pdfEngine.applyWatermark(
        signed,
        watermarked,
        "CONFIDENTIAL",
        null,
        0.3f,
        1.0f,
        45f,
        WatermarkPosition.DIAGONAL,
        emptySet(),
        Color.GRAY,
      ).isSuccess
    )

    val organized = tempFolder.newFile("organized.pdf")
    val items = listOf(
      OrganizerPageItem("1", watermarked.absolutePath, 1, rotationDegrees = 90),
      OrganizerPageItem("2", "", -1, isBlank = true),
    )
    assertTrue(pdfEngine.commitPageOrganization(items, organized).isSuccess)
    assertEquals(2, pdfEngine.getDocumentInfo(organized).getOrThrow().pageCount)
  }

  @Test
  fun `protect and unlock pdf enforces valid password`() = runBlocking {
    val src = createSamplePdf("sec_src.pdf", pages = 1)
    val protectedPdf = tempFolder.newFile("protected.pdf")
    assertTrue(pdfEngine.protectPdf(src, protectedPdf, "secret123", "owner123", allowPrinting = true, allowCopying = false).isSuccess)

    val unlockedFail = tempFolder.newFile("unlock_fail.pdf")
    assertTrue(pdfEngine.unlockPdf(protectedPdf, unlockedFail, "wrong_pw").isFailure)

    val unlockedOk = tempFolder.newFile("unlock_ok.pdf")
    assertTrue(pdfEngine.unlockPdf(protectedPdf, unlockedOk, "secret123").isSuccess)
    assertFalse(pdfEngine.getDocumentInfo(unlockedOk).getOrThrow().isEncrypted)
  }

  @Test
  fun `pdf to docx and docx to pdf roundtrip`() = runBlocking {
    val src = createSamplePdf("docx_src.pdf", pages = 1, textPrefix = "Invoice Summary")
    val docx = tempFolder.newFile("out.docx")
    assertTrue(docxEngine.pdfToDocx(src, docx).isSuccess)

    ZipFile(docx).use { zip ->
      val docXml = zip.getEntry("word/document.xml")
      assertTrue(docXml != null)
      val xmlText = zip.getInputStream(docXml).bufferedReader().readText()
      assertTrue(xmlText.contains("Invoice Summary"))
    }

    val pdfFromDocx = tempFolder.newFile("roundtrip.pdf")
    assertTrue(docxEngine.docxToPdf(docx, pdfFromDocx).isSuccess)
    assertEquals(1, pdfEngine.getDocumentInfo(pdfFromDocx).getOrThrow().pageCount)
  }

  @Test
  fun `bilingual english and bangla strings are populated`() {
    assertEquals("PDF Master Pro", StringsEn.appName)
    assertEquals("PDF Master Pro", StringsBn.appName)
    assertTrue(StringsBn.navHome.isNotBlank())
    assertTrue(StringsBn.toolEditPdf.isNotBlank())
    assertTrue(StringsBn.editorToolEditText.isNotBlank())
  }

  @Test
  fun `extract page text blocks detects coordinates for inline editing`() = runBlocking {
    val src = createSamplePdf("inline_src.pdf", pages = 1, textPrefix = "Contract Amount")
    val blocks = pdfEngine.extractPageTextBlocks(src, 0)
    assertTrue("Expected at least 1 text block", blocks.isNotEmpty())
    val first = blocks.first()
    assertTrue(first.text.contains("Contract Amount"))
    assertTrue(first.leftNorm in 0f..1f)
    assertTrue(first.topNorm in 0f..1f)
    assertTrue(first.rightNorm > first.leftNorm)
    assertTrue(first.bottomNorm > first.topNorm)

    val out = tempFolder.newFile("inline_edited.pdf")
    val replaceAction = PdfEditAction.RedactAndReplace(
      id = "r1",
      pageIndex = 0,
      leftNorm = first.leftNorm,
      topNorm = first.topNorm,
      rightNorm = first.rightNorm,
      bottomNorm = first.bottomNorm,
      replacementText = "Updated Total 5000",
      fontSizeSp = first.fontSizePt,
      textColorArgb = Color.BLACK,
      font = EditorFont.HELVETICA,
      backgroundArgb = Color.rgb(245, 240, 225),
    )
    assertTrue(pdfEngine.applyEdits(src, out, listOf(replaceAction)).isSuccess)
    val extracted = pdfEngine.extractTextByPage(out).first()
    assertTrue(extracted.contains("Updated Total 5000"))
  }

  @Test
  fun `preserveBackground vector edit removes old text and keeps adjacent labels`() = runBlocking {
    val src = tempFolder.newFile("cert_vector.pdf")
    PDDocument().use { doc ->
      val page = PDPage(PDRectangle.A4)
      doc.addPage(page)
      PDPageContentStream(doc, page).use { cs ->
        // Draw pastel green watermark background stripe
        cs.setNonStrokingColor(168, 213, 186)
        cs.addRect(100f, 650f, 300f, 120f)
        cs.fill()

        // Draw adjacent label and editable value
        cs.setNonStrokingColor(15, 23, 42)
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 14f)
        cs.newLineAtOffset(60f, 700f)
        cs.showText("Label:")
        cs.newLineAtOffset(140f, 0f)
        cs.showText("OldName")
        cs.endText()
      }
      doc.save(src)
    }

    val blocks = pdfEngine.extractPageTextBlocks(src, 0)
    val targetBlock = blocks.first { it.text.contains("OldName") }
    val out = tempFolder.newFile("cert_vector_edited.pdf")
    val replaceAction = PdfEditAction.RedactAndReplace(
      id = "r_vec",
      pageIndex = 0,
      leftNorm = targetBlock.leftNorm,
      topNorm = targetBlock.topNorm,
      rightNorm = targetBlock.rightNorm,
      bottomNorm = targetBlock.bottomNorm,
      replacementText = "Monir",
      fontSizeSp = targetBlock.fontSizePt,
      textColorArgb = Color.BLACK,
      font = EditorFont.HELVETICA,
      preserveBackground = true,
    )

    val res = pdfEngine.applyEdits(src, out, listOf(replaceAction))
    assertTrue(res.exceptionOrNull()?.stackTraceToString() ?: "failed", res.isSuccess)
    val extracted = pdfEngine.extractTextByPage(out).first()
    assertFalse("Old text should be stripped from PDF vector stream: $extracted", extracted.contains("OldName"))
    assertTrue("Adjacent label should remain untouched: $extracted", extracted.contains("Label:"))
    assertTrue("Replacement text should be present: $extracted", extracted.contains("Monir"))
  }

  @Test
  fun `bitmap patch utils erases dark ink while preserving watermark color`() {
    val bmp = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)
    val watermarkGreen = Color.rgb(168, 213, 186)
    for (y in 20..80) {
      for (x in 40..160) {
        bmp.setPixel(x, y, watermarkGreen)
      }
    }
    // Paint dark ink bar inside the watermark region
    val darkInk = Color.rgb(18, 18, 18)
    for (y in 45..52) {
      for (x in 70..130) {
        bmp.setPixel(x, y, darkInk)
      }
    }

    val patch = BitmapPatchUtils.createCleanBackgroundPatch(
      pageBmp = bmp,
      leftNorm = 68f / 200f,
      topNorm = 43f / 100f,
      rightNorm = 132f / 200f,
      bottomNorm = 54f / 100f,
    )
    assertTrue("Expected non-null patch", patch != null)
    val pbmp = patch!!.bitmap
    val centerPx = pbmp.getPixel(pbmp.width / 2, pbmp.height / 2)
    val r = Color.red(centerPx)
    val g = Color.green(centerPx)
    val b = Color.blue(centerPx)
    assertTrue("Expected dark ink to be removed, got rgb($r,$g,$b)", g > 170)
    assertTrue("Expected green watermark hue to be preserved instead of pure white, got rgb($r,$g,$b)", g > r + 15)
    pbmp.recycle()
    bmp.recycle()
  }
}
