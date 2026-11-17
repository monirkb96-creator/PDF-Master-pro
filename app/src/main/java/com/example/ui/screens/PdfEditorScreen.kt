package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.AppContainer
import com.example.core.i18n.LocalStrings
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentEntity
import com.example.domain.model.EditorFont
import com.example.domain.model.EditorTool
import com.example.domain.model.HeaderFooterConfig
import com.example.domain.model.PdfEditAction
import com.example.domain.model.ShapeKind
import com.example.ui.components.BusyOverlay
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.launch

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
  val scope = rememberCoroutineScope()

  var doc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var pageCount by remember { mutableIntStateOf(0) }
  var currentPage by remember { mutableIntStateOf(0) }
  var activeTool by remember { mutableStateOf(EditorTool.TEXT) }
  var selectedFont by remember { mutableStateOf(EditorFont.HELVETICA) }
  var selectedFontSize by remember { mutableFloatStateOf(16f) }
  var selectedColor by remember { mutableStateOf(Color(0xFF155EEF)) }
  var selectedShape by remember { mutableStateOf(ShapeKind.RECTANGLE) }
  var strokeWidth by remember { mutableFloatStateOf(3f) }
  var headerFooter by remember { mutableStateOf(HeaderFooterConfig()) }
  var showHeaderFooterDialog by remember { mutableStateOf(false) }

  // Pending dialog inputs
  var showTextDialogAt by remember { mutableStateOf<Offset?>(null) }
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

  fun pushAction(action: PdfEditAction) {
    actions.add(action)
    redoStack.clear()
  }

  fun saveToVault(overwrite: Boolean) {
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

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.toolEditPdf, maxLines = 1) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
          }
        },
        actions = {
          IconButton(
            onClick = {
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
          IconButton(
            onClick = { exportLauncher.launch("edited_${doc?.displayName ?: "document.pdf"}") },
            modifier = Modifier.testTag("editor_download_btn"),
          ) {
            Icon(Icons.Filled.SaveAlt, contentDescription = strings.saveAs)
          }
        },
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Tool Palette Row
        Surface(tonalElevation = 2.dp) {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            val tools = listOf(
              Triple(EditorTool.SELECT, strings.editorToolSelect, Icons.Filled.OpenWith),
              Triple(EditorTool.TEXT, strings.editorToolText, Icons.Filled.TextFields),
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
                  when (tool) {
                    EditorTool.IMAGE -> photoPicker.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    EditorTool.HEADER_FOOTER -> showHeaderFooterDialog = true
                    else -> activeTool = tool
                  }
                },
                label = { Text(label) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("editor_tool_${tool.name.lowercase()}"),
              )
            }
          }
        }

        // Contextual Tool Properties Strip
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            if (activeTool == EditorTool.REDACT) {
              Text(
                text = strings.redactNotice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                colorPalette.forEach { c ->
                  Box(
                    modifier = Modifier
                      .size(24.dp)
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
              if (activeTool == EditorTool.SHAPE) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  ShapeKind.entries.forEach { sk ->
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
                          style = MaterialTheme.typography.labelSmall,
                        )
                      },
                    )
                  }
                }
              } else {
                Text(
                  text = strings.tapPageToPlace,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        // Interactive Page Canvas
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
          contentAlignment = Alignment.Center,
        ) {
          val f = doc?.let { File(it.filePath) }
          if (f != null && f.exists() && pageCount > 0) {
            val pageBmp by produceState<Bitmap?>(initialValue = null, f.absolutePath, currentPage, refreshTick) {
              value = container.pdfRendererHelper.renderPage(f, currentPage, targetWidthPx = 1200)
            }
            val bmp = pageBmp
            if (bmp != null) {
              val aspect = bmp!!.width.toFloat() / bmp!!.height.coerceAtLeast(1)
              BoxWithConstraints(
                modifier = Modifier
                  .fillMaxWidth()
                  .aspectRatio(aspect)
                  .clip(RoundedCornerShape(6.dp))
                  .background(Color.White)
                  .pointerInput(activeTool, currentPage, selectedShape, selectedColor, pendingImagePath) {
                    detectTapGestures { offset ->
                      val nx = (offset.x / size.width).coerceIn(0f, 1f)
                      val ny = (offset.y / size.height).coerceIn(0f, 1f)
                      when (activeTool) {
                        EditorTool.TEXT -> showTextDialogAt = Offset(nx, ny)
                        EditorTool.COMMENT -> showCommentDialogAt = Offset(nx, ny)
                        EditorTool.IMAGE -> {
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
                          // Select nearest text/image/comment for moving
                          selectedActionId = actions.lastOrNull { it.pageIndex == currentPage }?.id
                        }
                        else -> Unit
                      }
                    }
                  }
                  .pointerInput(activeTool, currentPage, selectedShape, selectedColor, strokeWidth) {
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
                  },
              ) {
                Image(
                  bitmap = bmp.asImageBitmap(),
                  contentDescription = "${strings.pageLabel} ${currentPage + 1}",
                  contentScale = ContentScale.FillBounds,
                  modifier = Modifier.fillMaxSize(),
                )

                // Overlay Preview Canvas for pending actions
                val pageActions = actions.filter { it.pageIndex == currentPage }
                val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                Canvas(modifier = Modifier.fillMaxSize()) {
                  for (act in pageActions) {
                    when (act) {
                      is PdfEditAction.AddText -> {
                        val px = act.xNorm * size.width
                        val py = act.yNorm * size.height
                        val style = androidx.compose.ui.text.TextStyle(
                          color = Color(act.colorArgb),
                          fontSize = (act.fontSizeSp * 0.85f).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) },
                          fontWeight = FontWeight.Medium,
                        )
                        val result = textMeasurer.measure(act.text, style)
                        drawText(result, topLeft = Offset(px, (py - result.size.height).coerceAtLeast(0f)))
                      }
                      is PdfEditAction.RedactAndReplace -> {
                        val l = min(act.leftNorm, act.rightNorm) * size.width
                        val t = min(act.topNorm, act.bottomNorm) * size.height
                        val w = abs(act.rightNorm - act.leftNorm) * size.width
                        val h = abs(act.bottomNorm - act.topNorm) * size.height
                        drawRect(Color.White, topLeft = Offset(l, t), size = Size(w.coerceAtLeast(18f), h.coerceAtLeast(18f)))
                        drawRect(Color(0xFFCBD5E1), topLeft = Offset(l, t), size = Size(w.coerceAtLeast(18f), h.coerceAtLeast(18f)), style = Stroke(1.5f))
                        if (act.replacementText.isNotBlank()) {
                          val res = textMeasurer.measure(
                            act.replacementText,
                            androidx.compose.ui.text.TextStyle(color = Color(act.textColorArgb)),
                          )
                          drawText(res, topLeft = Offset(l + 4f, t + 4f))
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

                  // Live pen stroke preview
                  if (activePenPoints.size >= 2) {
                    val p = Path()
                    p.moveTo(activePenPoints.first().x * size.width, activePenPoints.first().y * size.height)
                    activePenPoints.drop(1).forEach { pt -> p.lineTo(pt.x * size.width, pt.y * size.height) }
                    drawPath(p, selectedColor, style = Stroke(strokeWidth * 2f))
                  }
                }
              }
            } else {
              CircularProgressIndicator()
            }
          }
        }

        // Bottom Page Switcher & Save Bar
        Surface(tonalElevation = 3.dp) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { if (currentPage > 0) currentPage-- }, enabled = currentPage > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
              }
              Text("${currentPage + 1} / ${pageCount.coerceAtLeast(1)}", style = MaterialTheme.typography.labelLarge)
              IconButton(onClick = { if (currentPage < pageCount - 1) currentPage++ }, enabled = currentPage < pageCount - 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = { saveToVault(overwrite = false) }) {
                Text(strings.saveCopy)
              }
              Button(onClick = { saveToVault(overwrite = true) }) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.saveOverwrite)
              }
            }
          }
        }
      }

      BusyOverlay(isBusy)
    }
  }

  // Add Text Dialog
  showTextDialogAt?.let { normPos ->
    var input by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showTextDialogAt = null },
      title = { Text(strings.editorToolText) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(strings.textContentHint) },
            modifier = Modifier.fillMaxWidth().testTag("editor_text_input"),
          )
          Text("${strings.fontSizeLabel}: ${selectedFontSize.toInt()} pt", style = MaterialTheme.typography.labelMedium)
          Slider(value = selectedFontSize, onValueChange = { selectedFontSize = it }, valueRange = 10f..44f)
          Text(strings.fontFamilyLabel, style = MaterialTheme.typography.labelMedium)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(EditorFont.entries) { font ->
              FilterChip(
                selected = selectedFont == font,
                onClick = { selectedFont = font },
                label = { Text(font.label) },
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (input.isNotBlank()) {
              pushAction(
                PdfEditAction.AddText(
                  id = UUID.randomUUID().toString(),
                  pageIndex = currentPage,
                  xNorm = normPos.x,
                  yNorm = normPos.y,
                  text = input,
                  fontSizeSp = selectedFontSize,
                  font = selectedFont,
                  colorArgb = selectedColor.toArgb(),
                )
              )
            }
            showTextDialogAt = null
          },
          modifier = Modifier.testTag("editor_text_confirm"),
        ) {
          Text(strings.apply)
        }
      },
      dismissButton = {
        TextButton(onClick = { showTextDialogAt = null }) { Text(strings.cancel) }
      },
    )
  }

  // Redact & Replace Dialog
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
