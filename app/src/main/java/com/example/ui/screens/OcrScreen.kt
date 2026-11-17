package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.AppContainer
import com.example.core.i18n.LocalStrings
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentEntity
import com.example.domain.engine.OcrPageOutput
import com.example.domain.model.OcrLanguageChoice
import com.example.ui.components.BusyOverlay
import com.example.ui.components.PdfPickerSheet
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
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

  var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var language by remember { mutableStateOf(OcrLanguageChoice.ENGLISH) }
  var banglaReady by remember { mutableStateOf(container.ocrEngine.isLanguageReady(OcrLanguageChoice.BANGLA)) }
  var ocrPages by remember { mutableStateOf<List<OcrPageOutput>>(emptyList()) }
  var searchQuery by remember { mutableStateOf("") }
  var showPicker by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }
  var progressMsg by remember { mutableStateOf<String?>(null) }
  var refreshModelTick by remember { mutableIntStateOf(0) }

  LaunchedEffect(preselectedDocId) {
    if (preselectedDocId > 0) {
      selectedDoc = container.documentRepository.getDocumentById(preselectedDocId)
    }
  }

  LaunchedEffect(refreshModelTick) {
    banglaReady = container.ocrEngine.isLanguageReady(OcrLanguageChoice.BANGLA)
  }

  val importModelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      scope.launch {
        isBusy = true
        container.ocrEngine.importTrainedData(uri, OcrLanguageChoice.BANGLA)
          .onSuccess {
            refreshModelTick++
            onNotify(strings.ocrModelAvailable)
          }
          .onFailure { onNotify(it.message ?: strings.errorGeneric) }
        isBusy = false
      }
    }
  }

  val exportTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
    if (uri != null) {
      val combined = ocrPages.joinToString("\n\n--- Page ---\n\n") { it.text }
      val ok = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
          out.write(combined.toByteArray(Charsets.UTF_8))
        }
        true
      }.getOrDefault(false)
      onNotify(if (ok) strings.savedSuccess else strings.errorGeneric)
    }
  }

  val combinedText = remember(ocrPages, searchQuery) {
    val full = ocrPages.joinToString("\n\n") { "--- ${strings.pageLabel} ${it.pageIndex + 1} ---\n${it.text}" }
    if (searchQuery.isBlank()) {
      full
    } else {
      ocrPages.filter { it.text.contains(searchQuery, ignoreCase = true) }
        .joinToString("\n\n") { "--- ${strings.pageLabel} ${it.pageIndex + 1} ---\n${it.text}" }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.toolOcrScanner) },
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Button(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("ocr_pick_pdf_btn"),
          ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(selectedDoc?.displayName ?: strings.selectFile)
          }
        }

        item {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(strings.ocrLanguage, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                  selected = language == OcrLanguageChoice.ENGLISH,
                  onClick = { language = OcrLanguageChoice.ENGLISH },
                  label = { Text(strings.ocrEnglish) },
                  modifier = Modifier.testTag("ocr_lang_en"),
                )
                FilterChip(
                  selected = language == OcrLanguageChoice.BANGLA,
                  onClick = { language = OcrLanguageChoice.BANGLA },
                  label = { Text(strings.ocrBangla) },
                  modifier = Modifier.testTag("ocr_lang_bn"),
                )
              }

              if (language == OcrLanguageChoice.BANGLA && !banglaReady) {
                Text(
                  text = strings.ocrModelMissingBn,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = { importModelLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                  ) {
                    Text(strings.importTrainedData)
                  }
                  Button(
                    onClick = {
                      scope.launch {
                        isBusy = true
                        progressMsg = strings.processing
                        container.ocrEngine.downloadTrainedData(OcrLanguageChoice.BANGLA)
                          .onSuccess {
                            refreshModelTick++
                            onNotify(strings.ocrModelAvailable)
                          }
                          .onFailure { onNotify(it.message ?: strings.errorGeneric) }
                        isBusy = false
                        progressMsg = null
                      }
                    },
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.downloadBanglaModel)
                  }
                }
              } else {
                Text(
                  text = strings.ocrModelAvailable,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }

              Button(
                onClick = {
                  val d = selectedDoc ?: return@Button
                  scope.launch {
                    isBusy = true
                    container.ocrEngine.recognizePdf(
                      pdfFile = File(d.filePath),
                      language = language,
                      onProgress = { cur, tot -> progressMsg = "${strings.processing} ($cur / $tot)" },
                    ).onSuccess { res ->
                      ocrPages = res
                      if (res.all { it.text.isBlank() }) onNotify(strings.ocrNoTextFound)
                    }.onFailure { err ->
                      val msg = if (err.message == "MISSING_MODEL_BEN") strings.ocrModelMissingBn else (err.message ?: strings.errorGeneric)
                      onNotify(msg)
                    }
                    isBusy = false
                    progressMsg = null
                  }
                },
                enabled = selectedDoc != null && (language == OcrLanguageChoice.ENGLISH || banglaReady),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("ocr_run_btn"),
              ) {
                Icon(Icons.Filled.TextFields, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.runOcr)
              }
            }
          }
        }

        if (ocrPages.isNotEmpty()) {
          item {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              label = { Text(strings.searchHint) },
              leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }

          item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = {
                  val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  cm.setPrimaryClip(ClipData.newPlainText("OCR Text", combinedText))
                  onNotify(strings.success)
                },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.copyText)
              }
              OutlinedButton(
                onClick = {
                  val base = selectedDoc?.displayName?.removeSuffix(".pdf") ?: "ocr"
                  exportTxtLauncher.launch("${base}_ocr.txt")
                },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.exportTxt)
              }
            }
            Spacer(Modifier.height(8.dp))
            Button(
              onClick = {
                val d = selectedDoc ?: return@Button
                scope.launch {
                  isBusy = true
                  val outFile = container.documentRepository.createVaultFile("searchable_${d.displayName}")
                  container.ocrEngine.createSearchablePdf(File(d.filePath), ocrPages, outFile)
                    .onSuccess { pdf ->
                      val reg = container.documentRepository.registerPdfFile(pdf)
                      isBusy = false
                      onNotify(strings.savedSuccess)
                      onOpenViewer(reg.id)
                    }
                    .onFailure { err ->
                      isBusy = false
                      onNotify(err.message ?: strings.errorGeneric)
                    }
                }
              },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("ocr_searchable_pdf_btn"),
            ) {
              Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(strings.createSearchablePdf)
            }
          }

          item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
              Text(
                text = combinedText.ifBlank { strings.noMatches },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp),
              )
            }
          }
        }
      }
      BusyOverlay(isBusy, progressMsg)
    }
  }

  if (showPicker) {
    PdfPickerSheet(
      documents = allDocuments,
      onSelectDoc = { selectedDoc = it; ocrPages = emptyList() },
      onImportUri = { uri -> onImportPdf(uri) { selectedDoc = it; ocrPages = emptyList() } },
      onDismiss = { showPicker = false },
    )
  }
}
