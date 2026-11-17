package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.AppContainer
import com.example.core.i18n.LocalStrings
import com.example.data.local.PdfDocumentEntity
import com.example.domain.model.WatermarkPosition
import com.example.ui.components.BusyOverlay
import com.example.ui.components.PdfPickerSheet
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureWatermarkScreen(
  initialTab: Int,
  preselectedDocId: Long,
  allDocuments: List<PdfDocumentEntity>,
  container: AppContainer,
  onImportPdf: (Uri, (PdfDocumentEntity) -> Unit) -> Unit,
  onOpenViewer: (Long) -> Unit,
  onBack: () -> Unit,
  onNotify: (String) -> Unit,
) {
  val strings = LocalStrings.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
  var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var showPicker by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }

  // Signature state
  val sigStrokes = remember { mutableStateListOf<List<Offset>>() }
  val activeStroke = remember { mutableStateListOf<Offset>() }
  var sigCanvasSize by remember { mutableStateOf(IntSize(600, 260)) }
  var uploadedSigBmp by remember { mutableStateOf<Bitmap?>(null) }
  var sigColor by remember { mutableStateOf(Color(0xFF0F172A)) }
  var sigPosX by remember { mutableFloatStateOf(0.55f) }
  var sigPosY by remember { mutableFloatStateOf(0.75f) }
  var sigWidthNorm by remember { mutableFloatStateOf(0.32f) }
  var applyAllPages by remember { mutableStateOf(false) }
  var targetPages by remember { mutableStateOf<Set<Int>>(setOf(0)) }

  // Watermark state
  var wmTextMode by remember { mutableStateOf(true) }
  var wmText by remember { mutableStateOf("CONFIDENTIAL") }
  var wmImageFile by remember { mutableStateOf<File?>(null) }
  var wmOpacity by remember { mutableFloatStateOf(0.32f) }
  var wmScale by remember { mutableFloatStateOf(1.0f) }
  var wmRotation by remember { mutableFloatStateOf(45f) }
  var wmPosition by remember { mutableStateOf(WatermarkPosition.DIAGONAL) }
  var wmColor by remember { mutableStateOf(Color(0xFF155EEF)) }

  LaunchedEffect(preselectedDocId) {
    if (preselectedDocId > 0) {
      selectedDoc = container.documentRepository.getDocumentById(preselectedDocId)
    }
  }

  val sigImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      context.contentResolver.openInputStream(uri)?.use { input ->
        uploadedSigBmp = BitmapFactory.decodeStream(input)
      }
    }
  }

  val wmImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      val dest = File(context.cacheDir, "wm_${System.currentTimeMillis()}.png")
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
      }
      wmImageFile = dest
      wmTextMode = false
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (selectedTab == 0) strings.toolSignature else strings.toolWatermark) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
          }
        },
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text(strings.toolSignature) },
            icon = { Icon(Icons.Filled.Draw, contentDescription = null) },
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text(strings.toolWatermark) },
            icon = { Icon(Icons.Filled.BrandingWatermark, contentDescription = null) },
          )
        }

        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Button(
              onClick = { showPicker = true },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("sign_wm_pick_btn"),
            ) {
              Icon(Icons.Filled.FolderOpen, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(selectedDoc?.displayName ?: strings.selectFile)
            }
          }

          selectedDoc?.let { doc ->
            item {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = applyAllPages, onCheckedChange = { applyAllPages = it })
                Text(strings.allPages, style = MaterialTheme.typography.bodyMedium)
              }
              if (!applyAllPages && doc.pageCount > 0) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  items((0 until doc.pageCount).toList()) { idx ->
                    FilterChip(
                      selected = targetPages.contains(idx),
                      onClick = {
                        targetPages = if (targetPages.contains(idx)) targetPages - idx else targetPages + idx
                      },
                      label = { Text("${strings.pageLabel} ${idx + 1}") },
                    )
                  }
                }
              }
            }
          }

          if (selectedTab == 0) {
            item {
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(strings.drawSignature, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      listOf(Color(0xFF0F172A), Color(0xFF155EEF), Color(0xFFDC2626)).forEach { c ->
                        Box(
                          modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (sigColor == c) 2.dp else 1.dp, Color.Gray, CircleShape)
                            .clickable { sigColor = c },
                        )
                      }
                    }
                  }

                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(170.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .background(Color.White)
                      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                      .onSizeChanged { sigCanvasSize = it }
                      .pointerInput(Unit) {
                        detectDragGestures(
                          onDragStart = { start ->
                            uploadedSigBmp = null
                            activeStroke.clear()
                            activeStroke.add(start)
                          },
                          onDrag = { change, _ ->
                            change.consume()
                            activeStroke.add(change.position)
                          },
                          onDragEnd = {
                            if (activeStroke.size >= 2) sigStrokes.add(activeStroke.toList())
                            activeStroke.clear()
                          },
                        )
                      },
                  ) {
                    val up = uploadedSigBmp
                    if (up != null) {
                      Image(
                        bitmap = up.asImageBitmap(),
                        contentDescription = strings.drawSignature,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                      )
                    } else {
                      Canvas(modifier = Modifier.fillMaxSize()) {
                        for (s in sigStrokes) {
                          if (s.size < 2) continue
                          val p = Path().apply {
                            moveTo(s.first().x, s.first().y)
                            s.drop(1).forEach { lineTo(it.x, it.y) }
                          }
                          drawPath(p, sigColor, style = Stroke(5f))
                        }
                        if (activeStroke.size >= 2) {
                          val p = Path().apply {
                            moveTo(activeStroke.first().x, activeStroke.first().y)
                            activeStroke.drop(1).forEach { lineTo(it.x, it.y) }
                          }
                          drawPath(p, sigColor, style = Stroke(5f))
                        }
                      }
                    }
                  }

                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                      onClick = { sigStrokes.clear(); uploadedSigBmp = null },
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text(strings.clear)
                    }
                    OutlinedButton(
                      onClick = {
                        sigImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                      },
                      modifier = Modifier.weight(1f),
                    ) {
                      Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text(strings.uploadSignature)
                    }
                  }

                  Text("${strings.signatureSize}: ${(sigWidthNorm * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                  Slider(value = sigWidthNorm, onValueChange = { sigWidthNorm = it }, valueRange = 0.15f..0.65f)
                }
              }
            }

            // Placement preview on page 1
            selectedDoc?.let { doc ->
              item {
                val pageBmp by produceState<Bitmap?>(initialValue = null, doc.filePath) {
                  value = container.pdfRendererHelper.renderPage(File(doc.filePath), 0, targetWidthPx = 720)
                }
                pageBmp?.let { bmp ->
                  val sigPreview = remember(sigStrokes.size, uploadedSigBmp, sigColor) {
                    uploadedSigBmp ?: if (sigStrokes.isNotEmpty()) {
                      container.pdfEngine.renderSignatureStrokesToBitmap(
                        sigStrokes.toList(),
                        sigCanvasSize.width.toFloat(),
                        sigCanvasSize.height.toFloat(),
                        sigColor.toArgb(),
                      )
                    } else null
                  }
                  BoxWithConstraints(
                    modifier = Modifier
                      .fillMaxWidth()
                      .aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1))
                      .clip(RoundedCornerShape(8.dp))
                      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                      .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                          change.consume()
                          sigPosX = (sigPosX + dragAmount.x / size.width).coerceIn(0f, 1f - sigWidthNorm)
                          sigPosY = (sigPosY + dragAmount.y / size.height).coerceIn(0f, 0.9f)
                        }
                      },
                  ) {
                    Image(bmp.asImageBitmap(), contentDescription = strings.previewLabel, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    sigPreview?.let { sp ->
                      Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = sigWidthNorm * size.width
                        val h = w * (sp.height.toFloat() / sp.width.coerceAtLeast(1))
                        drawImage(
                          image = sp.asImageBitmap(),
                          dstOffset = IntOffset((sigPosX * size.width).toInt(), (sigPosY * size.height).toInt()),
                          dstSize = IntSize(w.toInt().coerceAtLeast(20), h.toInt().coerceAtLeast(10)),
                        )
                      }
                    }
                  }
                }
              }
            }

            item {
              Button(
                onClick = {
                  val d = selectedDoc ?: return@Button
                  val bmp = uploadedSigBmp ?: if (sigStrokes.isNotEmpty()) {
                    container.pdfEngine.renderSignatureStrokesToBitmap(
                      sigStrokes.toList(),
                      sigCanvasSize.width.toFloat(),
                      sigCanvasSize.height.toFloat(),
                      sigColor.toArgb(),
                    )
                  } else null
                  if (bmp == null) {
                    onNotify(strings.drawSignature)
                    return@Button
                  }
                  scope.launch {
                    isBusy = true
                    val out = container.documentRepository.createVaultFile("signed_${d.displayName}")
                    val pagesArg = if (applyAllPages) emptySet() else targetPages
                    container.pdfEngine.applySignature(File(d.filePath), out, bmp, pagesArg, sigPosX, sigPosY, sigWidthNorm)
                      .onSuccess { file ->
                        val reg = container.documentRepository.registerPdfFile(file)
                        isBusy = false
                        onNotify(strings.savedSuccess)
                        onOpenViewer(reg.id)
                      }
                      .onFailure {
                        isBusy = false
                        onNotify(it.message ?: strings.errorGeneric)
                      }
                  }
                },
                enabled = selectedDoc != null && (sigStrokes.isNotEmpty() || uploadedSigBmp != null),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("sign_save_btn"),
              ) {
                Text(strings.saveSignedPdf)
              }
            }
          } else {
            // WATERMARK TAB
            item {
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                      selected = wmTextMode,
                      onClick = { wmTextMode = true },
                      label = { Text(strings.watermarkTextMode) },
                    )
                    FilterChip(
                      selected = !wmTextMode,
                      onClick = {
                        wmImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                      },
                      label = { Text(strings.watermarkImageMode) },
                    )
                  }

                  if (wmTextMode) {
                    OutlinedTextField(
                      value = wmText,
                      onValueChange = { wmText = it },
                      label = { Text(strings.watermarkTextHint) },
                      singleLine = true,
                      modifier = Modifier.fillMaxWidth().testTag("wm_text_input"),
                    )
                  } else {
                    Text(wmImageFile?.name ?: strings.uploadSignature, style = MaterialTheme.typography.bodySmall)
                  }

                  Text("${strings.opacityLabel}: ${(wmOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                  Slider(value = wmOpacity, onValueChange = { wmOpacity = it }, valueRange = 0.08f..0.95f)

                  Text("${strings.fontSizeLabel}: ${(wmScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                  Slider(value = wmScale, onValueChange = { wmScale = it }, valueRange = 0.4f..2.0f)

                  Text("${strings.rotationLabel}: ${wmRotation.toInt()}°", style = MaterialTheme.typography.labelMedium)
                  Slider(value = wmRotation, onValueChange = { wmRotation = it }, valueRange = -90f..90f)

                  Text(strings.positionLabel, style = MaterialTheme.typography.labelMedium)
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                      listOf(
                        WatermarkPosition.CENTER to strings.posCenter,
                        WatermarkPosition.DIAGONAL to strings.posDiagonal,
                        WatermarkPosition.TOP to strings.posTop,
                        WatermarkPosition.BOTTOM to strings.posBottom,
                      )
                    ) { (pos, label) ->
                      FilterChip(
                        selected = wmPosition == pos,
                        onClick = { wmPosition = pos },
                        label = { Text(label) },
                      )
                    }
                  }

                  Button(
                    onClick = {
                      val d = selectedDoc ?: return@Button
                      scope.launch {
                        isBusy = true
                        val out = container.documentRepository.createVaultFile("watermarked_${d.displayName}")
                        val pagesArg = if (applyAllPages) emptySet() else targetPages
                        container.pdfEngine.applyWatermark(
                          sourceFile = File(d.filePath),
                          outputFile = out,
                          textWatermark = if (wmTextMode) wmText else null,
                          imageWatermarkFile = if (!wmTextMode) wmImageFile else null,
                          opacity = wmOpacity,
                          sizeScale = wmScale,
                          rotationDeg = wmRotation,
                          position = wmPosition,
                          selectedPagesZeroBased = pagesArg,
                          colorArgb = wmColor.toArgb(),
                        ).onSuccess { file ->
                          val reg = container.documentRepository.registerPdfFile(file)
                          isBusy = false
                          onNotify(strings.savedSuccess)
                          onOpenViewer(reg.id)
                        }.onFailure {
                          isBusy = false
                          onNotify(it.message ?: strings.errorGeneric)
                        }
                      }
                    },
                    enabled = selectedDoc != null && ((wmTextMode && wmText.isNotBlank()) || (!wmTextMode && wmImageFile != null)),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("wm_apply_btn"),
                  ) {
                    Text(strings.applyWatermark)
                  }
                }
              }
            }
          }
        }
      }
      BusyOverlay(isBusy)
    }
  }

  if (showPicker) {
    PdfPickerSheet(
      documents = allDocuments,
      onSelectDoc = { selectedDoc = it; targetPages = setOf(0) },
      onImportUri = { uri -> onImportPdf(uri) { selectedDoc = it; targetPages = setOf(0) } },
      onDismiss = { showPicker = false },
    )
  }
}
