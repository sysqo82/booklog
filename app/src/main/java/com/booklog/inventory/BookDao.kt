package com.booklog.inventory

import androidx.room.*

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBook(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): BookEntity?

    @Query("SELECT * FROM books WHERE LOWER(title) = LOWER(:title) AND LOWER(author) = LOWER(:author) LIMIT 1")
    suspend fun findByTitleAndAuthor(title: String, author: String): BookEntity?

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE books SET thumbnail = :thumbnail, description = :description WHERE id = :id")
    suspend fun updateBookMetadata(id: String, thumbnail: String?, description: String?)

    @Query("UPDATE books SET isInWishlist = :isInWishlist WHERE id = :id")
    suspend fun updateWishlistStatus(id: String, isInWishlist: Boolean)

    @Query("UPDATE books SET isbn = :isbn, thumbnail = :thumbnail, description = :description WHERE id = :id")
    suspend fun updateFullMetadata(id: String, isbn: String?, thumbnail: String?, description: String?)

    @Query("SELECT * FROM books WHERE thumbnail IS NULL OR thumbnail = '' OR thumbnail LIKE '%openlibrary.org%' OR description IS NULL OR description = ''")
    suspend fun getBooksToEnrich(): List<BookEntity>
}
