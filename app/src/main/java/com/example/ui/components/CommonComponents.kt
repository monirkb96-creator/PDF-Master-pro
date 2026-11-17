package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.i18n.LocalStrings
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentEntity
import com.example.domain.engine.PdfRendererHelper
import java.io.File

@Composable
fun DocumentListCard(
  doc: PdfDocumentEntity,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onEdit: (() -> Unit)? = null,
  onOrganize: (() -> Unit)? = null,
  onRename: (() -> Unit)? = null,
  onShare: (() -> Unit)? = null,
  onExport: (() -> Unit)? = null,
  onDelete: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClick = onClick)
      .testTag("doc_card_${doc.id}"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Document thumbnail
      Box(
        modifier = Modifier
          .size(width = 54.dp, height = 68.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
      ) {
        if (doc.isPasswordProtected) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = strings.encryptedLabel,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
          )
        } else if (!doc.thumbnailPath.isNullOrBlank() && File(doc.thumbnailPath).exists()) {
          AsyncImage(
            model = File(doc.thumbnailPath),
            contentDescription = doc.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.PictureAsPdf,
            contentDescription = doc.displayName,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
          )
        }
      }

      Spacer(Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = doc.displayName,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = FileUtils.formatBytes(doc.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (doc.pageCount > 0) {
            Text(
              text = "• ${doc.pageCount} ${strings.pagesUnit}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (doc.isPasswordProtected) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = strings.encryptedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }
        Spacer(Modifier.height(2.dp))
        Text(
          text = FileUtils.formatDate(doc.lastModified),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onToggleFavorite,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("fav_btn_${doc.id}"),
      ) {
        Icon(
          imageVector = if (doc.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
          contentDescription = if (doc.isFavorite) strings.unfavorite else strings.favorite,
          tint = if (doc.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (onEdit != null || onRename != null || onShare != null || onDelete != null) {
        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("doc_menu_${doc.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.MoreVert,
              contentDescription = strings.docInfo,
            )
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
          ) {
            onEdit?.let {
              DropdownMenuItem(
                text = { Text(strings.toolEditPdf) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { menuExpanded = false; it() },
              )
            }
            onOrganize?.let {
              DropdownMenuItem(
                text = { Text(strings.toolPageOrganizer) },
                leadingIcon = { Icon(Icons.Outlined.ViewAgenda, contentDescription = null) },
                onClick = { menuExpanded = false; it() },
              )
            }
            onRename?.let {
              DropdownMenuItem(
                text = { Text(strings.rename) },
                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                onClick = { menuExpanded = false; it() },
              )
            }
            onShare?.let {
              DropdownMenuItem(
                text = { Text(strings.share) },
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = { menuExpanded = false; it() },
              )
            }
            onExport?.let {
              DropdownMenuItem(
                text = { Text(strings.saveAs) },
                leadingIcon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                onClick = { menuExpanded = false; it() },
              )
            }
            onDelete?.let {
              DropdownMenuItem(
                text = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                  Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { menuExpanded = false; it() },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun ToolActionTile(
  title: String,
  icon: ImageVector,
  accentColor: Color,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClick = onClick)
      .minimumInteractiveComponentSize()
      .testTag(testTag),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 14.dp, horizontal = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = accentColor,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(Modifier.height(8.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPickerSheet(
  documents: List<PdfDocumentEntity>,
  onSelectDoc: (PdfDocumentEntity) -> Unit,
  onImportUri: (Uri) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      onImportUri(uri)
      onDismiss()
    }
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      Text(
        text = strings.selectFile,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(12.dp))
      Button(
        onClick = { launcher.launch(arrayOf("application/pdf")) },
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag("picker_import_device_btn"),
      ) {
        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(strings.importFromDevice)
      }
      Spacer(Modifier.height(14.dp))
      if (documents.isNotEmpty()) {
        Text(
          text = strings.selectFromVault,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.height(280.dp),
        ) {
          items(documents, key = { it.id }) { doc ->
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable {
                  onSelectDoc(doc)
                  onDismiss()
                },
              tonalElevation = 1.dp,
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = if (doc.isPasswordProtected) Icons.Filled.Lock else Icons.Filled.Description,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(doc.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    "${FileUtils.formatBytes(doc.sizeBytes)} • ${doc.pageCount} ${strings.pagesUnit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }
      Spacer(Modifier.height(18.dp))
    }
  }
}

@Composable
fun PdfPageBitmapView(
  pdfRendererHelper: PdfRendererHelper,
  file: File,
  pageIndex: Int,
  targetWidthPx: Int = 900,
  rotationDegrees: Int = 0,
  refreshKey: Any = 0,
  modifier: Modifier = Modifier,
) {
  val bitmap by produceState<Bitmap?>(initialValue = null, file.absolutePath, pageIndex, targetWidthPx, rotationDegrees, refreshKey) {
    value = pdfRendererHelper.renderPage(file, pageIndex, targetWidthPx, rotationDegrees)
  }
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    val bmp = bitmap
    if (bmp != null) {
      Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = "${LocalStrings.current.pageLabel} ${pageIndex + 1}",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
  }
}

@Composable
fun ConfirmDeleteDialog(
  docName: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.deleteConfirmTitle) },
    text = { Text(String.format(strings.deleteConfirmMessage, docName)) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        modifier = Modifier.testTag("confirm_delete_btn"),
      ) {
        Text(strings.delete)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text(strings.cancel)
      }
    },
  )
}

@Composable
fun RenameDocumentDialog(
  currentName: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  var text by remember(currentName) { mutableStateOf(currentName.removeSuffix(".pdf")) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.renameTitle) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(strings.newNameHint) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("rename_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { if (text.isNotBlank()) onConfirm(text) },
        enabled = text.isNotBlank(),
        modifier = Modifier.testTag("confirm_rename_btn"),
      ) {
        Text(strings.save)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancel) }
    },
  )
}

@Composable
fun BusyOverlay(visible: Boolean, message: String? = null) {
  if (!visible) return
  val strings = LocalStrings.current
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.42f)),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(message ?: strings.processing, style = MaterialTheme.typography.titleSmall)
      }
    }
  }
}
