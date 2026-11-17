package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.i18n.LocalStrings
import com.example.ui.components.BusyOverlay
import com.example.ui.components.PdfPickerSheet
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.CompressScreen
import com.example.ui.screens.ConverterScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FilesFilter
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.MergeSplitScreen
import com.example.ui.screens.OcrScreen
import com.example.ui.screens.PageOrganizerScreen
import com.example.ui.screens.PdfEditorScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignatureWatermarkScreen
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.theme.PdfMasterTheme
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  private val appContainer: AppContainer
    get() = (application as PdfMasterApplication).container

  private val mainViewModel: MainViewModel by viewModels {
    MainViewModel.Factory(appContainer)
  }

  private val incomingPdfUri = mutableStateOf<Uri?>(null)

  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    extractIncomingPdf(intent)

    setContent {
      val settings by mainViewModel.settings.collectAsStateWithLifecycle()
      val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
      val pendingUri by incomingPdfUri

      PdfMasterTheme(
        themeMode = settings.themeMode,
        language = settings.language,
      ) {
        PdfMasterAppRoot(
          container = appContainer,
          viewModel = mainViewModel,
          widthSizeClass = widthSizeClass,
          incomingUri = pendingUri,
          onConsumedIncomingUri = { incomingPdfUri.value = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    extractIncomingPdf(intent)
  }

  private fun extractIncomingPdf(intent: Intent?) {
    if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
      incomingPdfUri.value = intent.data
    }
  }
}

@Composable
private fun PdfMasterAppRoot(
  container: AppContainer,
  viewModel: MainViewModel,
  widthSizeClass: WindowWidthSizeClass,
  incomingUri: Uri?,
  onConsumedIncomingUri: () -> Unit,
) {
  val strings = LocalStrings.current
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()
  val recentDocs by viewModel.recentDocuments.collectAsStateWithLifecycle()
  val favoriteDocs by viewModel.favoriteDocuments.collectAsStateWithLifecycle()
  val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
  val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

  var showEditorPicker by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    viewModel.messages.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  LaunchedEffect(incomingUri) {
    if (incomingUri != null) {
      viewModel.importPdf(incomingUri) { doc ->
        navController.navigate(AppRoute.Viewer(doc.id))
      }
      onConsumedIncomingUri()
    }
  }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = navBackStackEntry?.destination
  val isTopLevel = currentDest == null ||
    currentDest.hasRoute<AppRoute.Home>() ||
    currentDest.hasRoute<AppRoute.Files>() ||
    currentDest.hasRoute<AppRoute.Tools>() ||
    currentDest.hasRoute<AppRoute.Settings>()

  val useRail = widthSizeClass != WindowWidthSizeClass.Compact

  val topTabs = listOf(
    Triple(AppRoute.Home, strings.navHome, Icons.Filled.Home to Icons.Outlined.Home),
    Triple(AppRoute.Files(), strings.navFiles, Icons.Filled.Folder to Icons.Outlined.Folder),
    Triple(AppRoute.Tools, strings.navTools, Icons.Filled.Build to Icons.Outlined.Build),
    Triple(AppRoute.Settings, strings.navSettings, Icons.Filled.Settings to Icons.Outlined.Settings),
  )

  fun notify(msg: String) {
    scope.launch { snackbarHostState.showSnackbar(msg) }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (isTopLevel && !useRail) {
        NavigationBar(windowInsets = WindowInsets.navigationBars) {
          topTabs.forEachIndexed { idx, (route, label, icons) ->
            val selected = when (idx) {
              0 -> currentDest?.hasRoute<AppRoute.Home>() == true
              1 -> currentDest?.hasRoute<AppRoute.Files>() == true
              2 -> currentDest?.hasRoute<AppRoute.Tools>() == true
              else -> currentDest?.hasRoute<AppRoute.Settings>() == true
            }
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(route) {
                  popUpTo<AppRoute.Home> { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = { Icon(if (selected) icons.first else icons.second, contentDescription = label) },
              label = { Text(label, maxLines = 1) },
              modifier = Modifier.testTag("bottom_nav_$idx"),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (isTopLevel && useRail) {
        NavigationRail(modifier = Modifier.fillMaxHeight()) {
          topTabs.forEachIndexed { idx, (route, label, icons) ->
            val selected = when (idx) {
              0 -> currentDest?.hasRoute<AppRoute.Home>() == true
              1 -> currentDest?.hasRoute<AppRoute.Files>() == true
              2 -> currentDest?.hasRoute<AppRoute.Tools>() == true
              else -> currentDest?.hasRoute<AppRoute.Settings>() == true
            }
            NavigationRailItem(
              selected = selected,
              onClick = {
                navController.navigate(route) {
                  popUpTo<AppRoute.Home> { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = { Icon(if (selected) icons.first else icons.second, contentDescription = label) },
              label = { Text(label) },
              modifier = Modifier.testTag("rail_nav_$idx"),
            )
          }
        }
      }

      Box(modifier = Modifier.weight(1f).fillMaxSize()) {
        NavHost(
          navController = navController,
          startDestination = AppRoute.Home,
        ) {
          composable<AppRoute.Home> {
            DashboardScreen(
              settings = settings,
              recentDocs = recentDocs,
              favoriteCount = favoriteDocs.size,
              onImportPdf = { uri, cb -> viewModel.importPdf(uri, cb) },
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onOpenEditor = { showEditorPicker = true },
              onOpenEditorForDoc = { id -> navController.navigate(AppRoute.Editor(id)) },
              onOpenMerge = { navController.navigate(AppRoute.MergeSplit(initialTab = 0)) },
              onOpenSplit = { navController.navigate(AppRoute.MergeSplit(initialTab = 1)) },
              onOpenCompress = { navController.navigate(AppRoute.Compress()) },
              onOpenPdfToWord = { navController.navigate(AppRoute.Converter(initialMode = 0)) },
              onOpenPdfToJpg = { navController.navigate(AppRoute.Converter(initialMode = 2)) },
              onOpenJpgToPdf = { navController.navigate(AppRoute.Converter(initialMode = 4)) },
              onOpenScanner = { navController.navigate(AppRoute.Scanner) },
              onOpenSignature = { navController.navigate(AppRoute.SignatureWatermark(initialTab = 0)) },
              onOpenWatermark = { navController.navigate(AppRoute.SignatureWatermark(initialTab = 1)) },
              onOpenProtect = { navController.navigate(AppRoute.Security(initialTab = 0)) },
              onOpenUnlock = { navController.navigate(AppRoute.Security(initialTab = 1)) },
              onOpenOcr = { navController.navigate(AppRoute.Ocr()) },
              onOpenExtractImages = { navController.navigate(AppRoute.Converter(initialMode = 5)) },
              onOpenPageOrganizer = { navController.navigate(AppRoute.PageOrganizer()) },
              onOpenPageOrganizerForDoc = { id -> navController.navigate(AppRoute.PageOrganizer(id)) },
              onOpenRecentFiles = { navController.navigate(AppRoute.Files("ALL")) },
              onOpenFavorites = { navController.navigate(AppRoute.Files("FAVORITES")) },
              onOpenSettings = { navController.navigate(AppRoute.Settings) },
              onToggleThemeQuick = { viewModel.setThemeMode(it) },
              onToggleLanguageQuick = { viewModel.setLanguage(it) },
              onToggleFavorite = { viewModel.toggleFavorite(it) },
              onRenameDoc = { d, n -> viewModel.renameDocument(d, n) },
              onDeleteDoc = { viewModel.deleteDocument(it) },
            )
          }

          composable<AppRoute.Files> { entry ->
            val args = entry.toRoute<AppRoute.Files>()
            val initialFilter = when (args.filter) {
              "FAVORITES" -> FilesFilter.FAVORITES
              "PROTECTED" -> FilesFilter.PROTECTED
              else -> FilesFilter.ALL
            }
            FilesScreen(
              documents = allDocs,
              initialFilter = initialFilter,
              onImportPdf = { uri -> viewModel.importPdf(uri) },
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onOpenEditor = { id -> navController.navigate(AppRoute.Editor(id)) },
              onOpenOrganizer = { id -> navController.navigate(AppRoute.PageOrganizer(id)) },
              onToggleFavorite = { viewModel.toggleFavorite(it) },
              onRenameDoc = { d, n -> viewModel.renameDocument(d, n) },
              onDeleteDoc = { viewModel.deleteDocument(it) },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.Tools> {
            ToolsHubScreen(
              onOpenEditor = { showEditorPicker = true },
              onOpenMerge = { navController.navigate(AppRoute.MergeSplit(initialTab = 0)) },
              onOpenSplit = { navController.navigate(AppRoute.MergeSplit(initialTab = 1)) },
              onOpenCompress = { navController.navigate(AppRoute.Compress()) },
              onOpenPageOrganizer = { navController.navigate(AppRoute.PageOrganizer()) },
              onOpenPdfToWord = { navController.navigate(AppRoute.Converter(initialMode = 0)) },
              onOpenWordToPdf = { navController.navigate(AppRoute.Converter(initialMode = 1)) },
              onOpenPdfToJpg = { navController.navigate(AppRoute.Converter(initialMode = 2)) },
              onOpenPdfToPng = { navController.navigate(AppRoute.Converter(initialMode = 3)) },
              onOpenJpgToPdf = { navController.navigate(AppRoute.Converter(initialMode = 4)) },
              onOpenExtractImages = { navController.navigate(AppRoute.Converter(initialMode = 5)) },
              onOpenScanner = { navController.navigate(AppRoute.Scanner) },
              onOpenOcr = { navController.navigate(AppRoute.Ocr()) },
              onOpenSignature = { navController.navigate(AppRoute.SignatureWatermark(initialTab = 0)) },
              onOpenWatermark = { navController.navigate(AppRoute.SignatureWatermark(initialTab = 1)) },
              onOpenProtect = { navController.navigate(AppRoute.Security(initialTab = 0)) },
              onOpenUnlock = { navController.navigate(AppRoute.Security(initialTab = 1)) },
            )
          }

          composable<AppRoute.Settings> {
            SettingsScreen(
              settings = settings,
              storageStats = storageStats,
              onSetLanguage = { viewModel.setLanguage(it) },
              onSetThemeMode = { viewModel.setThemeMode(it) },
              onSetExportQuality = { viewModel.setExportQuality(it) },
              onSetLocalOnlyMode = { viewModel.setLocalOnlyMode(it) },
              onRefreshStorage = { viewModel.refreshStorageStats() },
              onClearCache = { viewModel.clearCache(strings.cacheCleared) },
            )
          }

          composable<AppRoute.Viewer> { entry ->
            val args = entry.toRoute<AppRoute.Viewer>()
            PdfViewerScreen(
              docId = args.docId,
              container = container,
              onBack = { navController.popBackStack() },
              onOpenEditor = { id -> navController.navigate(AppRoute.Editor(id)) },
              onOpenOrganizer = { id -> navController.navigate(AppRoute.PageOrganizer(id)) },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.Editor> { entry ->
            val args = entry.toRoute<AppRoute.Editor>()
            PdfEditorScreen(
              docId = args.docId,
              container = container,
              onBack = { navController.popBackStack() },
              onSaved = { _ -> viewModel.refreshStorageStats() },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.MergeSplit> { entry ->
            val args = entry.toRoute<AppRoute.MergeSplit>()
            MergeSplitScreen(
              initialTab = args.initialTab,
              preselectedDocId = args.docId,
              allDocuments = allDocs,
              container = container,
              onImportPdf = { uri, cb -> viewModel.importPdf(uri, cb) },
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onBack = { navController.popBackStack() },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.Compress> { entry ->
            val args = entry.toRoute<AppRoute.Compress>()
            CompressScreen(
              preselectedDocId = args.docId,
              allDocuments = allDocs,
              container = container,
              onImportPdf = { uri, cb -> viewModel.importPdf(uri, cb) },
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onBack = { navController.popBackStack() },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.Converter> { entry ->
            val args = entry.toRoute<AppRoute.Converter>()
            ConverterScreen(
              initialMode = args.initialMode,
              preselectedDocId = args.docId,
              exportQuality = settings.exportQuality,
              allDocuments = allDocs,
              container = container,
              onImportPdf = { uri, cb -> viewModel.importPdf(uri, cb) },
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onBack = { navController.popBackStack() },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.Scanner> {
            ScannerScreen(
              container = container,
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onBack = { navController.popBackStack() },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.Ocr> { entry ->
            val args = entry.toRoute<AppRoute.Ocr>()
            OcrScreen(
              preselectedDocId = args.docId,
              allDocuments = allDocs,
              container = container,
              onImportPdf = { uri, cb -> viewModel.importPdf(uri, cb) },
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onBack = { navController.popBackStack() },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.SignatureWatermark> { entry ->
            val args = entry.toRoute<AppRoute.SignatureWatermark>()
            SignatureWatermarkScreen(
              initialTab = args.initialTab,
              preselectedDocId = args.docId,
              allDocuments = allDocs,
              container = container,
              onImportPdf = { uri, cb -> viewModel.importPdf(uri, cb) },
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onBack = { navController.popBackStack() },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.Security> { entry ->
            val args = entry.toRoute<AppRoute.Security>()
            SecurityScreen(
              initialTab = args.initialTab,
              preselectedDocId = args.docId,
              allDocuments = allDocs,
              container = container,
              onImportPdf = { uri, cb -> viewModel.importPdf(uri, cb) },
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onBack = { navController.popBackStack() },
              onNotify = ::notify,
            )
          }

          composable<AppRoute.PageOrganizer> { entry ->
            val args = entry.toRoute<AppRoute.PageOrganizer>()
            PageOrganizerScreen(
              preselectedDocId = args.docId,
              allDocuments = allDocs,
              container = container,
              onImportPdf = { uri, cb -> viewModel.importPdf(uri, cb) },
              onOpenViewer = { id -> navController.navigate(AppRoute.Viewer(id)) },
              onBack = { navController.popBackStack() },
              onNotify = ::notify,
            )
          }
        }

        BusyOverlay(isImporting)
      }
    }
  }

  if (showEditorPicker) {
    PdfPickerSheet(
      documents = allDocs,
      onSelectDoc = { doc -> navController.navigate(AppRoute.Editor(doc.id)) },
      onImportUri = { uri ->
        viewModel.importPdf(uri) { doc -> navController.navigate(AppRoute.Editor(doc.id)) }
      },
      onDismiss = { showEditorPicker = false },
    )
  }
}
