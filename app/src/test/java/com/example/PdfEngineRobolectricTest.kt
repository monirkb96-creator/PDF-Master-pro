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

  @Test
  fun `detectVisualTextLines and detectInkPhraseAt segment columns accurately and ignore watermarks and barcodes`() {
    val w = 750
    val h = 1060
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)

    // Pastel green watermark circle/band across the center (must NOT be detected as text)
    val watermarkGreen = Color.rgb(180, 212, 195)
    for (y in 320..680) {
      for (x in 150..600) {
        bmp.setPixel(x, y, watermarkGreen)
      }
    }

    // Barcode on top-right (dense full-height vertical bars - should be filtered out)
    val darkInk = Color.rgb(18, 22, 28)
    for (x in 520..700 step 4) {
      for (bx in x..(x + 1)) {
        for (y in 60..92) {
          bmp.setPixel(bx, y, darkInk)
        }
      }
    }

    // Row 1: Left label (60..120), Middle Bangla value (190..310), Right English value (520..650)
    fun drawWordBlob(x0: Int, x1: Int, y0: Int, y1: Int) {
      for (y in y0..y1) {
        for (x in x0..x1) {
          if (((x + y) % 3) != 0) bmp.setPixel(x, y, darkInk)
        }
      }
    }
    drawWordBlob(60, 115, 360, 376)   // "নাম"
    drawWordBlob(195, 315, 360, 376)  // "পল্লবী বড়ুয়া" over watermark
    drawWordBlob(520, 645, 360, 376)  // "Pollobi Barua" over watermark

    // Row 2: Second row well separated vertically
    drawWordBlob(60, 115, 420, 436)   // "মাতা"
    drawWordBlob(195, 325, 420, 436)  // "কলি রাণী বড়ুয়া"

    val blocks = BitmapPatchUtils.detectVisualTextLines(bmp, 0)
    // Expect the 5 word phrases, and NOT the barcode or watermark background
    assertEquals("Expected 5 distinct column phrase blocks, got ${blocks.map { "${it.leftNorm}..${it.rightNorm}@${it.topNorm}" }}", 5, blocks.size)

    val row1Mid = blocks.firstOrNull { (250f / w) in it.leftNorm..it.rightNorm && (368f / h) in it.topNorm..it.bottomNorm }
    assertTrue("Expected middle Bangla phrase to have its own tight block", row1Mid != null)
    assertTrue("Middle block should not merge into right English column", row1Mid!!.rightNorm < 0.45f)

    // Verify tap-ink detection hits the exact tapped phrase
    val tapped = BitmapPatchUtils.detectInkPhraseAt(bmp, 250f / w, 368f / h, 0)
    assertTrue("Expected detectInkPhraseAt to find ink at tapped phrase", tapped != null)
    assertTrue((250f / w) in tapped!!.leftNorm..tapped.rightNorm)
    assertTrue((368f / h) in tapped.topNorm..tapped.bottomNorm)

    // Verify tapping blank pastel watermark returns null
    val blankTap = BitmapPatchUtils.detectInkPhraseAt(bmp, 400f / w, 520f / h, 0)
    assertTrue("Expected blank watermark tap to return null", blankTap == null)
    bmp.recycle()
  }

  @Test
  fun `bangla replacement over graphics watermark succeeds cleanly`() = runBlocking {
    val src = tempFolder.newFile("cert_bangla_graphics.pdf")
    PDDocument().use { doc ->
      val page = PDPage(PDRectangle.A4)
      doc.addPage(page)
      PDPageContentStream(doc, page).use { cs ->
        // Green watermark patch
        cs.setNonStrokingColor(175, 212, 190)
        cs.addRect(120f, 520f, 240f, 80f)
        cs.fill()
        // Simulated vector-drawn Bangla glyphs as dark rectangles
        cs.setNonStrokingColor(15, 23, 42)
        cs.addRect(150f, 555f, 70f, 10f)
        cs.fill()
      }
      doc.save(src)
    }

    val out = tempFolder.newFile("cert_bangla_edited.pdf")
    val action = PdfEditAction.RedactAndReplace(
      id = "r_bn",
      pageIndex = 0,
      leftNorm = 148f / PDRectangle.A4.width,
      topNorm = (PDRectangle.A4.height - 568f) / PDRectangle.A4.height,
      rightNorm = 224f / PDRectangle.A4.width,
      bottomNorm = (PDRectangle.A4.height - 552f) / PDRectangle.A4.height,
      replacementText = "মনিরুল ইসলাম",
      fontSizeSp = 11f,
      textColorArgb = Color.BLACK,
      font = EditorFont.BENGALI,
      preserveBackground = true,
    )
    val res = pdfEngine.applyEdits(src, out, listOf(action))
    assertTrue(res.exceptionOrNull()?.stackTraceToString() ?: "failed", res.isSuccess)
    assertTrue(out.exists() && out.length() > 0L)
  }

  @Test
  fun `detects Times-Bold metadata, real page size, and styled bold italic replacement`() = runBlocking {
    val src = tempFolder.newFile("styled_serif.pdf")
    PDDocument().use { doc ->
      val page = PDPage(PDRectangle(800f, 1100f))
      doc.addPage(page)
      PDPageContentStream(doc, page).use { cs ->
        cs.beginText()
        cs.setFont(PDType1Font.TIMES_BOLD, 15f)
        cs.newLineAtOffset(80f, 900f)
        cs.showText("Certificate Heading")
        cs.endText()
      }
      doc.save(src)
    }

    val (wPt, hPt) = pdfEngine.getPageSizePt(src, 0)
    assertEquals(800f, wPt, 0.5f)
    assertEquals(1100f, hPt, 0.5f)

    val blocks = pdfEngine.extractPageTextBlocks(src, 0)
    assertTrue(blocks.isNotEmpty())
    val blk = blocks.first()
    assertEquals(EditorFont.TIMES, blk.fontFamily)
    assertTrue("Expected TIMES_BOLD to be detected as bold", blk.isBold)
    assertEquals(15f, blk.fontSizePt, 1.5f)

    val out = tempFolder.newFile("styled_serif_out.pdf")
    val edit = PdfEditAction.RedactAndReplace(
      id = "r_styled",
      pageIndex = 0,
      leftNorm = blk.leftNorm,
      topNorm = blk.topNorm,
      rightNorm = blk.rightNorm,
      bottomNorm = blk.bottomNorm,
      replacementText = "Updated Certificate",
      fontSizeSp = blk.fontSizePt,
      textColorArgb = Color.BLACK,
      font = blk.fontFamily,
      isBold = true,
      isItalic = true,
      preserveBackground = true,
    )
    assertTrue(pdfEngine.applyEdits(src, out, listOf(edit)).isSuccess)
    assertTrue(pdfEngine.extractTextByPage(out).first().contains("Updated Certificate"))
  }

  @Test
  fun `clean background patch ignores adjacent dark borders in outer ring without smudging`() {
    val bmp = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)
    val darkInk = Color.rgb(12, 12, 12)
    // Neighbor dark line just 2px above the target box (inside outer ring)
    for (x in 50..190) {
      for (y in 36..38) {
        bmp.setPixel(x, y, darkInk)
      }
    }
    // Target text inside the box
    for (x in 70..160) {
      for (y in 50..58) {
        bmp.setPixel(x, y, darkInk)
      }
    }

    val patch = BitmapPatchUtils.createCleanBackgroundPatch(
      pageBmp = bmp,
      leftNorm = 68f / 240f,
      topNorm = 48f / 120f,
      rightNorm = 162f / 240f,
      bottomNorm = 60f / 120f,
    )
    assertTrue(patch != null)
    val pb = patch!!.bitmap
    // Verify untouched background pixels stay transparent and erased ink reconstructs to crisp white without ring-ink smudge
    for (y in 2 until pb.height - 2) {
      val px = pb.getPixel(pb.width / 2, y)
      assertTrue(
        "Expected transparent or clean white patch without ring-ink smudge at y=$y, got argb(${Color.alpha(px)},${Color.red(px)},${Color.green(px)},${Color.blue(px)})",
        Color.alpha(px) == 0 || (Color.red(px) > 225 && Color.green(px) > 225 && Color.blue(px) > 225),
      )
    }
    pb.recycle()
    bmp.recycle()
  }

  @Test
  fun `clean background patch preserves pastel green and pink watermarks without white paper stamp`() {
    val bmp = Bitmap.createBitmap(260, 120, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)
    val greenWatermark = Color.rgb(168, 213, 186) // #A8D5BA curved birth-certificate watermark
    val pinkWatermark = Color.rgb(244, 184, 184) // #F4B8B8 center watermark
    val blackInk = Color.rgb(15, 15, 15)

    // Left half of box sits over white paper + green watermark band; right half over pink watermark
    for (y in 20..100) {
      for (x in 40..130) {
        if ((x + y) % 12 < 6) bmp.setPixel(x, y, greenWatermark)
      }
      for (x in 131..220) {
        bmp.setPixel(x, y, pinkWatermark)
      }
    }
    // Print crisp black text stroke across both watermarks
    for (x in 60..200) {
      for (y in 54..58) {
        bmp.setPixel(x, y, blackInk)
      }
    }

    val patch = BitmapPatchUtils.createCleanBackgroundPatch(
      pageBmp = bmp,
      leftNorm = 55f / 260f,
      topNorm = 44f / 120f,
      rightNorm = 205f / 260f,
      bottomNorm = 68f / 120f,
    )
    assertTrue(patch != null)
    val pb = patch!!.bitmap

    // 1. Watermark band pixels outside the black stroke (e.g. y = 3 from top of patch) must remain TRANSPARENT (alpha == 0), never stamped white!
    var untouchedWatermarkPx = 0
    for (x in 4 until pb.width - 4) {
      val topPx = pb.getPixel(x, 2)
      assertTrue(
        "Watermark/paper pixel at x=$x should stay transparent (alpha==0), not opaque white stamp",
        Color.alpha(topPx) == 0,
      )
      untouchedWatermarkPx++
    }
    assertTrue(untouchedWatermarkPx > 20)

    // 2. Erased black stroke pixels over pink watermark (right side) must reconstruct to pink hue, not plain white paper
    val midInkX = (pb.width * 0.78f).toInt()
    val midInkY = pb.height / 2
    val reconstructedPink = pb.getPixel(midInkX, midInkY)
    assertTrue(
      "Expected erased ink over pink watermark to reconstruct near pink (${Color.red(reconstructedPink)},${Color.green(reconstructedPink)},${Color.blue(reconstructedPink)})",
      Color.alpha(reconstructedPink) == 255 &&
        Color.red(reconstructedPink) > Color.green(reconstructedPink) + 30 &&
        Color.red(reconstructedPink) > 210,
    )

    pb.recycle()
    bmp.recycle()
  }

  @Test
  fun `clean background patch erases 2px anti-aliased gray and pink watermark halos with zero ghost`() {
    val w = 900
    val h = 160
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)

    val pinkWatermark = Color.rgb(244, 184, 184)
    val blackInk = Color.rgb(12, 12, 12)
    val pinkHalo50 = Color.rgb(130, 98, 98)   // 1px from core over pink
    val pinkHalo20 = Color.rgb(206, 152, 152) // 2px from core over pink (lum ~0.66)
    val grayHalo20 = Color.rgb(212, 212, 212) // 2px from core over white paper (lum ~0.83)

    // Right half has pink watermark background
    for (y in 20 until 140) {
      for (x in 450 until 850) {
        bmp.setPixel(x, y, pinkWatermark)
      }
    }

    // Left stroke over white paper with 2px gray halo
    for (x in 150..300) {
      for (y in 76..82) bmp.setPixel(x, y, blackInk)
      bmp.setPixel(x, 74, grayHalo20)
      bmp.setPixel(x, 75, grayHalo20)
      bmp.setPixel(x, 83, grayHalo20)
      bmp.setPixel(x, 84, grayHalo20)
    }

    // Right stroke over pink watermark with 2px darkened pink halo
    for (x in 550..720) {
      for (y in 76..82) bmp.setPixel(x, y, blackInk)
      bmp.setPixel(x, 75, pinkHalo50)
      bmp.setPixel(x, 83, pinkHalo50)
      bmp.setPixel(x, 74, pinkHalo20)
      bmp.setPixel(x, 84, pinkHalo20)
    }

    val leftPatch = BitmapPatchUtils.createCleanBackgroundPatch(
      pageBmp = bmp,
      leftNorm = 145f / w,
      topNorm = 70f / h,
      rightNorm = 305f / w,
      bottomNorm = 88f / h,
    )!!
    val lp = leftPatch.bitmap
    // Check EVERY pixel in left patch column x=75: zero gray halo (no pixel < 235 if opaque)
    for (y in 0 until lp.height) {
      val px = lp.getPixel(lp.width / 2, y)
      assertTrue(
        "Gray 2px halo leaked at y=$y: argb(${Color.alpha(px)},${Color.red(px)},${Color.green(px)},${Color.blue(px)})",
        Color.alpha(px) == 0 || (Color.red(px) >= 235 && Color.green(px) >= 235 && Color.blue(px) >= 235),
      )
    }
    lp.recycle()

    val rightPatch = BitmapPatchUtils.createCleanBackgroundPatch(
      pageBmp = bmp,
      leftNorm = 545f / w,
      topNorm = 70f / h,
      rightNorm = 725f / w,
      bottomNorm = 88f / h,
    )!!
    val rp = rightPatch.bitmap
    // Check EVERY pixel in right patch column x=85: zero darkened pink halo (any opaque reconstructed pixel must match pure pink >= 225 red)
    for (y in 0 until rp.height) {
      val px = rp.getPixel(rp.width / 2, y)
      assertTrue(
        "Dark pink 2px halo leaked at y=$y: argb(${Color.alpha(px)},${Color.red(px)},${Color.green(px)},${Color.blue(px)})",
        Color.alpha(px) == 0 || (Color.red(px) >= 225 && Color.red(px) > Color.green(px) + 30),
      )
    }
    rp.recycle()

    // Also verify dominant ink sampler detects blue signature color accurately
    val blueBmp = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
    blueBmp.eraseColor(Color.WHITE)
    val sigBlue = Color.rgb(18, 65, 200)
    for (x in 20..100) for (y in 25..34) blueBmp.setPixel(x, y, sigBlue)
    val sampled = BitmapPatchUtils.sampleDominantInkColor(blueBmp, 15f / 120f, 20f / 60f, 105f / 120f, 40f / 60f)
    assertTrue("Expected non-null sampled blue ink", sampled != null)
    assertTrue("Expected blue channel to dominate sampled signature ink", Color.blue(sampled!!) > Color.red(sampled) + 80)

    blueBmp.recycle()
    bmp.recycle()
  }
}
