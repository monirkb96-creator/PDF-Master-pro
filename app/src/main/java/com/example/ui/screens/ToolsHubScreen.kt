package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalStrings
import com.example.ui.components.ToolActionTile

private data class ToolEntry(
  val title: String,
  val icon: ImageVector,
  val color: Color,
  val tag: String,
  val onClick: () -> Unit,
)

@Composable
fun ToolsHubScreen(
  onOpenEditor: () -> Unit,
  onOpenMerge: () -> Unit,
  onOpenSplit: () -> Unit,
  onOpenCompress: () -> Unit,
  onOpenPageOrganizer: () -> Unit,
  onOpenPdfToWord: () -> Unit,
  onOpenWordToPdf: () -> Unit,
  onOpenPdfToJpg: () -> Unit,
  onOpenPdfToPng: () -> Unit,
  onOpenJpgToPdf: () -> Unit,
  onOpenExtractImages: () -> Unit,
  onOpenScanner: () -> Unit,
  onOpenOcr: () -> Unit,
  onOpenSignature: () -> Unit,
  onOpenWatermark: () -> Unit,
  onOpenProtect: () -> Unit,
  onOpenUnlock: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current

  val sections = listOf(
    strings.editAndOrganize to listOf(
      ToolEntry(strings.toolEditPdf, Icons.Filled.EditNote, Color(0xFF155EEF), "tools_edit", onOpenEditor),
      ToolEntry(strings.toolMergePdf, Icons.Filled.CallMerge, Color(0xFF2563EB), "tools_merge", onOpenMerge),
      ToolEntry(strings.toolSplitPdf, Icons.Filled.CallSplit, Color(0xFF0284C7), "tools_split", onOpenSplit),
      ToolEntry(strings.toolCompressPdf, Icons.Filled.Compress, Color(0xFF0EA5E9), "tools_compress", onOpenCompress),
      ToolEntry(strings.toolPageOrganizer, Icons.Filled.ViewAgenda, Color(0xFF4F46E5), "tools_organizer", onOpenPageOrganizer),
    ),
    strings.convertAndScan to listOf(
      ToolEntry(strings.toolScanner, Icons.Filled.DocumentScanner, Color(0xFF0D9488), "tools_scanner", onOpenScanner),
      ToolEntry(strings.toolOcrScanner, Icons.Filled.TextFields, Color(0xFF4F46E5), "tools_ocr", onOpenOcr),
      ToolEntry(strings.toolPdfToWord, Icons.Outlined.Description, Color(0xFF1D4ED8), "tools_pdf_word", onOpenPdfToWord),
      ToolEntry(strings.toolWordToPdf, Icons.Filled.PictureAsPdf, Color(0xFF155EEF), "tools_word_pdf", onOpenWordToPdf),
      ToolEntry(strings.toolPdfToJpg, Icons.Filled.Image, Color(0xFF0369A1), "tools_pdf_jpg", onOpenPdfToJpg),
      ToolEntry(strings.toolPdfToPng, Icons.Filled.Image, Color(0xFF0284C7), "tools_pdf_png", onOpenPdfToPng),
      ToolEntry(strings.toolJpgToPdf, Icons.Filled.PhotoLibrary, Color(0xFF0F766E), "tools_img_pdf", onOpenJpgToPdf),
      ToolEntry(strings.toolExtractImages, Icons.Outlined.FolderCopy, Color(0xFF0284C7), "tools_extract_img", onOpenExtractImages),
    ),
    strings.securityAndSign to listOf(
      ToolEntry(strings.toolSignature, Icons.Filled.Draw, Color(0xFF155EEF), "tools_sign", onOpenSignature),
      ToolEntry(strings.toolWatermark, Icons.Filled.BrandingWatermark, Color(0xFF4338CA), "tools_watermark", onOpenWatermark),
      ToolEntry(strings.toolProtectPdf, Icons.Filled.Lock, Color(0xFF0F766E), "tools_protect", onOpenProtect),
      ToolEntry(strings.toolUnlockPdf, Icons.Filled.LockOpen, Color(0xFFB45309), "tools_unlock", onOpenUnlock),
    ),
  )

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 960.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      item {
        Text(
          text = strings.navTools,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
      }
      sections.forEach { (sectionTitle, tools) ->
        item {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = sectionTitle,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            val rows = tools.chunked(3)
            rows.forEach { rowItems ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                rowItems.forEach { entry ->
                  ToolActionTile(
                    title = entry.title,
                    icon = entry.icon,
                    accentColor = entry.color,
                    onClick = entry.onClick,
                    testTag = entry.tag,
                    modifier = Modifier.weight(1f),
                  )
                }
                repeat(3 - rowItems.size) {
                  Spacer(Modifier.weight(1f))
                }
              }
            }
          }
        }
      }
      item { Spacer(Modifier.height(12.dp)) }
    }
  }
}
