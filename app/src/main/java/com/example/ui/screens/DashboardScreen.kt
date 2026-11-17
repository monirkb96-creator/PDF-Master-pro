package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.LocalStrings
import com.example.core.i18n.ThemeMode
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentEntity
import com.example.data.preferences.UserSettings
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DocumentListCard
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.ToolActionTile
import java.io.File

private data class DashTileItem(
  val title: String,
  val icon: ImageVector,
  val color: Color,
  val tag: String,
  val onClick: () -> Unit,
)

@Composable
fun DashboardScreen(
  settings: UserSettings,
  recentDocs: List<PdfDocumentEntity>,
  favoriteCount: Int,
  onImportPdf: (Uri, (PdfDocumentEntity) -> Unit) -> Unit,
  onOpenViewer: (Long) -> Unit,
  onOpenEditor: () -> Unit,
  onOpenEditorForDoc: (Long) -> Unit,
  onOpenMerge: () -> Unit,
  onOpenSplit: () -> Unit,
  onOpenCompress: () -> Unit,
  onOpenPdfToWord: () -> Unit,
  onOpenPdfToJpg: () -> Unit,
  onOpenJpgToPdf: () -> Unit,
  onOpenScanner: () -> Unit,
  onOpenSignature: () -> Unit,
  onOpenWatermark: () -> Unit,
  onOpenProtect: () -> Unit,
  onOpenUnlock: () -> Unit,
  onOpenOcr: () -> Unit,
  onOpenExtractImages: () -> Unit,
  onOpenPageOrganizer: () -> Unit,
  onOpenPageOrganizerForDoc: (Long) -> Unit,
  onOpenRecentFiles: () -> Unit,
  onOpenFavorites: () -> Unit,
  onOpenSettings: () -> Unit,
  onToggleThemeQuick: (ThemeMode) -> Unit,
  onToggleLanguageQuick: (AppLanguage) -> Unit,
  onToggleFavorite: (PdfDocumentEntity) -> Unit,
  onRenameDoc: (PdfDocumentEntity, String) -> Unit,
  onDeleteDoc: (PdfDocumentEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val context = LocalContext.current
  var renameTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var deleteTarget by remember { mutableStateOf<PdfDocumentEntity?>(null) }

  val openPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      onImportPdf(uri) { imported -> onOpenViewer(imported.id) }
    }
  }

  val quickActions = listOf(
    DashTileItem(strings.openPdf, Icons.Filled.FolderOpen, Color(0xFF155EEF), "dash_open_pdf") {
      openPdfLauncher.launch(arrayOf("application/pdf"))
    },
    DashTileItem(strings.toolEditPdf, Icons.Filled.EditNote, Color(0xFF0284C7), "dash_edit_pdf", onOpenEditor),
    DashTileItem(strings.toolScanner, Icons.Filled.DocumentScanner, Color(0xFF0D9488), "dash_scanner", onOpenScanner),
    DashTileItem(strings.toolOcrScanner, Icons.Filled.TextFields, Color(0xFF4F46E5), "dash_ocr", onOpenOcr),
  )

  val organizeActions = listOf(
    DashTileItem(strings.toolMergePdf, Icons.Filled.CallMerge, Color(0xFF155EEF), "dash_merge", onOpenMerge),
    DashTileItem(strings.toolSplitPdf, Icons.Filled.CallSplit, Color(0xFF2563EB), "dash_split", onOpenSplit),
    DashTileItem(strings.toolCompressPdf, Icons.Filled.Compress, Color(0xFF0EA5E9), "dash_compress", onOpenCompress),
    DashTileItem(strings.toolPageOrganizer, Icons.Filled.ViewAgenda, Color(0xFF6366F1), "dash_organizer", onOpenPageOrganizer),
  )

  val convertActions = listOf(
    DashTileItem(strings.toolPdfToWord, Icons.Outlined.Description, Color(0xFF1D4ED8), "dash_pdf_to_word", onOpenPdfToWord),
    DashTileItem(strings.toolPdfToJpg, Icons.Filled.Image, Color(0xFF0369A1), "dash_pdf_to_jpg", onOpenPdfToJpg),
    DashTileItem(strings.toolJpgToPdf, Icons.Filled.PhotoLibrary, Color(0xFF0F766E), "dash_jpg_to_pdf", onOpenJpgToPdf),
    DashTileItem(strings.toolExtractImages, Icons.Outlined.FolderCopy, Color(0xFF0284C7), "dash_extract_images", onOpenExtractImages),
  )

  val securityActions = listOf(
    DashTileItem(strings.toolSignature, Icons.Filled.Draw, Color(0xFF155EEF), "dash_signature", onOpenSignature),
    DashTileItem(strings.toolWatermark, Icons.Filled.BrandingWatermark, Color(0xFF4338CA), "dash_watermark", onOpenWatermark),
    DashTileItem(strings.toolProtectPdf, Icons.Filled.Lock, Color(0xFF0F766E), "dash_protect", onOpenProtect),
    DashTileItem(strings.toolUnlockPdf, Icons.Filled.LockOpen, Color(0xFFB45309), "dash_unlock", onOpenUnlock),
  )

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 960.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // Top Brand Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "PDF MASTER PRO",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
              )
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp),
                  )
                  Text(
                    text = strings.offlineBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }
            }
            Text(
              text = strings.tagline,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(
              onClick = {
                val next = if (settings.language == AppLanguage.ENGLISH) AppLanguage.BANGLA else AppLanguage.ENGLISH
                onToggleLanguageQuick(next)
              },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("dash_lang_toggle"),
            ) {
              Icon(Icons.Filled.Translate, contentDescription = strings.languageSetting, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text(if (settings.language == AppLanguage.ENGLISH) "বাংলা" else "EN", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(
              onClick = {
                val nextTheme = if (settings.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
                onToggleThemeQuick(nextTheme)
              },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("dash_theme_toggle"),
            ) {
              Icon(Icons.Filled.Brightness4, contentDescription = strings.themeSetting)
            }
          }
        }
      }

      // Hero Banner Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.extraLarge,
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(172.dp),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_hero_banner),
              contentDescription = strings.heroTitle,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.horizontalGradient(
                    colors = listOf(
                      Color(0xFF0A2463).copy(alpha = 0.90f),
                      Color(0xFF155EEF).copy(alpha = 0.72f),
                    )
                  )
                ),
            )
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Column {
                Text(
                  text = strings.heroTitle,
                  style = MaterialTheme.typography.headlineSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  text = strings.heroSubtitle,
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White.copy(alpha = 0.9f),
                  maxLines = 2,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                  onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF155EEF),
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("hero_open_pdf_btn"),
                ) {
                  Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(strings.openPdf, fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                  onClick = onOpenScanner,
                  colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.22f),
                    contentColor = Color.White,
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("hero_scan_btn"),
                ) {
                  Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(strings.toolScanner)
                }
              }
            }
          }
        }
      }

      // Quick Navigation Shortcuts: Recent Files, Favorites, Settings
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          item {
            QuickStatChip(
              icon = Icons.Outlined.FolderCopy,
              label = "${strings.recentFiles} (${recentDocs.size})",
              onClick = onOpenRecentFiles,
              testTag = "dash_recent_chip",
            )
          }
          item {
            QuickStatChip(
              icon = Icons.Filled.Star,
              label = "${strings.favoritesTitle} ($favoriteCount)",
              onClick = onOpenFavorites,
              testTag = "dash_favorites_chip",
            )
          }
          item {
            QuickStatChip(
              icon = Icons.Filled.Settings,
              label = strings.navSettings,
              onClick = onOpenSettings,
              testTag = "dash_settings_chip",
            )
          }
        }
      }

      // Tool Categories
      item { ToolGridSection(title = strings.quickActions, items = quickActions) }
      item { ToolGridSection(title = strings.editAndOrganize, items = organizeActions) }
      item { ToolGridSection(title = strings.convertAndScan, items = convertActions) }
      item { ToolGridSection(title = strings.securityAndSign, items = securityActions) }

      // Recent Documents Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = strings.recentFiles,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          TextButton(onClick = onOpenRecentFiles) {
            Text(strings.seeAll)
          }
        }
      }

      if (recentDocs.isEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_empty_docs),
                contentDescription = strings.emptyDocsTitle,
                modifier = Modifier
                  .size(130.dp)
                  .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop,
              )
              Spacer(Modifier.height(12.dp))
              Text(
                text = strings.emptyDocsTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = strings.emptyDocsSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(Modifier.height(14.dp))
              Button(
                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("empty_import_pdf_btn"),
              ) {
                Text(strings.importFromDevice)
              }
            }
          }
        }
      } else {
        items(recentDocs, key = { it.id }) { doc ->
          DocumentListCard(
            doc = doc,
            onClick = { onOpenViewer(doc.id) },
            onToggleFavorite = { onToggleFavorite(doc) },
            onEdit = { onOpenEditorForDoc(doc.id) },
            onOrganize = { onOpenPageOrganizerForDoc(doc.id) },
            onRename = { renameTarget = doc },
            onShare = {
              FileUtils.shareFile(context, File(doc.filePath), "application/pdf", strings.share)
            },
            onDelete = { deleteTarget = doc },
          )
        }
      }

      item { Spacer(Modifier.height(12.dp)) }
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

@Composable
private fun QuickStatChip(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Surface(
    modifier = Modifier
      .clip(RoundedCornerShape(999.dp))
      .clickable(onClick = onClick)
      .minimumInteractiveComponentSize()
      .testTag(testTag),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
    shape = RoundedCornerShape(999.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
      Text(label, style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
private fun ToolGridSection(
  title: String,
  items: List<DashTileItem>,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items.forEach { item ->
        ToolActionTile(
          title = item.title,
          icon = item.icon,
          accentColor = item.color,
          onClick = item.onClick,
          testTag = item.tag,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}
