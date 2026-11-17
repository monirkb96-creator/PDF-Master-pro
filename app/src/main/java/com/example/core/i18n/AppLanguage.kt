package com.example.core.i18n

enum class AppLanguage(val code: String, val nativeName: String) {
  ENGLISH("en", "English"),
  BANGLA("bn", "বাংলা"),
}

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

enum class ExportQuality(val jpegQuality: Int, val renderScale: Float) {
  LOW(45, 1.0f),
  MEDIUM(72, 1.5f),
  HIGH(92, 2.2f),
}
