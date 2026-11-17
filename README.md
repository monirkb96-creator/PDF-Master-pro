# PDF Master Pro (Android)

**Tagline:** *Your Documents, Your Control.*

An offline-first, bilingual (English & বাংলা) PDF Editor, Viewer, Scanner, OCR, Converter, E-Signature, and Document Management Android application built with Kotlin, Jetpack Compose, Material Design 3, Apache PDFBox-Android, Android `PdfRenderer`, CameraX, ML Kit, and Tesseract4Android.

## Core Capabilities

- **Dashboard & File Management:** Local PDF vault backed by Room and `filesDir`, with rendered page thumbnails, search, favorites, protected filter, SAF import (`OpenDocument`), export (`CreateDocument`), rename, share (`FileProvider`), and confirmed deletion.
- **PDF Viewer:** Pinch-to-zoom, fit-to-width / fit-to-page, full-screen mode, page navigation, full-text search, thumbnail sidebar, rotation, document metadata info, printing (`PrintManager`), share, and password-protected PDF unlocking into private volatile cache.
- **PDF Editor:** Add text (English & Unicode বাংলা via bundled Noto Sans Bengali), transparent Overlay & Redact workflow, add images (`PickVisualMedia`), vector shapes (rectangle, oval, line, arrow), freehand pen, highlight, underline, sticky-note comments, headers/footers/page numbers, undo/redo stacks, and save/export.
- **Merge & Split:** Multi-PDF queue reordering and merging; split every page, extract selected pages, extract page ranges, or split into equal chunks.
- **Compress PDF:** Low, Medium, and High compression with honest original vs. compressed size reporting.
- **Converters:** PDF to Word (`.docx`), Word (`.docx`) to PDF, PDF to JPG/PNG, Images to PDF, and Extract Embedded Images.
- **PDF Scanner:** CameraX live capture + Photo Picker import, margin cropping, 90° rotation, brightness & contrast tuning, Original / Grayscale / B&W filters, and multi-page PDF export.
- **OCR Scanner:** Offline English recognition (ML Kit) and Bengali/English recognition (Tesseract4Android with `.traineddata` import or user-triggered download), text search/copy/`.txt` export, and Searchable PDF generation.
- **E-Signature & Watermark:** Touch signature canvas or uploaded signature image placement; customizable text/image watermarks with opacity, scale, angle, and position.
- **PDF Security & Page Organizer:** AES password encryption (`StandardProtectionPolicy`) and genuine password-verified unlocking; thumbnail grid to reorder, rotate, duplicate, delete, insert blank pages, import pages, and extract selected pages.
- **Bilingual & Theme Support:** Instant switching between **English** and **বাংলা (Bangla)** with persisted DataStore preferences, plus Light, Dark, and System themes.

## Building the Android APK

1. Ensure JDK 17+ and Android SDK (compileSdk 36, minSdk 24) are configured.
2. Run unit & Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
3. Build the debug APK:
   ```bash
   gradle :app:assembleDebug
   ```
4. Build a release APK/AAB using your configured signing keystore.
