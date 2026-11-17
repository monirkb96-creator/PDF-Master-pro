package com.example.domain.model

import androidx.compose.ui.geometry.Offset
import com.example.core.i18n.ExportQuality

enum class EditorTool {
  EDIT_TEXT,
  TEXT,
  SELECT,
  REDACT,
  IMAGE,
  SHAPE,
  PEN,
  HIGHLIGHT,
  UNDERLINE,
  COMMENT,
  HEADER_FOOTER,
}

enum class EditorFont(val label: String) {
  HELVETICA("Helvetica"),
  TIMES("Times"),
  COURIER("Courier"),
  BENGALI("বাংলা (Noto Sans)"),
}

enum class ShapeKind {
  RECTANGLE,
  CIRCLE,
  LINE,
  ARROW,
}

enum class WatermarkPosition {
  CENTER,
  DIAGONAL,
  TOP,
  BOTTOM,
}

enum class SplitMode {
  EVERY_PAGE,
  SELECTED_PAGES,
  PAGE_RANGE,
  CHUNKS,
}

enum class CompressionLevel(val quality: ExportQuality, val jpegQuality: Int, val maxDimPx: Int) {
  LOW(ExportQuality.HIGH, 82, 1800),
  MEDIUM(ExportQuality.MEDIUM, 58, 1280),
  HIGH(ExportQuality.LOW, 34, 900),
}

enum class ScannerFilter {
  ORIGINAL,
  GRAYSCALE,
  BLACK_AND_WHITE,
}

enum class OcrLanguageChoice(val code: String) {
  ENGLISH("eng"),
  BANGLA("ben"),
}

sealed interface PdfEditAction {
  val id: String
  val pageIndex: Int

  data class AddText(
    override val id: String,
    override val pageIndex: Int,
    val xNorm: Float,
    val yNorm: Float,
    val text: String,
    val fontSizeSp: Float,
    val font: EditorFont,
    val colorArgb: Int,
  ) : PdfEditAction

  data class RedactAndReplace(
    override val id: String,
    override val pageIndex: Int,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float,
    val replacementText: String,
    val fontSizeSp: Float,
    val textColorArgb: Int,
    val font: EditorFont = EditorFont.HELVETICA,
    val backgroundArgb: Int = android.graphics.Color.WHITE,
  ) : PdfEditAction

  data class AddImage(
    override val id: String,
    override val pageIndex: Int,
    val leftNorm: Float,
    val topNorm: Float,
    val widthNorm: Float,
    val heightNorm: Float,
    val imagePath: String,
  ) : PdfEditAction

  data class AddShape(
    override val id: String,
    override val pageIndex: Int,
    val shapeKind: ShapeKind,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float,
    val strokeColorArgb: Int,
    val strokeWidthPt: Float,
  ) : PdfEditAction

  data class AddPenStroke(
    override val id: String,
    override val pageIndex: Int,
    val pointsNorm: List<Offset>,
    val colorArgb: Int,
    val strokeWidthPt: Float,
  ) : PdfEditAction

  data class AddHighlight(
    override val id: String,
    override val pageIndex: Int,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float,
    val colorArgb: Int,
  ) : PdfEditAction

  data class AddUnderline(
    override val id: String,
    override val pageIndex: Int,
    val startXNorm: Float,
    val endXNorm: Float,
    val yNorm: Float,
    val colorArgb: Int,
  ) : PdfEditAction

  data class AddComment(
    override val id: String,
    override val pageIndex: Int,
    val xNorm: Float,
    val yNorm: Float,
    val author: String,
    val comment: String,
    val colorArgb: Int,
  ) : PdfEditAction
}

data class HeaderFooterConfig(
  val headerText: String = "",
  val footerText: String = "",
  val includePageNumbers: Boolean = false,
  val startNumber: Int = 1,
)

data class PdfDocInfo(
  val fileName: String,
  val sizeBytes: Long,
  val pageCount: Int,
  val lastModified: Long,
  val isEncrypted: Boolean,
  val title: String?,
  val author: String?,
  val creator: String?,
)

data class CompressionResult(
  val outputFile: java.io.File,
  val originalBytes: Long,
  val compressedBytes: Long,
) {
  val reduced: Boolean get() = compressedBytes < originalBytes
  val reductionPercent: Int
    get() = if (originalBytes > 0 && reduced) {
      (((originalBytes - compressedBytes).toDouble() / originalBytes) * 100.0).toInt()
    } else {
      0
    }
}

data class OrganizerPageItem(
  val uid: String,
  val sourceFilePath: String,
  val sourcePageIndex: Int, // -1 means blank page
  val rotationDegrees: Int = 0,
  val isBlank: Boolean = false,
  val selected: Boolean = false,
)

data class ScannedPageItem(
  val id: String,
  val rawImagePath: String,
  val rotationDegrees: Int = 0,
  val brightness: Float = 0f, // -100..100
  val contrast: Float = 1f, // 0.5..2.0
  val filter: ScannerFilter = ScannerFilter.ORIGINAL,
  val cropLeft: Float = 0f,
  val cropTop: Float = 0f,
  val cropRight: Float = 1f,
  val cropBottom: Float = 1f,
)

data class PdfTextBlock(
  val id: String,
  val pageIndex: Int,
  val text: String,
  val leftNorm: Float,
  val topNorm: Float,
  val rightNorm: Float,
  val bottomNorm: Float,
  val fontSizePt: Float,
)
