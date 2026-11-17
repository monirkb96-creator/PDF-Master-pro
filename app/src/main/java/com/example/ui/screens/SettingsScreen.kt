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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.ExportQuality
import com.example.core.i18n.LocalStrings
import com.example.core.i18n.ThemeMode
import com.example.core.util.FileUtils
import com.example.data.preferences.UserSettings
import com.example.data.repository.StorageStats

@Composable
fun SettingsScreen(
  settings: UserSettings,
  storageStats: StorageStats,
  onSetLanguage: (AppLanguage) -> Unit,
  onSetThemeMode: (ThemeMode) -> Unit,
  onSetExportQuality: (ExportQuality) -> Unit,
  onSetLocalOnlyMode: (Boolean) -> Unit,
  onRefreshStorage: () -> Unit,
  onClearCache: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val context = LocalContext.current
  var showPrivacyDialog by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { onRefreshStorage() }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 820.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = strings.navSettings,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
      }

      // Language Setting
      item {
        SettingsCard(title = strings.languageSetting, icon = Icons.Filled.Translate) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppLanguage.entries.forEach { lang ->
              FilterChip(
                selected = settings.language == lang,
                onClick = { onSetLanguage(lang) },
                label = { Text(lang.nativeName) },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("settings_lang_${lang.code}"),
              )
            }
          }
        }
      }

      // Theme Mode
      item {
        SettingsCard(title = strings.themeSetting, icon = null) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
              ThemeMode.SYSTEM to strings.themeSystem,
              ThemeMode.LIGHT to strings.themeLight,
              ThemeMode.DARK to strings.themeDark,
            ).forEach { (mode, label) ->
              FilterChip(
                selected = settings.themeMode == mode,
                onClick = { onSetThemeMode(mode) },
                label = { Text(label) },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("settings_theme_${mode.name.lowercase()}"),
              )
            }
          }
        }
      }

      // Export Quality
      item {
        SettingsCard(title = strings.exportQualitySetting, icon = null) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
              ExportQuality.LOW to strings.qualityLow,
              ExportQuality.MEDIUM to strings.qualityMedium,
              ExportQuality.HIGH to strings.qualityHigh,
            ).forEach { (q, label) ->
              FilterChip(
                selected = settings.exportQuality == q,
                onClick = { onSetExportQuality(q) },
                label = { Text(label) },
                modifier = Modifier.minimumInteractiveComponentSize(),
              )
            }
          }
        }
      }

      // Storage & Cache
      item {
        SettingsCard(title = strings.storageSection, icon = Icons.Filled.Storage) {
          Text(
            text = "${strings.vaultLocationLabel}: ${context.filesDir.absolutePath}/documents",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = "${strings.storageUsedLabel}: ${FileUtils.formatBytes(storageStats.totalBytes)} (${storageStats.documentCount} PDFs)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
          )
          Spacer(Modifier.height(10.dp))
          OutlinedButton(
            onClick = onClearCache,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("settings_clear_cache_btn"),
          ) {
            Icon(Icons.Filled.CleaningServices, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("${strings.clearCache} (${FileUtils.formatBytes(storageStats.cacheBytes)})")
          }
        }
      }

      // Privacy & Security
      item {
        SettingsCard(title = strings.privacySection, icon = Icons.Filled.Security) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(strings.offlineProcessingTitle, style = MaterialTheme.typography.titleSmall)
              Text(
                strings.offlineProcessingDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = settings.localOnlyMode,
              onCheckedChange = onSetLocalOnlyMode,
            )
          }
          Spacer(Modifier.height(10.dp))
          TextButton(
            onClick = { showPrivacyDialog = true },
            modifier = Modifier.testTag("settings_privacy_policy_btn"),
          ) {
            Icon(Icons.Filled.Policy, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text(strings.viewPrivacyPolicy)
          }
        }
      }

      // About
      item {
        SettingsCard(title = strings.aboutSection, icon = Icons.Filled.Info) {
          Text("PDF MASTER PRO", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(strings.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(4.dp))
          Text(
            text = "${strings.versionLabel}: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }

      item { Spacer(Modifier.height(12.dp)) }
    }
  }

  if (showPrivacyDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyDialog = false },
      title = { Text(strings.privacyPolicyTitle) },
      text = { Text(strings.privacyPolicyBody) },
      confirmButton = {
        Button(onClick = { showPrivacyDialog = false }) { Text(strings.close) }
      },
    )
  }
}

@Composable
private fun SettingsCard(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector?,
  content: @Composable () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) {
          Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
      Spacer(Modifier.height(10.dp))
      content()
    }
  }
}
