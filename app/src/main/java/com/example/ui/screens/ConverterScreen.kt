package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.AppContainer
import com.example.core.i18n.ExportQuality
import com.example.core.i18n.LocalStrings
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentEntity
import com.example.ui.components.BusyOverlay
import com.example.ui.components.PdfPickerSheet
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

/**
 * Modes:
 * 0 = PDF to Word (DOCX)
 * 1 = Word (DOCX) to PDF
 * 2 = PDF to JPG
 * 3 = PDF to PNG
 * 4 = Images (JPG/PNG) to PDF
 * 5 = Extract Embedded Images
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
  initialMode: Int,
  preselectedDocId: Long,
  exportQuality: ExportQuality,
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

  var mode by remember(initialMode) { mutableIntStateOf(initialMode.coerceIn(0, 5)) }
  var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var selectedDocxFile by remember { mutableStateOf<File?>(null) }
  var selectedImageFiles by remember { mutableStateOf<List<File>>(emptyList()) }
  var outputFiles by remember { mutableStateOf<List<File>>(emptyList()) }
  var outputPdfDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var showPdfPicker by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }
  var fileToSaveAs by remember { mutableStateOf<File?>(null) }

  LaunchedEffect(preselectedDocId) {
    if (preselectedDocId > 0) {
      selectedDoc = container.documentRepository.getDocumentById(preselectedDocId)
    }
  }

  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
    if (uris.isNotEmpty()) {
      val files = uris.mapIndexedNotNull { idx, uri ->
        runCatching {
          val dest = File(context.cacheDir, "conv_in_${System.currentTimeMillis()}_$idx.jpg")
          context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
          }
          dest
        }.getOrNull()
      }
      selectedImageFiles = files
      outputFiles = emptyList()
      outputPdfDoc = null
    }
  }

  val docxPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      val name = FileUtils.resolveDisplayName(context, uri, "document.docx")
      val dest = File(context.cacheDir, name)
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output) }
      }
      selectedDocxFile = dest
      outputFiles = emptyList()
      outputPdfDoc = null
    }
  }

  val saveAsDocxLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
  ) { uri ->
    val f = fileToSaveAs
    if (uri != null && f != null) {
      val ok = FileUtils.copyFileToUri(context, f, uri)
      onNotify(if (ok) strings.savedSuccess else strings.errorGeneric)
    }
    fileToSaveAs = null
  }

  val saveAsImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
    val f = fileToSaveAs
    if (uri != null && f != null) {
      val ok = FileUtils.copyFileToUri(context, f, uri)
      onNotify(if (ok) strings.savedSuccess else strings.errorGeneric)
    }
    fileToSaveAs = null
  }

  val modeLabels = listOf(
    0 to strings.toolPdfToWord,
    1 to strings.toolWordToPdf,
    2 to strings.toolPdfToJpg,
    3 to strings.toolPdfToPng,
    4 to strings.toolJpgToPdf,
    5 to strings.toolExtractImages,
  )

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(modeLabels.firstOrNull { it.first == mode }?.second ?: strings.convertNow) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
          }
        },
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(modeLabels) { (m, label) ->
              FilterChip(
                selected = mode == m,
                onClick = {
                  mode = m
                  outputFiles = emptyList()
                  outputPdfDoc = null
                },
                label = { Text(label) },
                modifier = Modifier.testTag("converter_mode_$m"),
              )
            }
          }
        }

        if (mode == 0 || mode == 1) {
          item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
              Text(
                text = strings.docxDisclaimer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
              )
            }
          }
        }

        item {
          when (mode) {
            0, 2, 3, 5 -> {
              Button(
                onClick = { showPdfPicker = true },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("converter_pick_pdf_btn"),
              ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(selectedDoc?.displayName ?: strings.selectFile)
              }
            }
            1 -> {
              Button(
                onClick = {
                  docxPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("converter_pick_docx_btn"),
              ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(selectedDocxFile?.name ?: strings.pickDocx)
              }
            }
            4 -> {
              Button(
                onClick = {
                  imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("converter_pick_images_btn"),
              ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                  if (selectedImageFiles.isEmpty()) strings.pickImages
                  else "${selectedImageFiles.size} ${strings.pickImages}"
                )
              }
            }
          }
        }

        item {
          val canRun = when (mode) {
            0, 2, 3, 5 -> selectedDoc != null
            1 -> selectedDocxFile != null
            4 -> selectedImageFiles.isNotEmpty()
            else -> false
          }
          Button(
            onClick = {
              scope.launch {
                isBusy = true
                when (mode) {
                  0 -> {
                    val d = selectedDoc!!
                    val out = container.documentRepository.createExportFile(d.displayName.removeSuffix(".pdf"), "docx")
                    container.docxEngine.pdfToDocx(File(d.filePath), out)
                      .onSuccess { outputFiles = listOf(it); onNotify(strings.success) }
                      .onFailure { onNotify(it.message ?: strings.errorGeneric) }
                  }
                  1 -> {
                    val docx = selectedDocxFile!!
                    val out = container.documentRepository.createVaultFile("${docx.nameWithoutExtension}.pdf")
                    container.docxEngine.docxToPdf(docx, out)
                      .onSuccess { pdf ->
                        outputPdfDoc = container.documentRepository.registerPdfFile(pdf)
                        outputFiles = listOf(pdf)
                        onNotify(strings.savedSuccess)
                      }
                      .onFailure { onNotify(it.message ?: strings.errorGeneric) }
                  }
                  2, 3 -> {
                    val d = selectedDoc!!
                    val fmt = if (mode == 2) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    val dir = File(container.documentRepository.exportsDir, "img_${System.currentTimeMillis()}")
                    val files = container.pdfRendererHelper.renderAllPagesToFiles(
                      pdfFile = File(d.filePath),
                      outputDir = dir,
                      baseName = d.displayName.removeSuffix(".pdf"),
                      format = fmt,
                      scaleFactor = exportQuality.renderScale,
                      quality = exportQuality.jpegQuality,
                    )
                    outputFiles = files
                    onNotify(String.format(strings.convertedFilesReady, files.size))
                  }
                  4 -> {
                    val out = container.documentRepository.createVaultFile("images_${System.currentTimeMillis() % 10000}.pdf")
                    container.pdfEngine.imagesToPdf(selectedImageFiles, out)
                      .onSuccess { pdf ->
                        outputPdfDoc = container.documentRepository.registerPdfFile(pdf)
                        outputFiles = listOf(pdf)
                        onNotify(strings.savedSuccess)
                      }
                      .onFailure { onNotify(it.message ?: strings.errorGeneric) }
                  }
                  5 -> {
                    val d = selectedDoc!!
                    val dir = File(container.documentRepository.exportsDir, "extract_${System.currentTimeMillis()}")
                    container.pdfEngine.extractEmbeddedImages(File(d.filePath), dir, d.displayName.removeSuffix(".pdf"))
                      .onSuccess { imgs ->
                        outputFiles = imgs
                        onNotify(
                          if (imgs.isEmpty()) strings.noEmbeddedImages
                          else String.format(strings.extractedImagesCount, imgs.size)
                        )
                      }
                      .onFailure { onNotify(it.message ?: strings.errorGeneric) }
                  }
                }
                isBusy = false
              }
            },
            enabled = canRun,
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("converter_run_btn"),
          ) {
            Icon(Icons.Filled.Transform, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(strings.convertNow)
          }
        }

        outputPdfDoc?.let { pdfDoc ->
          item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(pdfDoc.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                  Text("${pdfDoc.pageCount} ${strings.pagesUnit} • ${FileUtils.formatBytes(pdfDoc.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { onOpenViewer(pdfDoc.id) }) {
                  Text(strings.openPdf)
                }
              }
            }
          }
        }

        if (outputFiles.isNotEmpty() && outputPdfDoc == null) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                String.format(strings.convertedFilesReady, outputFiles.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              OutlinedButton(
                onClick = {
                  val mime = if (mode == 0) {
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  } else {
                    "image/*"
                  }
                  FileUtils.shareFiles(context, outputFiles, mime, strings.share)
                },
              ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.share)
              }
            }
          }

          items(outputFiles, key = { it.absolutePath }) { outFile ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (outFile.extension.lowercase() in listOf("jpg", "jpeg", "png")) {
                  AsyncImage(
                    model = outFile,
                    contentDescription = outFile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
                  )
                  Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(outFile.name, style = MaterialTheme.typography.titleSmall)
                  Text(FileUtils.formatBytes(outFile.length()), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                  onClick = {
                    fileToSaveAs = outFile
                    if (outFile.extension.lowercase() == "docx") {
                      saveAsDocxLauncher.launch(outFile.name)
                    } else {
                      saveAsImageLauncher.launch(outFile.name)
                    }
                  },
                ) {
                  Text(strings.saveAs)
                }
              }
            }
          }
        }
      }
      BusyOverlay(isBusy)
    }
  }

  if (showPdfPicker) {
    PdfPickerSheet(
      documents = allDocuments,
      onSelectDoc = { selectedDoc = it; outputFiles = emptyList(); outputPdfDoc = null },
      onImportUri = { uri -> onImportPdf(uri) { selectedDoc = it; outputFiles = emptyList(); outputPdfDoc = null } },
      onDismiss = { showPdfPicker = false },
    )
  }
}
