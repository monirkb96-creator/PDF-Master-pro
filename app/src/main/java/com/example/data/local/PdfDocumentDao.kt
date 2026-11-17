package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {
  @Query("SELECT * FROM pdf_documents ORDER BY lastOpened DESC")
  fun observeAllDocuments(): Flow<List<PdfDocumentEntity>>

  @Query("SELECT * FROM pdf_documents ORDER BY lastOpened DESC LIMIT :limit")
  fun observeRecentDocuments(limit: Int = 10): Flow<List<PdfDocumentEntity>>

  @Query("SELECT * FROM pdf_documents WHERE isFavorite = 1 ORDER BY lastModified DESC")
  fun observeFavoriteDocuments(): Flow<List<PdfDocumentEntity>>

  @Query("SELECT * FROM pdf_documents WHERE id = :id")
  suspend fun getDocumentById(id: Long): PdfDocumentEntity?

  @Query("SELECT * FROM pdf_documents WHERE filePath = :path LIMIT 1")
  suspend fun getDocumentByPath(path: String): PdfDocumentEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(doc: PdfDocumentEntity): Long

  @Update
  suspend fun update(doc: PdfDocumentEntity)

  @Query("DELETE FROM pdf_documents WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("UPDATE pdf_documents SET isFavorite = :favorite WHERE id = :id")
  suspend fun setFavorite(id: Long, favorite: Boolean)

  @Query("UPDATE pdf_documents SET lastOpened = :timestamp WHERE id = :id")
  suspend fun touchOpened(id: Long, timestamp: Long = System.currentTimeMillis())
}
