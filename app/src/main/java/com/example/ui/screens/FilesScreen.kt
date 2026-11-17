package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.i18n.LocalStrings
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentEntity
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DocumentListCard
import com.example.ui.components.RenameDocumentDialog
import java.io.File

enum class FilesFilter {
  ALL,
  FAVORITES,
  PROTECTED,
}

@Composable
fun FilesScreen(
  documents: List<PdfDocumentEntity>,
  initialFilter: FilesFilter = FilesFilter.ALL,
  onImportPdf: (Uri) -> Unit,
  onOpenViewer: (Long) -> Unit,
  onOpenEditor: (Long) -> Unit,
  onOpenOrganizer: (Long) -> Unit,
  onToggleFavorite: (PdfDocumentEntity) -> Unit,
  onRenameDoc: (PdfDocumentEntity, String) -> Unit,
  onDeleteDoc: (PdfDocumentEntity) -> Unit,
  onNotify: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val context = LocalContext.current
  var query by remember { mutableStateOf("") }
  var filter by remember(initialFilter) { mutableStateOf(initialFilter) }
  var renameTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var deleteTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var exportTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }

  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onImportPdf(uri)
  }

  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
    val target = exportTarget
    if (uri != null && target != null) {
      val ok = FileUtils.copyFileToUri(context, File(target.filePath), uri)
      onNotify(if (ok) strings.savedSuccess else strings.errorGeneric)
    }
    exportTarget = null
  }

  val filteredDocs = remember(documents, query, filter) {
    documents.filter { doc ->
      val matchesQuery = query.isBlank() || doc.displayName.contains(query.trim(), ignoreCase = true)
      val matchesFilter = when (filter) {
        FilesFilter.ALL -> true
        FilesFilter.FAVORITES -> doc.isFavorite
        FilesFilter.PROTECTED -> doc.isPasswordProtected
      }
      matchesQuery && matchesFilter
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    floatingActionButton = {
      FloatingActionButton(
        onClick = { importLauncher.launch(arrayOf("application/pdf")) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("files_import_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = strings.importFromDevice)
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 900.dp)
          .padding(horizontal = 16.dp, vertical = 12.dp),
      ) {
        Text(
          text = strings.navFiles,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text(strings.searchHint) },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          trailingIcon = {
            if (query.isNotEmpty()) {
              IconButton(onClick = { query = "" }) {
                Icon(Icons.Filled.Clear, contentDescription = strings.clear)
              }
            }
          },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("files_search_input"),
        )

        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = filter == FilesFilter.ALL,
              onClick = { filter = FilesFilter.ALL },
              label = { Text(strings.filterAll) },
              modifier = Modifier.testTag("filter_all_chip"),
            )
          }
          item {
            FilterChip(
              selected = filter == FilesFilter.FAVORITES,
              onClick = { filter = FilesFilter.FAVORITES },
              label = { Text(strings.filterFavorites) },
              modifier = Modifier.testTag("filter_fav_chip"),
            )
          }
          item {
            FilterChip(
              selected = filter == FilesFilter.PROTECTED,
              onClick = { filter = FilesFilter.PROTECTED },
              label = { Text(strings.filterProtected) },
              modifier = Modifier.testTag("filter_protected_chip"),
            )
          }
        }

        Spacer(Modifier.height(10.dp))

        if (filteredDocs.isEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_empty_docs),
              contentDescription = strings.emptyDocsTitle,
              modifier = Modifier
                .size(140.dp)
                .clip(MaterialTheme.shapes.large),
              contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(14.dp))
            Text(
              text = if (query.isNotBlank()) strings.noMatches else strings.emptyDocsTitle,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
              text = strings.emptyDocsSubtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(
              onClick = { importLauncher.launch(arrayOf("application/pdf")) },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Text(strings.importFromDevice)
            }
          }
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 84.dp),
          ) {
            items(filteredDocs, key = { it.id }) { doc ->
              DocumentListCard(
                doc = doc,
                onClick = { onOpenViewer(doc.id) },
                onToggleFavorite = { onToggleFavorite(doc) },
                onEdit = { onOpenEditor(doc.id) },
                onOrganize = { onOpenOrganizer(doc.id) },
                onRename = { renameTarget = doc },
                onShare = {
                  FileUtils.shareFile(context, File(doc.filePath), "application/pdf", strings.share)
                },
                onExport = {
                  exportTarget = doc
                  exportLauncher.launch(doc.displayName)
                },
                onDelete = { deleteTarget = doc },
              )
            }
          }
        }
      }
    }
  }

  renameTarget?.let { target ->
    RenameDocumentDialog(
      currentName = target.displayName,
      onConfirm = { newName ->
        onRenameDoc(target, newName)
        renameTarget = null
      },
      onDismiss = { renameTarget = null },
    )
  }

  deleteTarget?.let { target ->
    ConfirmDeleteDialog(
      docName = target.displayName,
      onConfirm = {
        onDeleteDoc(target)
        deleteTarget = null
      },
      onDismiss = { deleteTarget = null },
    )
  }
}
