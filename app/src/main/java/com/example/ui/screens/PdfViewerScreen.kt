package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.WidthFull
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.AppContainer
import com.example.core.i18n.LocalStrings
import com.example.core.util.FileUtils
import com.example.data.local.PdfDocumentEntity
import com.example.domain.model.PdfDocInfo
import com.example.ui.components.PdfPageBitmapView
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
  docId: Long,
  container: AppContainer,
  onBack: () -> Unit,
  onOpenEditor: (Long) -> Unit,
  onOpenOrganizer: (Long) -> Unit,
  onNotify: (String) -> Unit,
) {
  val strings = LocalStrings.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var doc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var activeFile by remember { mutableStateOf<File?>(null) }
  var tempUnlockedFile by remember { mutableStateOf<File?>(null) }
  var pageCount by remember { mutableIntStateOf(0) }
  var currentPage by remember { mutableIntStateOf(0) }
  var rotationDeg by remember { mutableIntStateOf(0) }
  var zoom by remember { mutableFloatStateOf(1f) }
  var pan by remember { mutableStateOf(Offset.Zero) }
  var fitWidthMode by remember { mutableStateOf(true) }
  var fullscreen by remember { mutableStateOf(false) }
  var showThumbnails by remember { mutableStateOf(false) }
  var showSearch by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var matchedPages by remember { mutableStateOf<List<Int>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }
  var docInfo by remember { mutableStateOf<PdfDocInfo?>(null) }
  var showInfoDialog by remember { mutableStateOf(false) }
  var showOverflow by remember { mutableStateOf(false) }

  var needsPassword by remember { mutableStateOf(false) }
  var passwordInput by remember { mutableStateOf("") }
  var passwordError by remember { mutableStateOf<String?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      tempUnlockedFile?.delete()
    }
  }

  LaunchedEffect(docId) {
    val loaded = container.documentRepository.getDocumentById(docId)
    doc = loaded
    if (loaded != null) {
      container.documentRepository.touchOpened(docId)
      val file = File(loaded.filePath)
      val inspection = runCatching { container.documentRepository.inspectPdf(file) }.getOrNull()
      if (inspection?.isEncrypted == true || loaded.isPasswordProtected) {
        needsPassword = true
      } else {
        activeFile = file
        pageCount = inspection?.pageCount ?: loaded.pageCount
      }
    }
  }

  val saveAsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
    val fileToExport = doc?.let { File(it.filePath) } ?: activeFile
    if (uri != null && fileToExport != null) {
      val ok = FileUtils.copyFileToUri(context, fileToExport, uri)
      onNotify(if (ok) strings.savedSuccess else strings.errorGeneric)
    }
  }

  Scaffold(
    topBar = {
      if (!fullscreen) {
        TopAppBar(
          title = {
            Text(
              text = doc?.displayName ?: strings.openPdf,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back_btn")) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
            }
          },
          actions = {
            IconButton(
              onClick = { showSearch = !showSearch },
              modifier = Modifier.testTag("viewer_search_btn"),
            ) {
              Icon(Icons.Filled.Search, contentDescription = strings.searchInPdf)
            }
            IconButton(
              onClick = { showThumbnails = !showThumbnails },
              modifier = Modifier.testTag("viewer_thumbs_btn"),
            ) {
              Icon(Icons.Filled.ViewSidebar, contentDescription = strings.thumbnails)
            }
            if (!needsPassword && doc != null) {
              IconButton(
                onClick = { onOpenEditor(doc!!.id) },
                modifier = Modifier.testTag("viewer_edit_btn"),
              ) {
                Icon(Icons.Filled.Edit, contentDescription = strings.toolEditPdf)
              }
            }
            Box {
              IconButton(onClick = { showOverflow = true }, modifier = Modifier.testTag("viewer_more_btn")) {
                Icon(Icons.Filled.MoreVert, contentDescription = strings.docInfo)
              }
              DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                DropdownMenuItem(
                  text = { Text(strings.docInfo) },
                  leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                  onClick = {
                    showOverflow = false
                    val f = activeFile ?: doc?.let { File(it.filePath) }
                    if (f != null) {
                      scope.launch {
                        docInfo = container.pdfEngine.getDocumentInfo(f).getOrNull()
                        showInfoDialog = true
                      }
                    }
                  },
                )
                DropdownMenuItem(
                  text = { Text(strings.print) },
                  leadingIcon = { Icon(Icons.Filled.Print, contentDescription = null) },
                  onClick = {
                    showOverflow = false
                    activeFile?.let { FileUtils.printPdf(context, it, doc?.displayName ?: "PDF") }
                  },
                )
                DropdownMenuItem(
                  text = { Text(strings.saveAs) },
                  leadingIcon = { Icon(Icons.Filled.SaveAlt, contentDescription = null) },
                  onClick = {
                    showOverflow = false
                    saveAsLauncher.launch(doc?.displayName ?: "document.pdf")
                  },
                )
                DropdownMenuItem(
                  text = { Text(strings.share) },
                  leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                  onClick = {
                    showOverflow = false
                    doc?.let { FileUtils.shareFile(context, File(it.filePath), "application/pdf", strings.share) }
                  },
                )
                if (!needsPassword && doc != null) {
                  DropdownMenuItem(
                    text = { Text(strings.toolPageOrganizer) },
                    onClick = {
                      showOverflow = false
                      onOpenOrganizer(doc!!.id)
                    },
                  )
                }
              }
            }
          },
        )
      }
    },
    bottomBar = {
      if (!fullscreen && activeFile != null && pageCount > 0) {
        Surface(tonalElevation = 3.dp) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { if (currentPage > 0) currentPage-- },
                enabled = currentPage > 0,
                modifier = Modifier.testTag("viewer_prev_page"),
              ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = strings.back)
              }
              Text(
                text = "${currentPage + 1} / $pageCount",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("viewer_page_counter"),
              )
              IconButton(
                onClick = { if (currentPage < pageCount - 1) currentPage++ },
                enabled = currentPage < pageCount - 1,
                modifier = Modifier.testTag("viewer_next_page"),
              ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(1f); if (zoom == 1f) pan = Offset.Zero }) {
                Icon(Icons.Filled.ZoomOut, contentDescription = strings.zoomOut)
              }
              IconButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(4f) }) {
                Icon(Icons.Filled.ZoomIn, contentDescription = strings.zoomIn)
              }
              IconButton(onClick = {
                fitWidthMode = !fitWidthMode
                zoom = 1f
                pan = Offset.Zero
              }) {
                Icon(
                  imageVector = if (fitWidthMode) Icons.Filled.FitScreen else Icons.Filled.WidthFull,
                  contentDescription = if (fitWidthMode) strings.fitPage else strings.fitWidth,
                )
              }
              IconButton(onClick = { rotationDeg = (rotationDeg + 90) % 360 }) {
                Icon(Icons.Filled.RotateRight, contentDescription = strings.rotateView)
              }
              IconButton(onClick = { fullscreen = true }, modifier = Modifier.testTag("viewer_fullscreen_btn")) {
                Icon(Icons.Filled.Fullscreen, contentDescription = strings.fullscreen)
              }
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      if (needsPassword && doc != null) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp),
          )
          Spacer(Modifier.height(12.dp))
          Text(strings.passwordRequired, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(14.dp))
          OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it; passwordError = null },
            label = { Text(strings.enterPassword) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = passwordError != null,
            supportingText = passwordError?.let { err -> { Text(err) } },
            modifier = Modifier.fillMaxWidth(0.85f),
          )
          Spacer(Modifier.height(12.dp))
          Button(
            onClick = {
              scope.launch {
                val res = container.pdfRendererHelper.createTempUnlockedCopy(File(doc!!.filePath), passwordInput)
                res.onSuccess { unlocked ->
                  tempUnlockedFile = unlocked
                  activeFile = unlocked
                  pageCount = container.pdfRendererHelper.getPageCount(unlocked)
                  needsPassword = false
                }.onFailure {
                  passwordError = strings.wrongPassword
                }
              }
            },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text(strings.unlockAndView)
          }
        }
      } else {
        Column(modifier = Modifier.fillMaxSize()) {
          // Search Bar
          AnimatedVisibility(visible = showSearch) {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchInPdf) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                  )
                  Button(
                    onClick = {
                      val f = activeFile ?: return@Button
                      scope.launch {
                        isSearching = true
                        matchedPages = container.pdfEngine.searchTextPages(f, searchQuery)
                        isSearching = false
                        if (matchedPages.isNotEmpty()) currentPage = matchedPages.first()
                      }
                    },
                    enabled = !isSearching && searchQuery.isNotBlank(),
                  ) {
                    if (isSearching) {
                      CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                      Text(strings.searchInPdf.substringBefore(" "))
                    }
                  }
                  IconButton(onClick = { showSearch = false }) {
                    Icon(Icons.Filled.Close, contentDescription = strings.close)
                  }
                }
                if (matchedPages.isNotEmpty()) {
                  Spacer(Modifier.height(6.dp))
                  LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                      Text(
                        "${matchedPages.size} ${strings.matchesFound}:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp, end = 4.dp),
                      )
                    }
                    items(matchedPages) { pageIdx ->
                      FilterChip(
                        selected = currentPage == pageIdx,
                        onClick = { currentPage = pageIdx },
                        label = { Text("${strings.pageLabel} ${pageIdx + 1}") },
                      )
                    }
                  }
                }
              }
            }
          }

          Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Thumbnail Sidebar
            AnimatedVisibility(visible = showThumbnails && activeFile != null && pageCount > 0) {
              Surface(
                tonalElevation = 2.dp,
                modifier = Modifier
                  .width(116.dp)
                  .fillMaxHeight(),
              ) {
                LazyColumn(
                  contentPadding = PaddingValues(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  items((0 until pageCount).toList()) { idx ->
                    val selected = idx == currentPage
                    Column(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                          width = if (selected) 2.dp else 1.dp,
                          color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                          shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { currentPage = idx }
                        .padding(4.dp),
                      horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                      Box(modifier = Modifier.height(110.dp).fillMaxWidth()) {
                        PdfPageBitmapView(
                          pdfRendererHelper = container.pdfRendererHelper,
                          file = activeFile!!,
                          pageIndex = idx,
                          targetWidthPx = 220,
                        )
                      }
                      Text("${idx + 1}", style = MaterialTheme.typography.labelSmall)
                    }
                  }
                }
              }
            }

            // Main Page Canvas with Pinch Zoom & Pan
            BoxWithConstraints(
              modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .pointerInput(currentPage) {
                  detectTransformGestures { _, panChange, zoomChange, _ ->
                    val newZoom = (zoom * zoomChange).coerceIn(1f, 4.5f)
                    zoom = newZoom
                    pan = if (newZoom > 1f) pan + panChange else Offset.Zero
                  }
                },
              contentAlignment = Alignment.Center,
            ) {
              val f = activeFile
              if (f != null && pageCount > 0) {
                val bmp by produceState<Bitmap?>(
                  initialValue = null,
                  f.absolutePath,
                  currentPage,
                  rotationDeg,
                ) {
                  value = container.pdfRendererHelper.renderPage(
                    file = f,
                    pageIndex = currentPage,
                    targetWidthPx = 1400,
                    extraRotationDegrees = rotationDeg,
                  )
                }
                val pageBmp = bmp
                if (pageBmp != null) {
                  Image(
                    bitmap = pageBmp.asImageBitmap(),
                    contentDescription = "${strings.pageLabel} ${currentPage + 1}",
                    contentScale = if (fitWidthMode) ContentScale.FillWidth else ContentScale.Fit,
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(if (fullscreen) 0.dp else 10.dp)
                      .graphicsLayer(
                        scaleX = zoom,
                        scaleY = zoom,
                        translationX = pan.x,
                        translationY = pan.y,
                      ),
                  )
                } else {
                  CircularProgressIndicator()
                }
              } else {
                CircularProgressIndicator()
              }

              if (fullscreen) {
                IconButton(
                  onClick = { fullscreen = false },
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp)),
                ) {
                  Icon(Icons.Filled.FullscreenExit, contentDescription = strings.fullscreen, tint = Color.White)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showInfoDialog && docInfo != null) {
    val info = docInfo!!
    AlertDialog(
      onDismissRequest = { showInfoDialog = false },
      title = { Text(strings.docInfo) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("${strings.fileNameLabel}: ${info.fileName}")
          Text("${strings.fileSizeLabel}: ${FileUtils.formatBytes(info.sizeBytes)}")
          Text("${strings.pageCountLabel}: ${info.pageCount}")
          Text("${strings.lastModifiedLabel}: ${FileUtils.formatDate(info.lastModified)}")
          Text("${strings.encryptedLabel}: ${if (info.isEncrypted) strings.yesLabel else strings.noLabel}")
          info.title?.let { Text("Title: $it") }
          info.author?.let { Text("Author: $it") }
        }
      },
      confirmButton = {
        TextButton(onClick = { showInfoDialog = false }) { Text(strings.close) }
      },
    )
  }
}
