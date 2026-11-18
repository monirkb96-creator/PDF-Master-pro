package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AppContainer
import com.example.core.i18n.LocalStrings
import com.example.core.util.BitmapPatchUtils
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentEntity
import com.example.domain.model.EditorFont
import com.example.domain.model.EditorTool
import com.example.domain.model.HeaderFooterConfig
import com.example.domain.model.OcrLanguageChoice
import com.example.domain.model.PdfEditAction
import com.example.domain.model.PdfTextBlock
import com.example.domain.model.ShapeKind
import com.example.ui.components.BusyOverlay
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class InlineEditSession(
  val sessionKey: String,
  val existingActionId: String?,
  val isReplaceBox: Boolean,
  val leftNorm: Float,
  val topNorm: Float,
  val rightNorm: Float,
  val bottomNorm: Float,
  val value: TextFieldValue,
  val fontSizeSp: Float,
  val font: EditorFont,
  val color: Color,
  val backgroundColor: Color = Color.White,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
  docId: Long,
  container: AppContainer,
  onBack: () -> Unit,
  onSaved: (PdfDocumentEntity) -> Unit,
  onNotify: (String) -> Unit,
) {
  val strings = LocalStrings.current
  val context = LocalContext.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current
  val inlineFocusRequester = remember { FocusRequester() }

  val hScroll = rememberScrollState()
  val vScroll = rememberScrollState()

  var doc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var pageCount by remember { mutableIntStateOf(0) }
  var currentPage by remember { mutableIntStateOf(0) }
  var activeTool by remember { mutableStateOf(EditorTool.EDIT_TEXT) }
  var selectedFont by remember { mutableStateOf(EditorFont.HELVETICA) }
  var selectedFontSize by remember { mutableFloatStateOf(12f) }
  var selectedColor by remember { mutableStateOf(Color(0xFF0F172A)) }
  var selectedShape by remember { mutableStateOf(ShapeKind.RECTANGLE) }
  var strokeWidth by remember { mutableFloatStateOf(3f) }
  var editorZoom by remember { mutableFloatStateOf(1f) }
  var headerFooter by remember { mutableStateOf(HeaderFooterConfig()) }
  var showHeaderFooterDialog by remember { mutableStateOf(false) }
  var showSaveMenu by remember { mutableStateOf(false) }

  // Viewport and page layout sizes for exact keyboard-aware scrolling
  var viewportHeightPx by remember { mutableIntStateOf(0) }
  var viewportWidthPx by remember { mutableIntStateOf(0) }
  var pageWidthPx by remember { mutableIntStateOf(0) }
  var pageHeightPx by remember { mutableIntStateOf(0) }

  // Detected selectable text blocks on the current page (vector PDF text or OCR on image PDFs)
  var pageTextBlocks by remember { mutableStateOf<List<PdfTextBlock>>(emptyList()) }
  var isScanningImageText by remember { mutableStateOf(false) }
  var editorOcrLang by remember { mutableStateOf(OcrLanguageChoice.ENGLISH) }
  var currentRenderedBmp by remember { mutableStateOf<Bitmap?>(null) }

  // Active inline live cursor editor directly on the PDF page
  var inlineEdit by remember { mutableStateOf<InlineEditSession?>(null) }

  // Other modal inputs (for manual drag-redact, comment notes)
  var showRedactDialogAt by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
  var showCommentDialogAt by remember { mutableStateOf<Offset?>(null) }
  var pendingImagePath by remember { mutableStateOf<String?>(null) }

  // Undo / Redo stacks
  val actions = remember { mutableStateListOf<PdfEditAction>() }
  val redoStack = remember { mutableStateListOf<PdfEditAction>() }
  var selectedActionId by remember { mutableStateOf<String?>(null) }

  // Live drag gesture state
  var dragStart by remember { mutableStateOf<Offset?>(null) }
  var dragCurrent by remember { mutableStateOf<Offset?>(null) }
  val activePenPoints = remember { mutableStateListOf<Offset>() }

  var isBusy by remember { mutableStateOf(false) }
  var refreshTick by remember { mutableIntStateOf(0) }

  LaunchedEffect(docId) {
    val loaded = container.documentRepository.getDocumentById(docId)
    doc = loaded
    if (loaded != null) {
      val f = File(loaded.filePath)
      pageCount = runCatching { container.pdfRendererHelper.getPageCount(f) }.getOrDefault(loaded.pageCount)
    }
  }

  LaunchedEffect(doc?.filePath, currentPage, refreshTick, currentRenderedBmp, editorOcrLang) {
    val path = doc?.filePath ?: return@LaunchedEffect
    val file = File(path)
    if (!file.exists()) return@LaunchedEffect
    val vectorBlocks = container.pdfEngine.extractPageTextBlocks(file, currentPage)
    pageTextBlocks = vectorBlocks
    val bmp = currentRenderedBmp
    if (bmp != null) {
      isScanningImageText = true
      pageTextBlocks = container.ocrEngine.detectHybridTextBlocks(
        bitmap = bmp,
        pageIndex = currentPage,
        language = editorOcrLang,
        existingVectorBlocks = vectorBlocks,
      )
      isScanningImageText = false
    }
  }

  // Focus inline text box on tap
  LaunchedEffect(inlineEdit?.sessionKey) {
    if (inlineEdit == null) return@LaunchedEffect
    delay(60)
    runCatching {
      inlineFocusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  // Keep the edited line centered above the soft keyboard once IME height settles
  LaunchedEffect(inlineEdit?.sessionKey, viewportHeightPx) {
    val session = inlineEdit ?: return@LaunchedEffect
    if (viewportHeightPx <= 0 || pageHeightPx <= 0) return@LaunchedEffect
    delay(90)
    val maxScrollY = (pageHeightPx - viewportHeightPx).coerceAtLeast(0)
    if (maxScrollY > 0) {
      val targetCenterY = ((session.topNorm + session.bottomNorm) * 0.5f) * pageHeightPx
      val desiredY = (targetCenterY - viewportHeightPx * 0.42f).roundToInt().coerceIn(0, maxScrollY)
      vScroll.animateScrollTo(desiredY)
    } else {
      vScroll.animateScrollTo(0)
    }
    if (viewportWidthPx > 0 && pageWidthPx > viewportWidthPx) {
      val maxScrollX = (pageWidthPx - viewportWidthPx).coerceAtLeast(0)
      val targetLeftX = session.leftNorm * pageWidthPx
      val desiredX = (targetLeftX - viewportWidthPx * 0.25f).roundToInt().coerceIn(0, maxScrollX)
      hScroll.animateScrollTo(desiredX)
    }
  }

  fun pushAction(action: PdfEditAction) {
    actions.add(action)
    redoStack.clear()
  }

  fun commitInlineEdit() {
    val session = inlineEdit ?: return
    inlineEdit = null
    keyboardController?.hide()

    val txt = session.value.text
    val existingIdx = session.existingActionId?.let { id -> actions.indexOfFirst { it.id == id } } ?: -1

    if (session.isReplaceBox) {
      val act = PdfEditAction.RedactAndReplace(
        id = session.existingActionId ?: UUID.randomUUID().toString(),
        pageIndex = currentPage,
        leftNorm = session.leftNorm,
        topNorm = session.topNorm,
        rightNorm = session.rightNorm,
        bottomNorm = session.bottomNorm,
        replacementText = txt,
        fontSizeSp = session.fontSizeSp,
        textColorArgb = session.color.toArgb(),
        font = session.font,
        backgroundArgb = session.backgroundColor.toArgb(),
      )
      if (existingIdx >= 0) {
        actions[existingIdx] = act
      } else {
        pushAction(act)
      }
    } else if (txt.isNotBlank()) {
      val act = PdfEditAction.AddText(
        id = session.existingActionId ?: UUID.randomUUID().toString(),
        pageIndex = currentPage,
        xNorm = session.leftNorm,
        yNorm = (session.topNorm + 0.022f).coerceIn(0.02f, 0.98f),
        text = txt,
        fontSizeSp = session.fontSizeSp,
        font = session.font,
        colorArgb = session.color.toArgb(),
      )
      if (existingIdx >= 0) {
        actions[existingIdx] = act
      } else {
        pushAction(act)
      }
    } else if (existingIdx >= 0) {
      actions.removeAt(existingIdx)
    }
  }

  fun distToRectSq(px: Float, py: Float, l: Float, t: Float, r: Float, b: Float): Float {
    val dx = when {
      px < l -> l - px
      px > r -> px - r
      else -> 0f
    }
    val dy = when {
      py < t -> t - py
      py > b -> py - b
      else -> 0f
    }
    return dx * dx + dy * dy
  }

  fun openInlineForTap(nx: Float, ny: Float, preferExistingBlock: Boolean) {
    commitInlineEdit()

    // 1. Check if user tapped an existing pending AddText / RedactAndReplace action on this page
    val hitPending = actions
      .filter { act ->
        if (act.pageIndex != currentPage) return@filter false
        when (act) {
          is PdfEditAction.RedactAndReplace -> {
            val l = min(act.leftNorm, act.rightNorm) - 0.008f
            val r = max(act.leftNorm, act.rightNorm) + 0.014f
            val t = min(act.topNorm, act.bottomNorm) - 0.008f
            val b = max(act.topNorm, act.bottomNorm) + 0.008f
            nx in l..r && ny in t..b
          }
          is PdfEditAction.AddText -> {
            val l = act.xNorm - 0.012f
            val r = (act.xNorm + (act.text.length * 0.015f).coerceAtLeast(0.10f)).coerceAtMost(1f)
            val b = act.yNorm + 0.016f
            val t = (act.yNorm - 0.032f).coerceAtLeast(0f)
            nx in l..r && ny in t..b
          }
          else -> false
        }
      }
      .minByOrNull { act ->
        when (act) {
          is PdfEditAction.RedactAndReplace -> distToRectSq(
            nx,
            ny,
            min(act.leftNorm, act.rightNorm),
            min(act.topNorm, act.bottomNorm),
            max(act.leftNorm, act.rightNorm),
            max(act.topNorm, act.bottomNorm),
          )
          is PdfEditAction.AddText -> distToRectSq(
            nx,
            ny,
            act.xNorm,
            (act.yNorm - 0.022f).coerceAtLeast(0f),
            (act.xNorm + 0.14f).coerceAtMost(1f),
            act.yNorm + 0.006f,
          )
          else -> Float.MAX_VALUE
        }
      }

    if (hitPending is PdfEditAction.RedactAndReplace) {
      val text = hitPending.replacementText
      inlineEdit = InlineEditSession(
        sessionKey = UUID.randomUUID().toString(),
        existingActionId = hitPending.id,
        isReplaceBox = true,
        leftNorm = min(hitPending.leftNorm, hitPending.rightNorm),
        topNorm = min(hitPending.topNorm, hitPending.bottomNorm),
        rightNorm = max(hitPending.leftNorm, hitPending.rightNorm),
        bottomNorm = max(hitPending.topNorm, hitPending.bottomNorm),
        value = TextFieldValue(text = text, selection = TextRange(text.length)),
        fontSizeSp = hitPending.fontSizeSp,
        font = hitPending.font,
        color = Color(hitPending.textColorArgb),
        backgroundColor = Color(hitPending.backgroundArgb),
      )
      return
    }

    if (hitPending is PdfEditAction.AddText) {
      val text = hitPending.text
      inlineEdit = InlineEditSession(
        sessionKey = UUID.randomUUID().toString(),
        existingActionId = hitPending.id,
        isReplaceBox = false,
        leftNorm = hitPending.xNorm,
        topNorm = (hitPending.yNorm - 0.022f).coerceIn(0f, 0.94f),
        rightNorm = (hitPending.xNorm + 0.25f).coerceAtMost(0.98f),
        bottomNorm = (hitPending.yNorm + 0.02f).coerceAtMost(0.99f),
        value = TextFieldValue(text = text, selection = TextRange(text.length)),
        fontSizeSp = hitPending.fontSizeSp,
        font = hitPending.font,
        color = Color(hitPending.colorArgb),
      )
      return
    }

    // 2. Check if user tapped an existing extracted text block (vector, OCR, or offline visual Bangla phrase)
    if (preferExistingBlock) {
      val strictMatches = pageTextBlocks.filter { blk ->
        nx in (blk.leftNorm - 0.004f)..(blk.rightNorm + 0.004f) &&
          ny in (blk.topNorm - 0.004f)..(blk.bottomNorm + 0.004f)
      }
      val nearMatches = if (strictMatches.isNotEmpty()) {
        strictMatches
      } else {
        pageTextBlocks.filter { blk ->
          nx in (blk.leftNorm - 0.012f)..(blk.rightNorm + 0.012f) &&
            ny in (blk.topNorm - 0.010f)..(blk.bottomNorm + 0.010f)
        }
      }
      val hitBlock = nearMatches.minWithOrNull(
        compareBy<PdfTextBlock> { distToRectSq(nx, ny, it.leftNorm, it.topNorm, it.rightNorm, it.bottomNorm) }
          .thenBy { (it.rightNorm - it.leftNorm) * (it.bottomNorm - it.topNorm) }
      ) ?: BitmapPatchUtils.detectInkPhraseAt(currentRenderedBmp, nx, ny, currentPage)

      if (hitBlock != null) {
        val text = hitBlock.text
        val sampledBg = sampleBoxBackgroundColor(
          currentRenderedBmp,
          hitBlock.leftNorm,
          hitBlock.topNorm,
          hitBlock.rightNorm,
          hitBlock.bottomNorm,
        )
        val lum = (0.299f * sampledBg.red + 0.587f * sampledBg.green + 0.114f * sampledBg.blue)
        val defaultInk = if (lum < 0.45f) Color.White else selectedColor
        inlineEdit = InlineEditSession(
          sessionKey = UUID.randomUUID().toString(),
          existingActionId = null,
          isReplaceBox = true,
          leftNorm = hitBlock.leftNorm,
          topNorm = hitBlock.topNorm,
          rightNorm = hitBlock.rightNorm,
          bottomNorm = hitBlock.bottomNorm,
          value = TextFieldValue(text = text, selection = TextRange(text.length)),
          fontSizeSp = hitBlock.fontSizePt.coerceIn(9f, 36f),
          font = if (text.isEmpty() || text.any { it.code > 126 }) EditorFont.BENGALI else selectedFont,
          color = defaultInk,
          backgroundColor = sampledBg,
        )
        return
      }
    }

    // 3. Otherwise open a new compact inline text cursor box centered vertically at the tapped spot
    val boxTop = (ny - 0.014f).coerceIn(0.01f, 0.92f)
    val boxLeft = (nx - 0.01f).coerceIn(0.01f, 0.85f)
    inlineEdit = InlineEditSession(
      sessionKey = UUID.randomUUID().toString(),
      existingActionId = null,
      isReplaceBox = false,
      leftNorm = boxLeft,
      topNorm = boxTop,
      rightNorm = (boxLeft + 0.20f).coerceAtMost(0.98f),
      bottomNorm = (boxTop + 0.032f).coerceAtMost(0.98f),
      value = TextFieldValue(text = "", selection = TextRange(0)),
      fontSizeSp = selectedFontSize,
      font = selectedFont,
      color = selectedColor,
    )
  }

  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      val imgFile = File(context.cacheDir, "editor_img_${System.currentTimeMillis()}.png")
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(imgFile).use { output -> input.copyTo(output) }
      }
      pendingImagePath = imgFile.absolutePath
      activeTool = EditorTool.IMAGE
      onNotify(strings.tapPageToPlace)
    }
  }

  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
    commitInlineEdit()
    val currentDoc = doc ?: return@rememberLauncherForActivityResult
    if (uri != null) {
      scope.launch {
        isBusy = true
        val tmp = container.documentRepository.createVaultFile("edited_${currentDoc.displayName}")
        val res = container.pdfEngine.applyEdits(
          sourceFile = File(currentDoc.filePath),
          outputFile = tmp,
          actions = actions.toList(),
          headerFooter = headerFooter,
        )
        isBusy = false
        res.onSuccess { out ->
          FileUtils.copyFileToUri(context, out, uri)
          val registered = container.documentRepository.registerPdfFile(out)
          onNotify(strings.savedSuccess)
          onSaved(registered)
        }.onFailure {
          onNotify(it.message ?: strings.errorGeneric)
        }
      }
    }
  }

  fun saveToVault(overwrite: Boolean) {
    commitInlineEdit()
    val currentDoc = doc ?: return
    scope.launch {
      isBusy = true
      val srcFile = File(currentDoc.filePath)
      val outFile = if (overwrite) {
        File(context.cacheDir, "save_tmp_${System.currentTimeMillis()}.pdf")
      } else {
        container.documentRepository.createVaultFile("edited_${currentDoc.displayName}")
      }
      val res = container.pdfEngine.applyEdits(
        sourceFile = srcFile,
        outputFile = outFile,
        actions = actions.toList(),
        headerFooter = headerFooter,
      )
      res.onSuccess { produced ->
        val finalDoc = if (overwrite) {
          produced.copyTo(srcFile, overwrite = true)
          produced.delete()
          container.documentRepository.refreshMetadata(currentDoc)
        } else {
          container.documentRepository.registerPdfFile(produced)
        }
        doc = finalDoc
        actions.clear()
        redoStack.clear()
        headerFooter = HeaderFooterConfig()
        refreshTick++
        isBusy = false
        onNotify(strings.savedSuccess)
        onSaved(finalDoc)
      }.onFailure { err ->
        isBusy = false
        onNotify(err.message ?: strings.errorGeneric)
      }
    }
  }

  val colorPalette = listOf(
    Color(0xFF0F172A),
    Color(0xFF155EEF),
    Color(0xFFDC2626),
    Color(0xFF059669),
    Color(0xFFF59E0B),
    Color(0xFF7C3AED),
  )

  BackHandler(enabled = inlineEdit != null) {
    commitInlineEdit()
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      TopAppBar(
        title = { Text(strings.toolEditPdf, maxLines = 1) },
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
          IconButton(onClick = {
            commitInlineEdit()
            onBack()
          }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
          }
        },
        actions = {
          IconButton(
            onClick = {
              commitInlineEdit()
              if (actions.isNotEmpty()) {
                val last = actions.removeAt(actions.lastIndex)
                redoStack.add(last)
              }
            },
            enabled = actions.isNotEmpty(),
            modifier = Modifier.testTag("editor_undo_btn"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.undo)
          }
          IconButton(
            onClick = {
              commitInlineEdit()
              if (redoStack.isNotEmpty()) {
                actions.add(redoStack.removeAt(redoStack.lastIndex))
              }
            },
            enabled = redoStack.isNotEmpty(),
            modifier = Modifier.testTag("editor_redo_btn"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = strings.redo)
          }
          IconButton(
            onClick = { saveToVault(overwrite = true) },
            modifier = Modifier.testTag("editor_save_btn"),
          ) {
            Icon(Icons.Filled.Save, contentDescription = strings.saveOverwrite)
          }
          Box {
            IconButton(
              onClick = { showSaveMenu = true },
              modifier = Modifier.testTag("editor_more_save_btn"),
            ) {
              Icon(Icons.Filled.MoreVert, contentDescription = strings.saveAs)
            }
            DropdownMenu(
              expanded = showSaveMenu,
              onDismissRequest = { showSaveMenu = false },
            ) {
              DropdownMenuItem(
                text = { Text(strings.saveOverwrite) },
                leadingIcon = { Icon(Icons.Filled.Save, contentDescription = null) },
                onClick = {
                  showSaveMenu = false
                  saveToVault(overwrite = true)
                },
              )
              DropdownMenuItem(
                text = { Text(strings.saveCopy) },
                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = {
                  showSaveMenu = false
                  saveToVault(overwrite = false)
                },
              )
              DropdownMenuItem(
                text = { Text(strings.saveAs) },
                leadingIcon = { Icon(Icons.Filled.SaveAlt, contentDescription = null) },
                onClick = {
                  showSaveMenu = false
                  exportLauncher.launch("edited_${doc?.displayName ?: "document.pdf"}")
                },
              )
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // 1. Sleek Tool Palette Strip (only visible when not actively typing so canvas stays huge!)
        if (inlineEdit == null) {
          Surface(tonalElevation = 1.dp) {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val tools = listOf(
                Triple(EditorTool.EDIT_TEXT, strings.editorToolEditText, Icons.Filled.Edit),
                Triple(EditorTool.TEXT, strings.editorToolText, Icons.Filled.TextFields),
                Triple(EditorTool.SELECT, strings.editorToolSelect, Icons.Filled.OpenWith),
                Triple(EditorTool.REDACT, strings.editorToolRedact, Icons.Filled.HideSource),
                Triple(EditorTool.IMAGE, strings.editorToolImage, Icons.Filled.Image),
                Triple(EditorTool.SHAPE, strings.editorToolShape, Icons.Filled.Category),
                Triple(EditorTool.PEN, strings.editorToolPen, Icons.Filled.Brush),
                Triple(EditorTool.HIGHLIGHT, strings.editorToolHighlight, Icons.Filled.BorderColor),
                Triple(EditorTool.UNDERLINE, strings.editorToolUnderline, Icons.Filled.FormatUnderlined),
                Triple(EditorTool.COMMENT, strings.editorToolComment, Icons.AutoMirrored.Filled.Comment),
                Triple(EditorTool.HEADER_FOOTER, strings.editorToolHeaderFooter, Icons.Filled.ViewDay),
              )
              items(tools) { (tool, label, icon) ->
                FilterChip(
                  selected = activeTool == tool,
                  onClick = {
                    commitInlineEdit()
                    when (tool) {
                      EditorTool.IMAGE -> photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                      EditorTool.HEADER_FOOTER -> showHeaderFooterDialog = true
                      else -> activeTool = tool
                    }
                  },
                  label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                  leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp)) },
                  modifier = Modifier.height(32.dp).testTag("editor_tool_${tool.name.lowercase()}"),
                )
              }
            }
          }
        }

        // 2. Full-Height Zoomable & Keyboard-Aware PDF Workspace
        BoxWithConstraints(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .onSizeChanged { sz ->
              viewportWidthPx = sz.width
              viewportHeightPx = sz.height
            },
        ) {
          val viewportWidth = maxWidth - 12.dp
          val f = doc?.let { File(it.filePath) }

          if (f != null && f.exists() && pageCount > 0) {
            val pageBmp by produceState<Bitmap?>(initialValue = null, f.absolutePath, currentPage, refreshTick) {
              val rendered = container.pdfRendererHelper.renderPage(f, currentPage, targetWidthPx = 1500)
              currentRenderedBmp = rendered
              value = rendered
            }
            val bmp = pageBmp
            if (bmp != null) {
              val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
              val zoomedWidth = (viewportWidth * editorZoom).coerceAtLeast(240.dp)
              val fontScale = (zoomedWidth.value / 595f).coerceIn(0.42f, 3.0f)
              val textMeasurer = rememberTextMeasurer()
              val bgPatchCache = remember(bmp) { mutableMapOf<String, BitmapPatchUtils.PatchResult?>() }
              DisposableEffect(bmp) {
                onDispose {
                  bgPatchCache.values.forEach { it?.bitmap?.recycle() }
                  bgPatchCache.clear()
                }
              }
              fun getOrCreateBgPatch(
                leftNorm: Float,
                topNorm: Float,
                rightNorm: Float,
                bottomNorm: Float,
              ): BitmapPatchUtils.PatchResult? {
                val key = "$leftNorm|$topNorm|$rightNorm|$bottomNorm"
                return bgPatchCache.getOrPut(key) {
                  BitmapPatchUtils.createCleanBackgroundPatch(bmp, leftNorm, topNorm, rightNorm, bottomNorm)
                }
              }

              val currentOnPageTap by rememberUpdatedState { nx: Float, ny: Float ->
                when (activeTool) {
                  EditorTool.EDIT_TEXT -> openInlineForTap(nx, ny, preferExistingBlock = true)
                  EditorTool.TEXT -> openInlineForTap(nx, ny, preferExistingBlock = false)
                  EditorTool.COMMENT -> {
                    commitInlineEdit()
                    showCommentDialogAt = Offset(nx, ny)
                  }
                  EditorTool.IMAGE -> {
                    commitInlineEdit()
                    val imgPath = pendingImagePath
                    if (imgPath != null) {
                      pushAction(
                        PdfEditAction.AddImage(
                          id = UUID.randomUUID().toString(),
                          pageIndex = currentPage,
                          leftNorm = (nx - 0.15f).coerceIn(0f, 0.7f),
                          topNorm = (ny - 0.1f).coerceIn(0f, 0.7f),
                          widthNorm = 0.30f,
                          heightNorm = 0.20f,
                          imagePath = imgPath,
                        )
                      )
                    } else {
                      photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    }
                  }
                  EditorTool.SELECT -> {
                    commitInlineEdit()
                    selectedActionId = actions.lastOrNull { it.pageIndex == currentPage }?.id
                  }
                  else -> Unit
                }
              }

              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .then(if (editorZoom > 1.02f) Modifier.horizontalScroll(hScroll) else Modifier)
                  .verticalScroll(vScroll)
                  .padding(6.dp),
                contentAlignment = Alignment.TopCenter,
              ) {
                BoxWithConstraints(
                  modifier = Modifier
                    .width(zoomedWidth)
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .onSizeChanged { sz ->
                      pageWidthPx = sz.width
                      pageHeightPx = sz.height
                    }
                    .pointerInput(currentPage) {
                      detectTapGestures { offset ->
                        val nx = (offset.x / size.width).coerceIn(0f, 1f)
                        val ny = (offset.y / size.height).coerceIn(0f, 1f)
                        currentOnPageTap(nx, ny)
                      }
                    }
                    .pointerInput(activeTool, currentPage, selectedShape, selectedColor, strokeWidth) {
                      if (activeTool in setOf(
                          EditorTool.PEN,
                          EditorTool.SHAPE,
                          EditorTool.HIGHLIGHT,
                          EditorTool.UNDERLINE,
                          EditorTool.REDACT,
                          EditorTool.SELECT,
                        )
                      ) {
                        detectDragGestures(
                          onDragStart = { start ->
                            val norm = Offset((start.x / size.width).coerceIn(0f, 1f), (start.y / size.height).coerceIn(0f, 1f))
                            dragStart = norm
                            dragCurrent = norm
                            if (activeTool == EditorTool.PEN) {
                              activePenPoints.clear()
                              activePenPoints.add(norm)
                            }
                          },
                          onDrag = { change, dragAmount ->
                            change.consume()
                            val norm = Offset(
                              (change.position.x / size.width).coerceIn(0f, 1f),
                              (change.position.y / size.height).coerceIn(0f, 1f),
                            )
                            dragCurrent = norm
                            if (activeTool == EditorTool.PEN) {
                              activePenPoints.add(norm)
                            } else if (activeTool == EditorTool.SELECT && selectedActionId != null) {
                              val dx = dragAmount.x / size.width
                              val dy = dragAmount.y / size.height
                              val idx = actions.indexOfFirst { it.id == selectedActionId }
                              if (idx >= 0) {
                                actions[idx] = nudgeAction(actions[idx], dx, dy)
                              }
                            }
                          },
                          onDragEnd = {
                            val s = dragStart
                            val e = dragCurrent
                            if (s != null && e != null) {
                              when (activeTool) {
                                EditorTool.PEN -> {
                                  if (activePenPoints.size >= 2) {
                                    pushAction(
                                      PdfEditAction.AddPenStroke(
                                        id = UUID.randomUUID().toString(),
                                        pageIndex = currentPage,
                                        pointsNorm = activePenPoints.toList(),
                                        colorArgb = selectedColor.toArgb(),
                                        strokeWidthPt = strokeWidth,
                                      )
                                    )
                                  }
                                  activePenPoints.clear()
                                }
                                EditorTool.SHAPE -> {
                                  pushAction(
                                    PdfEditAction.AddShape(
                                      id = UUID.randomUUID().toString(),
                                      pageIndex = currentPage,
                                      shapeKind = selectedShape,
                                      leftNorm = s.x,
                                      topNorm = s.y,
                                      rightNorm = e.x,
                                      bottomNorm = e.y,
                                      strokeColorArgb = selectedColor.toArgb(),
                                      strokeWidthPt = strokeWidth,
                                    )
                                  )
                                }
                                EditorTool.HIGHLIGHT -> {
                                  pushAction(
                                    PdfEditAction.AddHighlight(
                                      id = UUID.randomUUID().toString(),
                                      pageIndex = currentPage,
                                      leftNorm = s.x,
                                      topNorm = s.y - 0.015f,
                                      rightNorm = e.x,
                                      bottomNorm = e.y + 0.015f,
                                      colorArgb = Color(0xFFFACC15).toArgb(),
                                    )
                                  )
                                }
                                EditorTool.UNDERLINE -> {
                                  pushAction(
                                    PdfEditAction.AddUnderline(
                                      id = UUID.randomUUID().toString(),
                                      pageIndex = currentPage,
                                      startXNorm = s.x,
                                      endXNorm = e.x,
                                      yNorm = e.y,
                                      colorArgb = selectedColor.toArgb(),
                                    )
                                  )
                                }
                                EditorTool.REDACT -> {
                                  showRedactDialogAt = s to e
                                }
                                else -> Unit
                              }
                            }
                            dragStart = null
                            dragCurrent = null
                          },
                        )
                      }
                    },
                ) {
                  Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "${strings.pageLabel} ${currentPage + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                  )

                  val activeEditingId = inlineEdit?.existingActionId
                  val pageActions = actions.filter { it.pageIndex == currentPage && it.id != activeEditingId }

                  Canvas(modifier = Modifier.fillMaxSize()) {
                    // Subtle dashed outlines over clickable existing PDF text blocks when EDIT_TEXT is active
                    if (activeTool == EditorTool.EDIT_TEXT) {
                      val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                      for (blk in pageTextBlocks) {
                        val l = blk.leftNorm * size.width
                        val t = blk.topNorm * size.height
                        val w = ((blk.rightNorm - blk.leftNorm) * size.width).coerceAtLeast(14f)
                        val h = ((blk.bottomNorm - blk.topNorm) * size.height).coerceAtLeast(10f)
                        drawRect(
                          color = Color(0xFF155EEF).copy(alpha = 0.05f),
                          topLeft = Offset(l, t),
                          size = Size(w, h),
                        )
                        drawRect(
                          color = Color(0xFF155EEF).copy(alpha = 0.42f),
                          topLeft = Offset(l, t),
                          size = Size(w, h),
                          style = Stroke(width = 1f, pathEffect = dashEffect),
                        )
                      }
                    }

                    // Committed pending actions preview
                    for (act in pageActions) {
                      when (act) {
                        is PdfEditAction.AddText -> {
                          val px = act.xNorm * size.width
                          val py = act.yNorm * size.height
                          val style = TextStyle(
                            color = Color(act.colorArgb),
                            fontSize = (act.fontSizeSp * fontScale).sp,
                            fontWeight = FontWeight.Normal,
                          )
                          val result = textMeasurer.measure(act.text, style)
                          drawText(result, topLeft = Offset(px, (py - result.size.height * 0.85f).coerceAtLeast(0f)))
                        }
                        is PdfEditAction.RedactAndReplace -> {
                          val l = min(act.leftNorm, act.rightNorm) * size.width
                          val t = min(act.topNorm, act.bottomNorm) * size.height
                          val w = abs(act.rightNorm - act.leftNorm) * size.width
                          val h = abs(act.bottomNorm - act.topNorm) * size.height
                          val patch = if (act.preserveBackground) {
                            getOrCreateBgPatch(act.leftNorm, act.topNorm, act.rightNorm, act.bottomNorm)
                          } else {
                            null
                          }
                          if (patch != null && !patch.bitmap.isRecycled) {
                            val pLeft = (patch.leftNorm * size.width).roundToInt()
                            val pTop = (patch.topNorm * size.height).roundToInt()
                            val pW = ((patch.rightNorm - patch.leftNorm) * size.width).roundToInt().coerceAtLeast(4)
                            val pH = ((patch.bottomNorm - patch.topNorm) * size.height).roundToInt().coerceAtLeast(4)
                            drawImage(
                              image = patch.bitmap.asImageBitmap(),
                              dstOffset = IntOffset(pLeft, pTop),
                              dstSize = IntSize(pW, pH),
                            )
                          } else {
                            drawRect(
                              Color(act.backgroundArgb),
                              topLeft = Offset(l - 1f, t - 1f),
                              size = Size(w.coerceAtLeast(14f) + 2f, h.coerceAtLeast(10f) + 2f),
                            )
                          }
                          if (act.replacementText.isNotBlank()) {
                            val res = textMeasurer.measure(
                              act.replacementText,
                              TextStyle(
                                color = Color(act.textColorArgb),
                                fontSize = (act.fontSizeSp * fontScale).sp,
                              ),
                            )
                            drawText(res, topLeft = Offset(l, t))
                          }
                        }
                        is PdfEditAction.AddImage -> {
                          val imgBmp = BitmapFactory.decodeFile(act.imagePath)
                          if (imgBmp != null) {
                            drawImage(
                              image = imgBmp.asImageBitmap(),
                              dstOffset = IntOffset((act.leftNorm * size.width).toInt(), (act.topNorm * size.height).toInt()),
                              dstSize = IntSize((act.widthNorm * size.width).toInt(), (act.heightNorm * size.height).toInt()),
                            )
                          }
                        }
                        is PdfEditAction.AddShape -> {
                          val x1 = act.leftNorm * size.width
                          val y1 = act.topNorm * size.height
                          val x2 = act.rightNorm * size.width
                          val y2 = act.bottomNorm * size.height
                          val c = Color(act.strokeColorArgb)
                          when (act.shapeKind) {
                            ShapeKind.RECTANGLE -> drawRect(c, Offset(min(x1, x2), min(y1, y2)), Size(abs(x2 - x1), abs(y2 - y1)), style = Stroke(act.strokeWidthPt * 2f))
                            ShapeKind.CIRCLE -> drawOval(c, Offset(min(x1, x2), min(y1, y2)), Size(abs(x2 - x1), abs(y2 - y1)), style = Stroke(act.strokeWidthPt * 2f))
                            ShapeKind.LINE, ShapeKind.ARROW -> drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = act.strokeWidthPt * 2f)
                          }
                        }
                        is PdfEditAction.AddPenStroke -> {
                          if (act.pointsNorm.size >= 2) {
                            val p = Path()
                            p.moveTo(act.pointsNorm.first().x * size.width, act.pointsNorm.first().y * size.height)
                            act.pointsNorm.drop(1).forEach { pt -> p.lineTo(pt.x * size.width, pt.y * size.height) }
                            drawPath(p, Color(act.colorArgb), style = Stroke(act.strokeWidthPt * 2f))
                          }
                        }
                        is PdfEditAction.AddHighlight -> {
                          val l = min(act.leftNorm, act.rightNorm) * size.width
                          val t = min(act.topNorm, act.bottomNorm) * size.height
                          val w = abs(act.rightNorm - act.leftNorm) * size.width
                          val h = abs(act.bottomNorm - act.topNorm) * size.height
                          drawRect(Color(act.colorArgb).copy(alpha = 0.35f), Offset(l, t), Size(w.coerceAtLeast(14f), h.coerceAtLeast(16f)))
                        }
                        is PdfEditAction.AddUnderline -> {
                          drawLine(
                            Color(act.colorArgb),
                            Offset(act.startXNorm * size.width, act.yNorm * size.height),
                            Offset(act.endXNorm * size.width, act.yNorm * size.height),
                            strokeWidth = 3f,
                          )
                        }
                        is PdfEditAction.AddComment -> {
                          drawCircle(Color(act.colorArgb), radius = 14f, center = Offset(act.xNorm * size.width, act.yNorm * size.height))
                        }
                      }
                    }

                    // Clean watermark-preserving patch under the active inline replacement box
                    val activeSession = inlineEdit
                    if (activeSession != null && activeSession.isReplaceBox) {
                      val patch = getOrCreateBgPatch(
                        activeSession.leftNorm,
                        activeSession.topNorm,
                        activeSession.rightNorm,
                        activeSession.bottomNorm,
                      )
                      if (patch != null && !patch.bitmap.isRecycled) {
                        val pLeft = (patch.leftNorm * size.width).roundToInt()
                        val pTop = (patch.topNorm * size.height).roundToInt()
                        val pW = ((patch.rightNorm - patch.leftNorm) * size.width).roundToInt().coerceAtLeast(4)
                        val pH = ((patch.bottomNorm - patch.topNorm) * size.height).roundToInt().coerceAtLeast(4)
                        drawImage(
                          image = patch.bitmap.asImageBitmap(),
                          dstOffset = IntOffset(pLeft, pTop),
                          dstSize = IntSize(pW, pH),
                        )
                      }
                    }

                    // Live pen stroke preview
                    if (activePenPoints.size >= 2) {
                      val p = Path()
                      p.moveTo(activePenPoints.first().x * size.width, activePenPoints.first().y * size.height)
                      activePenPoints.drop(1).forEach { pt -> p.lineTo(pt.x * size.width, pt.y * size.height) }
                      drawPath(p, selectedColor, style = Stroke(strokeWidth * 2f))
                    }
                  }

                  // Live Tightly-Fitted Inline Editable Text Box placed with exact layout padding
                  val session = inlineEdit
                  if (session != null) {
                    val leftDp = with(density) { (session.leftNorm * constraints.maxWidth).toDp() }
                    val topDp = with(density) { (session.topNorm * constraints.maxHeight).toDp() }
                    val origWidthDp = ((session.rightNorm - session.leftNorm) * zoomedWidth.value).coerceAtLeast(36f).dp
                    val maxRemainingDp = (zoomedWidth * (1f - session.leftNorm) - 4.dp).coerceAtLeast(origWidthDp)

                    Box(
                      modifier = Modifier
                        .padding(start = leftDp, top = topDp)
                        .width(IntrinsicSize.Min)
                        .widthIn(min = origWidthDp, max = maxRemainingDp)
                        .background(
                          color = Color.Transparent,
                          shape = RoundedCornerShape(2.dp),
                        )
                        .border(1.dp, Color(0xFF155EEF).copy(alpha = 0.85f), RoundedCornerShape(2.dp)),
                    ) {
                      BasicTextField(
                        value = session.value,
                        onValueChange = { updated ->
                          inlineEdit = session.copy(value = updated)
                        },
                        maxLines = 2,
                        textStyle = TextStyle(
                          color = session.color,
                          fontSize = (session.fontSizeSp * fontScale).sp,
                          fontWeight = FontWeight.Normal,
                        ),
                        cursorBrush = SolidColor(Color(0xFF155EEF)),
                        decorationBox = { innerTextField ->
                          Box {
                            if (session.isReplaceBox && session.value.text.isEmpty()) {
                              Text(
                                text = strings.textContentHint,
                                color = Color(0xFF155EEF).copy(alpha = 0.45f),
                                fontSize = (session.fontSizeSp * fontScale * 0.85f).sp,
                                maxLines = 1,
                              )
                            }
                            innerTextField()
                          }
                        },
                        modifier = Modifier
                          .widthIn(min = origWidthDp, max = maxRemainingDp)
                          .focusRequester(inlineFocusRequester)
                          .testTag("editor_inline_text_input"),
                      )
                    }
                  }
                }
              }
            } else {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }
            }
          }
        }

        // 3. Adobe Acrobat-Style Bottom Toolbar (Right above keyboard when typing, or Page + Zoom + OCR when browsing!)
        Surface(tonalElevation = 3.dp) {
          val currentSession = inlineEdit
          if (currentSession != null) {
            LazyRow(
              modifier = Modifier.fillMaxWidth().height(44.dp),
              contentPadding = PaddingValues(horizontal = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              item {
                Button(
                  onClick = { commitInlineEdit() },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                  modifier = Modifier.height(32.dp).testTag("editor_inline_done_btn"),
                ) {
                  Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                  Spacer(Modifier.width(4.dp))
                  Text(strings.doneLabel, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium)
                }
              }
              item {
                IconButton(
                  onClick = {
                    inlineEdit = null
                    keyboardController?.hide()
                  },
                  modifier = Modifier.size(32.dp),
                ) {
                  Icon(Icons.Filled.Close, contentDescription = strings.cancel, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
              }
              item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                  IconButton(
                    onClick = {
                      val nextSize = (currentSession.fontSizeSp - 1.5f).coerceAtLeast(7f)
                      selectedFontSize = nextSize
                      inlineEdit = currentSession.copy(fontSizeSp = nextSize)
                    },
                    modifier = Modifier.size(30.dp),
                  ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Smaller text", modifier = Modifier.size(16.dp))
                  }
                  Text("${currentSession.fontSizeSp.toInt()}pt", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                  IconButton(
                    onClick = {
                      val nextSize = (currentSession.fontSizeSp + 1.5f).coerceAtMost(42f)
                      selectedFontSize = nextSize
                      inlineEdit = currentSession.copy(fontSizeSp = nextSize)
                    },
                    modifier = Modifier.size(30.dp),
                  ) {
                    Icon(Icons.Filled.Add, contentDescription = "Larger text", modifier = Modifier.size(16.dp))
                  }
                }
              }
              items(colorPalette) { c ->
                Box(
                  modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                      width = if (currentSession.color == c) 2.5.dp else 1.dp,
                      color = if (currentSession.color == c) MaterialTheme.colorScheme.onSurface else Color.White,
                      shape = CircleShape,
                    )
                    .clickable {
                      selectedColor = c
                      inlineEdit = currentSession.copy(color = c)
                    },
                )
              }
              item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { editorZoom = (editorZoom - 0.35f).coerceAtLeast(1f) },
                    enabled = editorZoom > 1.01f,
                    modifier = Modifier.size(30.dp),
                  ) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = strings.zoomOut, modifier = Modifier.size(16.dp))
                  }
                  Text("${(editorZoom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                  IconButton(
                    onClick = { editorZoom = (editorZoom + 0.35f).coerceAtMost(3.0f) },
                    enabled = editorZoom < 2.99f,
                    modifier = Modifier.size(30.dp),
                  ) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = strings.zoomIn, modifier = Modifier.size(16.dp))
                  }
                }
              }
            }
          } else {
            LazyRow(
              modifier = Modifier.fillMaxWidth().height(44.dp),
              contentPadding = PaddingValues(horizontal = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = {
                      commitInlineEdit()
                      if (currentPage > 0) currentPage--
                    },
                    enabled = currentPage > 0,
                    modifier = Modifier.size(32.dp),
                  ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(20.dp))
                  }
                  Text("${currentPage + 1}/${pageCount.coerceAtLeast(1)}", style = MaterialTheme.typography.labelMedium)
                  IconButton(
                    onClick = {
                      commitInlineEdit()
                      if (currentPage < pageCount - 1) currentPage++
                    },
                    enabled = currentPage < pageCount - 1,
                    modifier = Modifier.size(32.dp),
                  ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
                  }
                }
              }
              item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { editorZoom = (editorZoom - 0.35f).coerceAtLeast(1f) },
                    enabled = editorZoom > 1.01f,
                    modifier = Modifier.size(32.dp),
                  ) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = strings.zoomOut, modifier = Modifier.size(18.dp))
                  }
                  Text("${(editorZoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                  IconButton(
                    onClick = { editorZoom = (editorZoom + 0.35f).coerceAtMost(3.0f) },
                    enabled = editorZoom < 2.99f,
                    modifier = Modifier.size(32.dp),
                  ) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = strings.zoomIn, modifier = Modifier.size(18.dp))
                  }
                  if (editorZoom > 1.05f) {
                    IconButton(
                      onClick = { editorZoom = 1f },
                      modifier = Modifier.size(32.dp),
                    ) {
                      Icon(Icons.Filled.FitScreen, contentDescription = strings.fitWidth, modifier = Modifier.size(18.dp))
                    }
                  }
                }
              }
              if (activeTool == EditorTool.EDIT_TEXT) {
                item {
                  FilterChip(
                    selected = editorOcrLang == OcrLanguageChoice.BANGLA,
                    onClick = {
                      val next = if (editorOcrLang == OcrLanguageChoice.ENGLISH) {
                        if (container.ocrEngine.isLanguageReady(OcrLanguageChoice.BANGLA)) {
                          OcrLanguageChoice.BANGLA
                        } else {
                          onNotify(strings.ocrModelMissingBn)
                          OcrLanguageChoice.ENGLISH
                        }
                      } else {
                        OcrLanguageChoice.ENGLISH
                      }
                      editorOcrLang = next
                    },
                    label = {
                      Text(
                        if (editorOcrLang == OcrLanguageChoice.BANGLA) "OCR: বাংলা" else "OCR: EN",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                      )
                    },
                    leadingIcon = {
                      Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.height(30.dp),
                  )
                }
              }
              if (activeTool == EditorTool.SHAPE) {
                items(ShapeKind.entries) { sk ->
                  FilterChip(
                    selected = selectedShape == sk,
                    onClick = { selectedShape = sk },
                    label = {
                      Text(
                        when (sk) {
                          ShapeKind.RECTANGLE -> "Rect"
                          ShapeKind.CIRCLE -> "Oval"
                          ShapeKind.LINE -> "Line"
                          ShapeKind.ARROW -> "Arrow"
                        },
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                      )
                    },
                    modifier = Modifier.height(30.dp),
                  )
                }
              }
              items(colorPalette) { c ->
                Box(
                  modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(
                      width = if (selectedColor == c) 2.5.dp else 1.dp,
                      color = if (selectedColor == c) MaterialTheme.colorScheme.onSurface else Color.White,
                      shape = CircleShape,
                    )
                    .clickable { selectedColor = c },
                )
              }
            }
          }
        }
      }

      BusyOverlay(isBusy)
    }
  }

  // Redact & Replace Dialog (for manual drag-redact box over scanned/image areas)
  showRedactDialogAt?.let { (s, e) ->
    var replacement by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showRedactDialogAt = null },
      title = { Text(strings.editorToolRedact) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(strings.redactNotice, style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(
            value = replacement,
            onValueChange = { replacement = it },
            label = { Text(strings.textContentHint) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(onClick = {
          pushAction(
            PdfEditAction.RedactAndReplace(
              id = UUID.randomUUID().toString(),
              pageIndex = currentPage,
              leftNorm = s.x,
              topNorm = s.y,
              rightNorm = e.x,
              bottomNorm = e.y,
              replacementText = replacement,
              fontSizeSp = selectedFontSize,
              textColorArgb = selectedColor.toArgb(),
              font = selectedFont,
              preserveBackground = false,
            )
          )
          showRedactDialogAt = null
        }) { Text(strings.apply) }
      },
      dismissButton = {
        TextButton(onClick = { showRedactDialogAt = null }) { Text(strings.cancel) }
      },
    )
  }

  // Comment Dialog
  showCommentDialogAt?.let { normPos ->
    var comment by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCommentDialogAt = null },
      title = { Text(strings.editorToolComment) },
      text = {
        OutlinedTextField(
          value = comment,
          onValueChange = { comment = it },
          label = { Text(strings.commentHint) },
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (comment.isNotBlank()) {
              pushAction(
                PdfEditAction.AddComment(
                  id = UUID.randomUUID().toString(),
                  pageIndex = currentPage,
                  xNorm = normPos.x,
                  yNorm = normPos.y,
                  author = "PDF Master Pro",
                  comment = comment,
                  colorArgb = selectedColor.toArgb(),
                )
              )
            }
            showCommentDialogAt = null
          },
        ) { Text(strings.apply) }
      },
      dismissButton = {
        TextButton(onClick = { showCommentDialogAt = null }) { Text(strings.cancel) }
      },
    )
  }

  // Header & Footer Dialog
  if (showHeaderFooterDialog) {
    var hText by remember { mutableStateOf(headerFooter.headerText) }
    var fText by remember { mutableStateOf(headerFooter.footerText) }
    var pageNums by remember { mutableStateOf(headerFooter.includePageNumbers) }
    AlertDialog(
      onDismissRequest = { showHeaderFooterDialog = false },
      title = { Text(strings.editorToolHeaderFooter) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = hText, onValueChange = { hText = it }, label = { Text(strings.headerTextHint) })
          OutlinedTextField(value = fText, onValueChange = { fText = it }, label = { Text(strings.footerTextHint) })
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pageNums, onCheckedChange = { pageNums = it })
            Text(strings.includePageNumbers, style = MaterialTheme.typography.bodyMedium)
          }
        }
      },
      confirmButton = {
        Button(onClick = {
          headerFooter = HeaderFooterConfig(hText, fText, pageNums)
          showHeaderFooterDialog = false
        }) { Text(strings.apply) }
      },
      dismissButton = {
        TextButton(onClick = { showHeaderFooterDialog = false }) { Text(strings.cancel) }
      },
    )
  }
}

