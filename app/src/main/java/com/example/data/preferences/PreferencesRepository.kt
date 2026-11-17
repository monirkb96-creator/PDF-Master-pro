package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.ExportQuality
import com.example.core.i18n.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_master_prefs")

data class UserSettings(
  val language: AppLanguage = AppLanguage.ENGLISH,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val exportQuality: ExportQuality = ExportQuality.HIGH,
  val localOnlyMode: Boolean = true,
)

class PreferencesRepository(private val context: Context) {
  private object Keys {
    val LANGUAGE = stringPreferencesKey("app_language")
    val THEME = stringPreferencesKey("theme_mode")
    val EXPORT_QUALITY = stringPreferencesKey("export_quality")
    val LOCAL_ONLY = booleanPreferencesKey("local_only_mode")
  }

  val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
    val lang = AppLanguage.entries.firstOrNull { it.code == prefs[Keys.LANGUAGE] } ?: AppLanguage.ENGLISH
    val theme = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME] } ?: ThemeMode.SYSTEM
    val quality = ExportQuality.entries.firstOrNull { it.name == prefs[Keys.EXPORT_QUALITY] } ?: ExportQuality.HIGH
    val localOnly = prefs[Keys.LOCAL_ONLY] ?: true
    UserSettings(lang, theme, quality, localOnly)
  }

  suspend fun setLanguage(language: AppLanguage) {
    context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { it[Keys.THEME] = mode.name }
  }

  suspend fun setExportQuality(quality: ExportQuality) {
    context.dataStore.edit { it[Keys.EXPORT_QUALITY] = quality.name }
  }

  suspend fun setLocalOnlyMode(enabled: Boolean) {
    context.dataStore.edit { it[Keys.LOCAL_ONLY] = enabled }
  }
}
