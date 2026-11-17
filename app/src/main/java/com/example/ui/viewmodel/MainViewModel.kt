package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.i18n.AppLanguage
import com.example.core.i18n.ExportQuality
import com.example.core.i18n.ThemeMode
import com.example.data.local.PdfDocumentEntity
import com.example.data.preferences.UserSettings
import com.example.data.repository.StorageStats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val container: AppContainer) : ViewModel() {

  val settings: StateFlow<UserSettings> = container.preferencesRepository.settings
    .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

  val allDocuments: StateFlow<List<PdfDocumentEntity>> = container.documentRepository.allDocuments
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recentDocuments: StateFlow<List<PdfDocumentEntity>> = container.documentRepository.recentDocuments
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val favoriteDocuments: StateFlow<List<PdfDocumentEntity>> = container.documentRepository.favoriteDocuments
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _storageStats = MutableStateFlow(StorageStats(0, 0, 0, 0, 0))
  val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

  private val _isImporting = MutableStateFlow(false)
  val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val messages = _messages.asSharedFlow()

  init {
    refreshStorageStats()
  }

  fun importPdf(uri: Uri, onImported: (PdfDocumentEntity) -> Unit = {}) {
    viewModelScope.launch {
      _isImporting.value = true
      val res = container.documentRepository.importPdfFromUri(uri)
      _isImporting.value = false
      res.onSuccess { doc ->
        refreshStorageStats()
        onImported(doc)
      }.onFailure { err ->
        _messages.tryEmit(err.message ?: "Failed to import PDF")
      }
    }
  }

  fun toggleFavorite(doc: PdfDocumentEntity) {
    viewModelScope.launch {
      container.documentRepository.toggleFavorite(doc)
    }
  }

  fun renameDocument(doc: PdfDocumentEntity, newName: String) {
    viewModelScope.launch {
      container.documentRepository.renameDocument(doc, newName)
        .onFailure { _messages.tryEmit(it.message ?: "Rename failed") }
    }
  }

  fun deleteDocument(doc: PdfDocumentEntity) {
    viewModelScope.launch {
      container.documentRepository.deleteDocument(doc)
      refreshStorageStats()
    }
  }

  fun setLanguage(language: AppLanguage) {
    viewModelScope.launch { container.preferencesRepository.setLanguage(language) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { container.preferencesRepository.setThemeMode(mode) }
  }

  fun setExportQuality(quality: ExportQuality) {
    viewModelScope.launch { container.preferencesRepository.setExportQuality(quality) }
  }

  fun setLocalOnlyMode(enabled: Boolean) {
    viewModelScope.launch { container.preferencesRepository.setLocalOnlyMode(enabled) }
  }

  fun refreshStorageStats() {
    viewModelScope.launch {
      _storageStats.value = container.documentRepository.getStorageStats()
    }
  }

  fun clearCache(doneMessage: String) {
    viewModelScope.launch {
      container.documentRepository.clearCache()
      refreshStorageStats()
      _messages.tryEmit(doneMessage)
    }
  }

  fun emitMessage(msg: String) {
    _messages.tryEmit(msg)
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MainViewModel(container) as T
    }
  }
}
