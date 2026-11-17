package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.AppContainer
import com.example.core.i18n.LocalStrings
import com.example.data.local.PdfDocumentEntity
import com.example.domain.model.OrganizerPageItem
import com.example.ui.components.BusyOverlay
import com.example.ui.components.PdfPageBitmapView
import com.example.ui.components.PdfPickerSheet
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOrganizerScreen(
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

  var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  val pages = remember { mutableStateListOf<OrganizerPageItem>() }
  var showMainPicker by remember { mutableStateOf(false) }
  var showImportPagesPicker by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }

  fun loadPagesFor(doc: PdfDocumentEntity) {
    selectedDoc = doc
    pages.clear()
    for (i in 0 until doc.pageCount) {
      pages.add(
        OrganizerPageItem(
          uid = UUID.randomUUID().toString(),
          sourceFilePath = doc.filePath,
          sourcePageIndex = i,
        )
      )
    }
  }

  LaunchedEffect(preselectedDocId) {
    if (preselectedDocId > 0) {
      container.documentRepository.getDocumentById(preselectedDocId)?.let { loadPagesFor(it) }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(strings.toolPageOrganizer) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
          }
        },
        actions = {
          if (pages.isNotEmpty() && selectedDoc != null) {
            Button(
              onClick = {
                val baseDoc = selectedDoc!!
                scope.launch {
                  isBusy = true
                  val out = container.documentRepository.createVaultFile("organized_${baseDoc.displayName}")
                  container.pdfEngine.commitPageOrganization(pages.toList(), out)
                    .onSuccess { file ->
                      val reg = container.documentRepository.registerPdfFile(file)
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
              modifier = Modifier.padding(end = 8.dp).testTag("organizer_save_btn"),
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
      Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Button(
          onClick = { showMainPicker = true },
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("organizer_pick_btn"),
        ) {
          Icon(Icons.Filled.FolderOpen, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(selectedDoc?.displayName ?: strings.selectFile)
        }

        if (pages.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = {
                pages.add(
                  OrganizerPageItem(
                    uid = UUID.randomUUID().toString(),
                    sourceFilePath = "",
                    sourcePageIndex = -1,
                    isBlank = true,
                  )
                )
              },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(strings.insertBlankPage, maxLines = 1)
            }
            OutlinedButton(
              onClick = { showImportPagesPicker = true },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(strings.importPagesFromPdf, maxLines = 1)
            }
            val selectedItems = pages.filter { it.selected }
            if (selectedItems.isNotEmpty()) {
              Button(
                onClick = {
                  val baseDoc = selectedDoc ?: return@Button
                  scope.launch {
                    isBusy = true
                    val out = container.documentRepository.createVaultFile("extracted_${baseDoc.displayName}")
                    container.pdfEngine.commitPageOrganization(selectedItems, out)
                      .onSuccess { file ->
                        val reg = container.documentRepository.registerPdfFile(file)
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
              ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${strings.extractSelected} (${selectedItems.size})")
              }
            }
          }

          Spacer(Modifier.height(10.dp))

          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.weight(1f),
          ) {
            itemsIndexed(pages, key = { _, item -> item.uid }) { idx, item ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                  .border(
                    width = if (item.selected) 2.5.dp else 1.dp,
                    color = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(14.dp),
                  )
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { pages[idx] = item.copy(selected = !item.selected) },
              ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(145.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color.White),
                    contentAlignment = Alignment.Center,
                  ) {
                    if (item.isBlank) {
                      Text(strings.insertBlankPage, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    } else {
                      PdfPageBitmapView(
                        pdfRendererHelper = container.pdfRendererHelper,
                        file = File(item.sourceFilePath),
                        pageIndex = item.sourcePageIndex,
                        targetWidthPx = 300,
                        rotationDegrees = item.rotationDegrees,
                      )
                    }
                    Surface(
                      color = MaterialTheme.colorScheme.primary,
                      shape = RoundedCornerShape(6.dp),
                      modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    ) {
                      Text(
                        "${idx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      )
                    }
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    IconButton(
                      onClick = {
                        if (idx > 0) {
                          val moving = pages.removeAt(idx)
                          pages.add(idx - 1, moving)
                        }
                      },
                      enabled = idx > 0,
                      modifier = Modifier.size(36.dp),
                    ) {
                      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.moveUp, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                      onClick = {
                        pages[idx] = item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360)
                      },
                      modifier = Modifier.size(36.dp),
                    ) {
                      Icon(Icons.Filled.RotateRight, contentDescription = strings.rotate90, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                      onClick = {
                        pages.add(idx + 1, item.copy(uid = UUID.randomUUID().toString(), selected = false))
                      },
                      modifier = Modifier.size(36.dp),
                    ) {
                      Icon(Icons.Filled.ContentCopy, contentDescription = strings.duplicatePage, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                      onClick = {
                        if (pages.size <= 1) {
                          onNotify(strings.cannotDeleteLastPage)
                        } else {
                          pages.removeAt(idx)
                        }
                      },
                      modifier = Modifier.size(36.dp),
                    ) {
                      Icon(Icons.Filled.Delete, contentDescription = strings.delete, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                      onClick = {
                        if (idx < pages.lastIndex) {
                          val moving = pages.removeAt(idx)
                          pages.add(idx + 1, moving)
                        }
                      },
                      enabled = idx < pages.lastIndex,
                      modifier = Modifier.size(36.dp),
                    ) {
                      Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = strings.moveDown, modifier = Modifier.size(16.dp))
                    }
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

  if (showMainPicker) {
    PdfPickerSheet(
      documents = allDocuments,
      onSelectDoc = { loadPagesFor(it) },
      onImportUri = { uri -> onImportPdf(uri) { loadPagesFor(it) } },
      onDismiss = { showMainPicker = false },
    )
  }

  if (showImportPagesPicker) {
    PdfPickerSheet(
      documents = allDocuments,
      onSelectDoc = { other ->
        for (i in 0 until other.pageCount) {
          pages.add(
            OrganizerPageItem(
              uid = UUID.randomUUID().toString(),
              sourceFilePath = other.filePath,
              sourcePageIndex = i,
            )
          )
        }
      },
      onImportUri = { uri ->
        onImportPdf(uri) { other ->
          for (i in 0 until other.pageCount) {
            pages.add(
              OrganizerPageItem(
                uid = UUID.randomUUID().toString(),
                sourceFilePath = other.filePath,
                sourcePageIndex = i,
              )
            )
          }
        }
      },
      onDismiss = { showImportPagesPicker = false },
    )
  }
}
