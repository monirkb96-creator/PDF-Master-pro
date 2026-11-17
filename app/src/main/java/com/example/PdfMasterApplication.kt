package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.DocumentRepository
import com.example.domain.engine.DocxEngine
import com.example.domain.engine.OcrEngine
import com.example.domain.engine.PdfManipulationEngine
import com.example.domain.engine.PdfRendererHelper
import com.example.domain.engine.ScannerEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AppContainer(application: Application) {
  private val database = AppDatabase.build(application)
  val preferencesRepository = PreferencesRepository(application)
  val documentRepository = DocumentRepository(application, database.pdfDocumentDao())
  val pdfRendererHelper = PdfRendererHelper(documentRepository.unlockedCacheDir)
  val pdfEngine = PdfManipulationEngine(application)
  val docxEngine = DocxEngine(application)
  val scannerEngine = ScannerEngine()
  val ocrEngine = OcrEngine(application, documentRepository.tessdataDir)
}

class PdfMasterApplication : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    PDFBoxResourceLoader.init(applicationContext)
    container = AppContainer(this)
  }
}
