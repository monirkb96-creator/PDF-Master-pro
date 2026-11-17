package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PdfDocumentEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun pdfDocumentDao(): PdfDocumentDao

  companion object {
    fun build(context: Context): AppDatabase =
      Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "pdf_master_pro.db",
      ).fallbackToDestructiveMigration(dropAllTables = true).build()
  }
}
