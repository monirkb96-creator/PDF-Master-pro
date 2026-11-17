package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.AppContainer
import com.example.core.i18n.LocalStrings
import com.example.data.local.PdfDocumentEntity
import com.example.domain.model.ScannedPageItem
import com.example.domain.model.ScannerFilter
import com.example.ui.components.BusyOverlay
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
  container: AppContainer,
  onOpenViewer: (Long) -> Unit,
  onBack: () -> Unit,
  onNotify: (String) -> Unit,
) {
  val strings = LocalStrings.current
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()

  val pages = remember { mutableStateListOf<ScannedPageItem>() }
  var selectedIdx by remember { mutableIntStateOf(0) }
  var showCamera by remember { mutableStateOf(false) }
  var showCropControls by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasCameraPermission = granted
    if (granted) {
      showCamera = true
    } else {
      onNotify(strings.cameraPermissionNeeded)
    }
  }

  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
    uris.forEachIndexed { idx, uri ->
      runCatching {
        val dest = File(context.cacheDir, "scan_${System.currentTimeMillis()}_$idx.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
          FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        pages.add(ScannedPageItem(id = UUID.randomUUID().toString(), rawImagePath = dest.absolutePath))
        selectedIdx = pages.lastIndex
      }
    }
  }

  var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.toolScanner) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
          }
        },
        actions = {
          if (pages.isNotEmpty() && !showCamera) {
            Button(
              onClick = {
                scope.launch {
                  isBusy = true
                  val outFile = container.documentRepository.createVaultFile("scan_${System.currentTimeMillis() % 10000}.pdf")
                  container.scannerEngine.exportScannedPagesToPdf(pages.toList(), outFile)
                    .onSuccess { pdf ->
                      val registered: PdfDocumentEntity = container.documentRepository.registerPdfFile(pdf)
                      isBusy = false
                      onNotify(strings.savedSuccess)
                      onOpenViewer(registered.id)
                    }
                    .onFailure { err ->
                      isBusy = false
                      onNotify(err.message ?: strings.errorGeneric)
                    }
                }
              },
              modifier = Modifier.padding(end = 8.dp).testTag("scanner_save_pdf_btn"),
            ) {
              Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(strings.save)
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (showCamera && hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
              val previewView = PreviewView(ctx)
              val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
              cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                  it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                  .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                  .build()
                imageCapture = capture
                runCatching {
                  provider.unbindAll()
                  provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                }
              }, ContextCompat.getMainExecutor(ctx))
              previewView
            },
          )

          Row(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .background(Color.Black.copy(alpha = 0.55f))
              .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedButton(onClick = { showCamera = false }) {
              Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
              Spacer(Modifier.width(6.dp))
              Text(strings.close, color = Color.White)
            }
            Button(
              onClick = {
                val cap = imageCapture ?: return@Button
                val outFile = File(context.cacheDir, "cam_scan_${System.currentTimeMillis()}.jpg")
                val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                cap.takePicture(
                  opts,
                  ContextCompat.getMainExecutor(context),
                  object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                      pages.add(ScannedPageItem(id = UUID.randomUUID().toString(), rawImagePath = outFile.absolutePath))
                      selectedIdx = pages.lastIndex
                      showCamera = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                      onNotify(exception.message ?: strings.errorGeneric)
                    }
                  },
                )
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("scanner_capture_btn"),
            ) {
              Icon(Icons.Filled.CameraAlt, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text(strings.capturePage)
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = {
                  if (hasCameraPermission) showCamera = true
                  else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("scanner_open_camera_btn"),
              ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(strings.openCamera)
              }
              OutlinedButton(
                onClick = {
                  photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("scanner_import_photos_btn"),
              ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(strings.importPhotos)
              }
            }
          }

          if (pages.isNotEmpty()) {
            val active = pages.getOrNull(selectedIdx)
            item {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(pages, key = { _, p -> p.id }) { idx, p ->
                  val isSel = idx == selectedIdx
                  Box(
                    modifier = Modifier
                      .size(68.dp, 88.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .border(
                        width = if (isSel) 2.5.dp else 1.dp,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp),
                      )
                      .clickable { selectedIdx = idx },
                  ) {
                    AsyncImage(
                      model = File(p.rawImagePath),
                      contentDescription = "${strings.pageLabel} ${idx + 1}",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  }
                }
              }
            }

            if (active != null) {
              item {
                val processedBmp by produceState<Bitmap?>(initialValue = null, active) {
                  value = container.scannerEngine.processScannedBitmap(active, maxDimension = 1000)
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(290.dp)
                      .padding(10.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    val bmp = processedBmp
                    if (bmp != null) {
                      Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = strings.previewLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                      )
                    } else {
                      CircularProgressIndicator()
                    }
                  }
                }
              }

              item {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = {
                      pages[selectedIdx] = active.copy(rotationDegrees = (active.rotationDegrees + 90) % 360)
                    },
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.rotate90)
                  }
                  OutlinedButton(
                    onClick = { showCropControls = !showCropControls },
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Filled.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.cropEdges)
                  }
                  IconButton(
                    onClick = {
                      pages.removeAt(selectedIdx)
                      selectedIdx = (selectedIdx - 1).coerceAtLeast(0)
                    },
                  ) {
                    Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error)
                  }
                }
              }

              if (showCropControls) {
                item {
                  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                      Text(strings.cropEdges, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                      Text("Left: ${(active.cropLeft * 100).toInt()}%  Right: ${(active.cropRight * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                      Slider(value = active.cropLeft, onValueChange = { pages[selectedIdx] = active.copy(cropLeft = it.coerceAtMost(active.cropRight - 0.15f)) }, valueRange = 0f..0.45f)
                      Slider(value = active.cropRight, onValueChange = { pages[selectedIdx] = active.copy(cropRight = it.coerceAtLeast(active.cropLeft + 0.15f)) }, valueRange = 0.55f..1f)
                      Text("Top: ${(active.cropTop * 100).toInt()}%  Bottom: ${(active.cropBottom * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                      Slider(value = active.cropTop, onValueChange = { pages[selectedIdx] = active.copy(cropTop = it.coerceAtMost(active.cropBottom - 0.15f)) }, valueRange = 0f..0.45f)
                      Slider(value = active.cropBottom, onValueChange = { pages[selectedIdx] = active.copy(cropBottom = it.coerceAtLeast(active.cropTop + 0.15f)) }, valueRange = 0.55f..1f)
                    }
                  }
                }
              }

              item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      listOf(
                        ScannerFilter.ORIGINAL to strings.filterOriginal,
                        ScannerFilter.GRAYSCALE to strings.filterGrayscale,
                        ScannerFilter.BLACK_AND_WHITE to strings.filterBw,
                      ).forEach { (flt, label) ->
                        FilterChip(
                          selected = active.filter == flt,
                          onClick = { pages[selectedIdx] = active.copy(filter = flt) },
                          label = { Text(label) },
                        )
                      }
                    }
                    Text("${strings.brightness}: ${active.brightness.toInt()}", style = MaterialTheme.typography.labelMedium)
                    Slider(
                      value = active.brightness,
                      onValueChange = { pages[selectedIdx] = active.copy(brightness = it) },
                      valueRange = -80f..80f,
                    )
                    Text("${strings.contrast}: ${String.format("%.1fx", active.contrast)}", style = MaterialTheme.typography.labelMedium)
                    Slider(
                      value = active.contrast,
                      onValueChange = { pages[selectedIdx] = active.copy(contrast = it) },
                      valueRange = 0.5f..2.0f,
                    )
                  }
                }
              }
            }
          } else {
            item {
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                  Spacer(Modifier.height(10.dp))
                  Text(strings.cameraPermissionNeeded, style = MaterialTheme.typography.bodyMedium)
                }
              }
            }
          }
        }
      }
      BusyOverlay(isBusy)
    }
  }
}
