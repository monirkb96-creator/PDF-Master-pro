package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.AppContainer
import com.example.core.i18n.LocalStrings
import com.example.data.local.PdfDocumentEntity
import com.example.ui.components.BusyOverlay
import com.example.ui.components.PdfPickerSheet
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
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
  var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
  var showPicker by remember { mutableStateOf(false) }
  var isBusy by remember { mutableStateOf(false) }

  var userPassword by remember { mutableStateOf("") }
  var ownerPassword by remember { mutableStateOf("") }
  var allowPrinting by remember { mutableStateOf(true) }
  var allowCopying by remember { mutableStateOf(false) }
  var unlockPassword by remember { mutableStateOf("") }

  LaunchedEffect(preselectedDocId) {
    if (preselectedDocId > 0) {
      selectedDoc = container.documentRepository.getDocumentById(preselectedDocId)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (selectedTab == 0) strings.toolProtectPdf else strings.toolUnlockPdf) },
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
            text = { Text(strings.toolProtectPdf) },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text(strings.toolUnlockPdf) },
            icon = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
          )
        }

        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))) {
              Text(
                text = strings.securityNote,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          item {
            Button(
              onClick = { showPicker = true },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("security_pick_btn"),
            ) {
              Icon(Icons.Filled.FolderOpen, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(selectedDoc?.displayName ?: strings.selectFile)
            }
          }

          if (selectedTab == 0) {
            item {
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  OutlinedTextField(
                    value = userPassword,
                    onValueChange = { userPassword = it },
                    label = { Text(strings.userPasswordLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("protect_user_pw"),
                  )
                  OutlinedTextField(
                    value = ownerPassword,
                    onValueChange = { ownerPassword = it },
                    label = { Text(strings.ownerPasswordLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                  )
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(strings.allowPrinting, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = allowPrinting, onCheckedChange = { allowPrinting = it })
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(strings.allowCopying, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = allowCopying, onCheckedChange = { allowCopying = it })
                  }
                  Button(
                    onClick = {
                      val d = selectedDoc ?: return@Button
                      scope.launch {
                        isBusy = true
                        val out = container.documentRepository.createVaultFile("protected_${d.displayName}")
                        container.pdfEngine.protectPdf(
                          sourceFile = File(d.filePath),
                          outputFile = out,
                          userPassword = userPassword,
                          ownerPassword = ownerPassword,
                          allowPrinting = allowPrinting,
                          allowCopying = allowCopying,
                        ).onSuccess { file ->
                          val reg = container.documentRepository.registerPdfFile(file)
                          userPassword = ""
                          ownerPassword = ""
                          isBusy = false
                          onNotify(strings.savedSuccess)
                          onOpenViewer(reg.id)
                        }.onFailure { err ->
                          isBusy = false
                          onNotify(err.message ?: strings.errorGeneric)
                        }
                      }
                    },
                    enabled = selectedDoc != null && userPassword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("protect_execute_btn"),
                  ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.protectNow)
                  }
                }
              }
            }
          } else {
            item {
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  OutlinedTextField(
                    value = unlockPassword,
                    onValueChange = { unlockPassword = it },
                    label = { Text(strings.enterPassword) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("unlock_pw_input"),
                  )
                  Button(
                    onClick = {
                      val d = selectedDoc ?: return@Button
                      scope.launch {
                        isBusy = true
                        val out = container.documentRepository.createVaultFile("unlocked_${d.displayName}")
                        container.pdfEngine.unlockPdf(File(d.filePath), out, unlockPassword)
                          .onSuccess { file ->
                            val reg = container.documentRepository.registerPdfFile(file)
                            unlockPassword = ""
                            isBusy = false
                            onNotify(strings.savedSuccess)
                            onOpenViewer(reg.id)
                          }
                          .onFailure { err ->
                            isBusy = false
                            val msg = if (err.message?.contains("not encrypted", ignoreCase = true) == true) {
                              strings.notEncryptedMsg
                            } else {
                              strings.wrongPassword
                            }
                            onNotify(msg)
                          }
                      }
                    },
                    enabled = selectedDoc != null && unlockPassword.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("unlock_execute_btn"),
                  ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.unlockNow)
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

  if (showPicker) {
    PdfPickerSheet(
      documents = allDocuments,
      onSelectDoc = { selectedDoc = it },
      onImportUri = { uri -> onImportPdf(uri) { selectedDoc = it } },
      onDismiss = { showPicker = false },
    )
  }
}
