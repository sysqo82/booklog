package com.booklog.inventory

import android.content.Context

class BookRepository(context: Context) {
    private val bookDao = BookDatabase.getDatabase(context).bookDao()

    suspend fun addBook(book: Book, isInWishlist: Boolean = false) {
        val normalizedIsbn = book.isbn?.replace(Regex("[^0-9X]"), "")
        val existing = if (normalizedIsbn != null) {
            bookDao.findByIsbn(normalizedIsbn)
        } else {
            bookDao.findByTitleAndAuthor(book.title, book.author)
        }
        
        val idToUse = existing?.id ?: book.id

        bookDao.insert(BookEntity(
            id = idToUse,
            title = book.title,
            author = book.author,
            isbn = normalizedIsbn,
            thumbnail = book.thumbnail,
            description = book.description,
            isInWishlist = isInWishlist,
        ))
    }

    suspend fun updateWishlistStatus(id: String, isInWishlist: Boolean) {
        bookDao.updateWishlistStatus(id, isInWishlist)
    }

    suspend fun findByIsbn(isbn: String): BookEntity? {
        val normalizedIsbn = isbn.replace(Regex("[^0-9X]"), "")
        return bookDao.findByIsbn(normalizedIsbn)
    }

    suspend fun findByTitleAndAuthor(title: String, author: String): BookEntity? {
        return bookDao.findByTitleAndAuthor(title, author)
    }

    suspend fun removeBook(id: String) {
        bookDao.deleteById(id)
    }

    suspend fun getMyBooks(): List<BookEntity> {
        return bookDao.getAllBooks()
    }

    suspend fun getBooksToEnrich(): List<BookEntity> {
        return bookDao.getBooksToEnrich()
    }

    suspend fun updateFullMetadata(id: String, isbn: String?, thumbnail: String?, description: String?) {
        bookDao.updateFullMetadata(id, isbn, thumbnail, description)
    }

}
