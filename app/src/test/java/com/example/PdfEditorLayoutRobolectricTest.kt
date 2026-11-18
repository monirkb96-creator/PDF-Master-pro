package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.ThemeMode
import com.example.domain.engine.PdfRendererHelper
import com.example.ui.screens.PdfEditorScreen
import com.example.ui.theme.PdfMasterTheme
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xhdpi")
class PdfEditorLayoutRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  private lateinit var app: Application
  private lateinit var container: AppContainer
  private var docId: Long = 0L

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    PDFBoxResourceLoader.init(app)
    container = AppContainer(app)
    container.pdfRendererHelper = object : PdfRendererHelper(container.documentRepository.unlockedCacheDir) {
      override suspend fun getPageCount(file: File): Int = 1

      override suspend fun renderPage(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int,
        extraRotationDegrees: Int,
      ): Bitmap {
        val h = (targetWidthPx * (842f / 595f)).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(targetWidthPx, h, Bitmap.Config.ARGB_8888).apply {
          eraseColor(AndroidColor.WHITE)
        }
      }
    }

    val pdfFile = container.documentRepository.createVaultFile("test_inline_layout.pdf")
    PDDocument().use { doc ->
      val page = PDPage(PDRectangle.A4)
      doc.addPage(page)
      PDPageContentStream(doc, page).use { cs ->
        cs.beginText()
        cs.setFont(PDType1Font.HELVETICA, 14f)
        cs.newLineAtOffset(80f, 650f)
        cs.showText("MONIR")
        cs.endText()
      }
      doc.save(pdfFile)
    }

    runBlocking {
      val entity = container.documentRepository.registerPdfFile(pdfFile)
      docId = entity.id
    }
  }

  @Test
  fun `pdf page and inline editor remain visible above soft keyboard`() {
    var hostView: View? = null

    composeRule.setContent {
      hostView = LocalView.current
      PdfMasterTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
              .consumeWindowInsets(innerPadding),
          ) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
              PdfEditorScreen(
                docId = docId,
                container = container,
                onBack = {},
                onSaved = {},
                onNotify = {},
              )
            }
          }
        }
      }
    }

    composeRule.waitUntil(timeoutMillis = 5_000) {
      runCatching {
        composeRule.onNodeWithContentDescription("Page 1").assertExists()
        true
      }.getOrDefault(false)
    }

    val pageNode = composeRule.onNodeWithContentDescription("Page 1")
    pageNode.assertIsDisplayed()

    pageNode.performTouchInput {
      click(Offset(width * 0.25f, height * 0.25f))
    }

    composeRule.runOnIdle {
      val v = hostView
      if (v != null) {
        val imeBottomPx = (300f * v.resources.displayMetrics.density).toInt()
        val insets = WindowInsetsCompat.Builder()
          .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 48, 0, 0))
          .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeBottomPx))
          .build()
        ViewCompat.dispatchApplyWindowInsets(v, insets)
      }
    }

    composeRule.waitForIdle()

    val doneBtn = composeRule.onNodeWithTag("editor_inline_done_btn")
    val inlineInput = composeRule.onNodeWithTag("editor_inline_text_input")

    doneBtn.assertIsDisplayed()
    inlineInput.assertIsDisplayed()
    pageNode.assertIsDisplayed()

    val pageBounds = pageNode.getBoundsInRoot()
    val doneBounds = doneBtn.getBoundsInRoot()
    val inputBounds = inlineInput.getBoundsInRoot()

    // With 844dp height - 300dp keyboard = ~544dp bottom, Done bar must sit near bottom (~490dp), NOT squished under TopAppBar
    assertTrue(
      "Expected Done button near bottom above keyboard (>350dp), got top=${doneBounds.top}",
      doneBounds.top.value > 350f,
    )
    // Verify PDF workspace has real height (>250dp) and inline input sits cleanly inside it above Done bar
    assertTrue(
      "Expected PDF page height > 250dp, got ${pageBounds.bottom - pageBounds.top}",
      (pageBounds.bottom - pageBounds.top).value > 250f,
    )
    assertTrue(
      "Expected inline input visible above Done bar, input=$inputBounds, doneTop=${doneBounds.top}",
      inputBounds.bottom < doneBounds.top,
    )
  }
}
