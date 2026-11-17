package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
  @Serializable data object Home : AppRoute
  @Serializable data class Files(val filter: String = "ALL") : AppRoute
  @Serializable data object Tools : AppRoute
  @Serializable data object Settings : AppRoute
  @Serializable data class Viewer(val docId: Long) : AppRoute
  @Serializable data class Editor(val docId: Long) : AppRoute
  @Serializable data class MergeSplit(val initialTab: Int = 0, val docId: Long = -1L) : AppRoute
  @Serializable data class Compress(val docId: Long = -1L) : AppRoute
  @Serializable data class Converter(val initialMode: Int = 0, val docId: Long = -1L) : AppRoute
  @Serializable data object Scanner : AppRoute
  @Serializable data class Ocr(val docId: Long = -1L) : AppRoute
  @Serializable data class SignatureWatermark(val initialTab: Int = 0, val docId: Long = -1L) : AppRoute
  @Serializable data class Security(val initialTab: Int = 0, val docId: Long = -1L) : AppRoute
  @Serializable data class PageOrganizer(val docId: Long = -1L) : AppRoute
}
