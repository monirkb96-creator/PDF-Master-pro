package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
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
import com.example.domain.model.CompressionLevel
import com.example.domain.model.CompressionResult
import com.example.ui.components.BusyOverlay
import com.example.ui.components.PdfPickerSheet
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(
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
  var level by remember { mutableStateOf(CompressionLevel.MEDIUM) }
  var result by remember { mutableStateOf<CompressionResult?>(null) }
  var registeredResultDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var showPicker by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }

  LaunchedEffect(preselectedDocId) {
    if (preselectedDocId > 0) {
      selectedDoc = container.documentRepository.getDocumentById(preselectedDocId)
    }
  }

  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
    val file = result?.outputFile
    if (uri != null && file != null) {
      val ok = FileUtils.copyFileToUri(context, file, uri)
      onNotify(if (ok) strings.savedSuccess else strings.errorGeneric)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.toolCompressPdf) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
          }
        },
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Button(
          onClick = { showPicker = true },
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("compress_pick_btn"),
        ) {
          Icon(Icons.Filled.FolderOpen, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(selectedDoc?.displayName ?: strings.selectFile)
        }

        selectedDoc?.let { doc ->
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(
                "${strings.originalSize}: ${FileUtils.formatBytes(doc.sizeBytes)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                  CompressionLevel.LOW to strings.compressionLow,
                  CompressionLevel.MEDIUM to strings.compressionMedium,
                  CompressionLevel.HIGH to strings.compressionHigh,
                ).forEach { (lvl, label) ->
                  FilterChip(
                    selected = level == lvl,
                    onClick = { level = lvl },
                    label = { Text(label) },
                    modifier = Modifier.testTag("compress_level_${lvl.name.lowercase()}"),
                  )
                }
              }

              Button(
                onClick = {
                  scope.launch {
                    isBusy = true
                    val outFile = container.documentRepository.createVaultFile("compressed_${doc.displayName}")
                    val res = container.pdfEngine.compressPdf(File(doc.filePath), outFile, level)
                    res.onSuccess { cr ->
                      result = cr
                      registeredResultDoc = container.documentRepository.registerPdfFile(cr.outputFile)
                      isBusy = false
                    }.onFailure { err ->
                      isBusy = false
                      onNotify(err.message ?: strings.errorGeneric)
                    }
                  }
                },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("compress_run_btn"),
              ) {
                Icon(Icons.Filled.Compress, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.compressNow)
              }
            }
          }
        }

        result?.let { cr ->
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("${strings.originalSize}: ${FileUtils.formatBytes(cr.originalBytes)}", style = MaterialTheme.typography.bodyMedium)
              Text("${strings.compressedSize}: ${FileUtils.formatBytes(cr.compressedBytes)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              if (cr.reduced) {
                Text(
                  "${strings.sizeReduction}: ${cr.reductionPercent}%",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
              } else {
                Text(
                  strings.noSizeReductionNote,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              Spacer(Modifier.height(6.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                registeredResultDoc?.let { rd ->
                  Button(onClick = { onOpenViewer(rd.id) }, modifier = Modifier.weight(1f)) {
                    Text(strings.openPdf)
                  }
                }
                OutlinedButton(
                  onClick = { exportLauncher.launch(cr.outputFile.name) },
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Filled.SaveAlt, contentDescription = null)
                  Spacer(Modifier.width(4.dp))
                  Text(strings.saveAs)
                }
                OutlinedButton(
                  onClick = { FileUtils.shareFile(context, cr.outputFile, "application/pdf", strings.share) },
                ) {
                  Icon(Icons.Filled.Share, contentDescription = strings.share)
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
      onSelectDoc = { selectedDoc = it; result = null },
      onImportUri = { uri -> onImportPdf(uri) { selectedDoc = it; result = null } },
      onDismiss = { showPicker = false },
    )
  }
}