private fun nudgeAction(action: PdfEditAction, dx: Float, dy: Float): PdfEditAction {
  return when (action) {
    is PdfEditAction.AddText -> action.copy(
      xNorm = (action.xNorm + dx).coerceIn(0f, 0.95f),
      yNorm = (action.yNorm + dy).coerceIn(0.02f, 0.98f),
    )
    is PdfEditAction.RedactAndReplace -> action.copy(
      leftNorm = (action.leftNorm + dx).coerceIn(0f, 0.95f),
      rightNorm = (action.rightNorm + dx).coerceIn(0.02f, 0.99f),
      topNorm = (action.topNorm + dy).coerceIn(0f, 0.95f),
      bottomNorm = (action.bottomNorm + dy).coerceIn(0.02f, 0.99f),
    )
    is PdfEditAction.AddImage -> action.copy(
      leftNorm = (action.leftNorm + dx).coerceIn(0f, 0.9f),
      topNorm = (action.topNorm + dy).coerceIn(0f, 0.9f),
    )
    is PdfEditAction.AddComment -> action.copy(
      xNorm = (action.xNorm + dx).coerceIn(0f, 0.95f),
      yNorm = (action.yNorm + dy).coerceIn(0.02f, 0.98f),
    )
    else -> action
  }
}

private fun sampleBoxBackgroundColor(
  bmp: Bitmap?,
  leftNorm: Float,
  topNorm: Float,
  rightNorm: Float,
  bottomNorm: Float,
): Color {
  if (bmp == null || bmp.width <= 4 || bmp.height <= 4) return Color.White
  val x0 = (leftNorm * bmp.width).toInt().coerceIn(2, bmp.width - 3)
  val x1 = (rightNorm * bmp.width).toInt().coerceIn(x0 + 1, bmp.width - 2)
  val y0 = (topNorm * bmp.height).toInt().coerceIn(2, bmp.height - 3)
  val y1 = (bottomNorm * bmp.height).toInt().coerceIn(y0 + 1, bmp.height - 2)

  val samples = mutableListOf<Int>()
  val stepX = ((x1 - x0) / 4).coerceAtLeast(1)
  for (x in x0..x1 step stepX) {
    samples.add(bmp.getPixel(x, (y0 - 2).coerceAtLeast(0)))
    samples.add(bmp.getPixel(x, (y1 + 2).coerceAtMost(bmp.height - 1)))
  }
  val midY = (y0 + y1) / 2
  samples.add(bmp.getPixel((x0 - 2).coerceAtLeast(0), midY))
  samples.add(bmp.getPixel((x1 + 2).coerceAtMost(bmp.width - 1), midY))

  if (samples.isEmpty()) return Color.White
  val sorted = samples.sortedBy { px ->
    val r = android.graphics.Color.red(px)
    val g = android.graphics.Color.green(px)
    val b = android.graphics.Color.blue(px)
    0.299f * r + 0.587f * g + 0.114f * b
  }
  val chosen = sorted[(sorted.size * 3) / 4]
  return Color(chosen)
}
