package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.AppContainer
import com.example.core.i18n.LocalStrings
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentEntity
import com.example.domain.model.SplitMode
import com.example.ui.components.BusyOverlay
import com.example.ui.components.PdfPageBitmapView
import com.example.ui.components.PdfPickerSheet
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeSplitScreen(
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
  val scope = rememberCoroutineScope()
  var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
  var isBusy by remember { mutableStateOf(false) }

  // Merge state
  val mergeQueue = remember { mutableStateListOf<PdfDocumentEntity>() }
  var mergeOutputName by remember { mutableStateOf("merged_${System.currentTimeMillis() % 10000}.pdf") }
  var showMergeVaultSheet by remember { mutableStateOf(false) }

  // Split state
  var splitDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var splitMode by remember { mutableStateOf(SplitMode.SELECTED_PAGES) }
  var selectedPages by remember { mutableStateOf<Set<Int>>(setOf(0)) }
  var rangeStartText by remember { mutableStateOf("1") }
  var rangeEndText by remember { mutableStateOf("1") }
  var chunkSizeText by remember { mutableStateOf("2") }
  var showSplitPicker by remember { mutableStateOf(false) }
  var splitResults by remember { mutableStateOf<List<PdfDocumentEntity>>(emptyList()) }

  LaunchedEffect(preselectedDocId, allDocuments) {
    if (preselectedDocId > 0) {
      val doc = container.documentRepository.getDocumentById(preselectedDocId)
      if (doc != null) {
        if (selectedTab == 0 && mergeQueue.none { it.id == doc.id }) mergeQueue.add(doc)
        if (selectedTab == 1 && splitDoc == null) {
          splitDoc = doc
          rangeEndText = doc.pageCount.coerceAtLeast(1).toString()
        }
      }
    }
  }

  val multiImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    uris.forEach { uri ->
      onImportPdf(uri) { imported ->
        if (mergeQueue.none { it.id == imported.id }) mergeQueue.add(imported)
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (selectedTab == 0) strings.toolMergePdf else strings.toolSplitPdf) },
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
            text = { Text(strings.toolMergePdf) },
            icon = { Icon(Icons.Filled.CallMerge, contentDescription = null) },
            modifier = Modifier.testTag("tab_merge"),
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text(strings.toolSplitPdf) },
            icon = { Icon(Icons.Filled.CallSplit, contentDescription = null) },
            modifier = Modifier.testTag("tab_split"),
          )
        }

        if (selectedTab == 0) {
          // MERGE WORKSPACE
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            item {
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                  onClick = { multiImportLauncher.launch(arrayOf("application/pdf")) },
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("merge_import_btn"),
                ) {
                  Icon(Icons.Filled.FolderOpen, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text(strings.importFromDevice)
                }
                OutlinedButton(
                  onClick = { showMergeVaultSheet = true },
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("merge_vault_btn"),
                ) {
                  Icon(Icons.Filled.Add, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text(strings.selectFromVault)
                }
              }
            }

            itemsIndexed(mergeQueue, key = { idx, item -> "${item.id}_$idx" }) { index, item ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Spacer(Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                      "${item.pageCount} ${strings.pagesUnit} • ${FileUtils.formatBytes(item.sizeBytes)}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  IconButton(
                    onClick = {
                      if (index > 0) {
                        val moved = mergeQueue.removeAt(index)
                        mergeQueue.add(index - 1, moved)
                      }
                    },
                    enabled = index > 0,
                  ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = strings.moveUp)
                  }
                  IconButton(
                    onClick = {
                      if (index < mergeQueue.lastIndex) {
                        val moved = mergeQueue.removeAt(index)
                        mergeQueue.add(index + 1, moved)
                      }
                    },
                    enabled = index < mergeQueue.lastIndex,
                  ) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = strings.moveDown)
                  }
                  IconButton(onClick = { mergeQueue.removeAt(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = strings.remove)
                  }
                }
              }
            }

            item {
              OutlinedTextField(
                value = mergeOutputName,
                onValueChange = { mergeOutputName = it },
                label = { Text(strings.outputFileName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("merge_output_name"),
              )
              Spacer(Modifier.height(10.dp))
              Button(
                onClick = {
                  if (mergeQueue.size < 2) {
                    onNotify(strings.needTwoPdfs)
                    return@Button
                  }
                  scope.launch {
                    isBusy = true
                    val outFile = container.documentRepository.createVaultFile(mergeOutputName)
                    val res = container.pdfEngine.mergePdfs(mergeQueue.map { File(it.filePath) }, outFile)
                    res.onSuccess { mergedFile ->
                      val registered = container.documentRepository.registerPdfFile(mergedFile)
                      isBusy = false
                      onNotify(strings.savedSuccess)
                      onOpenViewer(registered.id)
                    }.onFailure { err ->
                      isBusy = false
                      onNotify(err.message ?: strings.errorGeneric)
                    }
                  }
                },
                enabled = mergeQueue.size >= 2,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("merge_execute_btn"),
              ) {
                Icon(Icons.Filled.CallMerge, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.mergeNow)
              }
            }
          }
        } else {
          // SPLIT WORKSPACE
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            item {
              Button(
                onClick = { showSplitPicker = true },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("split_pick_pdf_btn"),
              ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(splitDoc?.displayName ?: strings.selectFile)
              }
            }

            val sDoc = splitDoc
            if (sDoc != null) {
              item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  listOf(
                    SplitMode.EVERY_PAGE to strings.splitModeEvery,
                    SplitMode.SELECTED_PAGES to strings.splitModeSelected,
                    SplitMode.PAGE_RANGE to strings.splitModeRange,
                    SplitMode.CHUNKS to strings.splitModeChunks,
                  ).forEach { (mode, label) ->
                    item {
                      FilterChip(
                        selected = splitMode == mode,
                        onClick = { splitMode = mode },
                        label = { Text(label) },
                        modifier = Modifier.testTag("split_mode_${mode.name.lowercase()}"),
                      )
                    }
                  }
                }
              }

              if (splitMode == SplitMode.SELECTED_PAGES && sDoc.pageCount > 0) {
                item {
                  Text(strings.selectedPages, style = MaterialTheme.typography.titleSmall)
                  Spacer(Modifier.height(8.dp))
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items((0 until sDoc.pageCount).toList()) { pageIdx ->
                      val picked = selectedPages.contains(pageIdx)
                      Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                          .width(88.dp)
                          .clip(RoundedCornerShape(10.dp))
                          .border(
                            width = if (picked) 2.5.dp else 1.dp,
                            color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(10.dp),
                          )
                          .clickable {
                            selectedPages = if (picked) selectedPages - pageIdx else selectedPages + pageIdx
                          }
                          .padding(6.dp),
                      ) {
                        Box(modifier = Modifier.height(106.dp).fillMaxWidth()) {
                          PdfPageBitmapView(
                            pdfRendererHelper = container.pdfRendererHelper,
                            file = File(sDoc.filePath),
                            pageIndex = pageIdx,
                            targetWidthPx = 220,
                          )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${strings.pageLabel} ${pageIdx + 1}", style = MaterialTheme.typography.labelSmall)
                      }
                    }
                  }
                }
              }

              if (splitMode == SplitMode.PAGE_RANGE) {
                item {
                  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                      value = rangeStartText,
                      onValueChange = { rangeStartText = it.filter(Char::isDigit) },
                      label = { Text(strings.rangeStart) },
                      modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                      value = rangeEndText,
                      onValueChange = { rangeEndText = it.filter(Char::isDigit) },
                      label = { Text(strings.rangeEnd) },
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }

              if (splitMode == SplitMode.CHUNKS) {
                item {
                  OutlinedTextField(
                    value = chunkSizeText,
                    onValueChange = { chunkSizeText = it.filter(Char::isDigit) },
                    label = { Text(strings.pagesPerChunk) },
                    modifier = Modifier.fillMaxWidth(),
                  )
                }
              }

              item {
                Button(
                  onClick = {
                    scope.launch {
                      isBusy = true
                      val base = sDoc.displayName.removeSuffix(".pdf")
                      val res = container.pdfEngine.splitPdf(
                        sourceFile = File(sDoc.filePath),
                        mode = splitMode,
                        selectedPagesZeroBased = selectedPages,
                        rangeStartOneBased = rangeStartText.toIntOrNull() ?: 1,
                        rangeEndOneBased = rangeEndText.toIntOrNull() ?: sDoc.pageCount,
                        chunkSize = chunkSizeText.toIntOrNull() ?: 2,
                        outputFileFactory = { suffix ->
                          container.documentRepository.createVaultFile("${base}_$suffix.pdf")
                        },
                      )
                      res.onSuccess { files ->
                        val registeredList = files.map { container.documentRepository.registerPdfFile(it) }
                        splitResults = registeredList
                        isBusy = false
                        onNotify(String.format(strings.splitResultSummary, registeredList.size))
                      }.onFailure { err ->
                        isBusy = false
                        onNotify(err.message ?: strings.errorGeneric)
                      }
                    }
                  },
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("split_execute_btn"),
                ) {
                  Icon(Icons.Filled.CallSplit, contentDescription = null)
                  Spacer(Modifier.width(8.dp))
                  Text(strings.splitNow)
                }
              }

              items(splitResults, key = { it.id }) { createdDoc ->
                Card(
                  modifier = Modifier.fillMaxWidth().clickable { onOpenViewer(createdDoc.id) },
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                  Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(createdDoc.displayName, style = MaterialTheme.typography.titleSmall)
                      Text("${createdDoc.pageCount} ${strings.pagesUnit} • ${FileUtils.formatBytes(createdDoc.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(strings.openPdf, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
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

  if (showMergeVaultSheet) {
    PdfPickerSheet(
      documents = allDocuments,
      onSelectDoc = { doc -> if (mergeQueue.none { it.id == doc.id }) mergeQueue.add(doc) },
      onImportUri = { uri ->
        onImportPdf(uri) { doc -> if (mergeQueue.none { it.id == doc.id }) mergeQueue.add(doc) }
      },
      onDismiss = { showMergeVaultSheet = false },
    )
  }

  if (showSplitPicker) {
    PdfPickerSheet(
      documents = allDocuments,
      onSelectDoc = { doc ->
        splitDoc = doc
        selectedPages = setOf(0)
        rangeStartText = "1"
        rangeEndText = doc.pageCount.coerceAtLeast(1).toString()
        splitResults = emptyList()
      },
      onImportUri = { uri ->
        onImportPdf(uri) { doc ->
          splitDoc = doc
          selectedPages = setOf(0)
          rangeStartText = "1"
          rangeEndText = doc.pageCount.coerceAtLeast(1).toString()
          splitResults = emptyList()
        }
      },
      onDismiss = { showSplitPicker = false },
    )
  }
}
