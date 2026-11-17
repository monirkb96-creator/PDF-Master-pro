package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val displayName: String,
  val filePath: String,
  val thumbnailPath: String? = null,
  val sizeBytes: Long,
  val pageCount: Int,
  val lastModified: Long,
  val lastOpened: Long = lastModified,
  val isFavorite: Boolean = false,
  val isPasswordProtected: Boolean = false,
)
