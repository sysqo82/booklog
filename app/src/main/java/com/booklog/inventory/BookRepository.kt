package com.booklog.inventory

import android.content.Context

class BookRepository(context: Context) {
    private val bookDao = BookDatabase.getDatabase(context).bookDao()

    suspend fun addBook(book: Book) {
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
            description = book.description
        ))
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

    suspend fun getBook(id: String): BookEntity? {
        return bookDao.getBook(id)
    }

    suspend fun getBooksToEnrich(): List<BookEntity> {
        return bookDao.getBooksToEnrich()
    }

    suspend fun updateBookMetadata(id: String, thumbnail: String?, description: String?) {
        bookDao.updateBookMetadata(id, thumbnail, description)
    }

    suspend fun updateFullMetadata(id: String, isbn: String?, thumbnail: String?, description: String?) {
        bookDao.updateFullMetadata(id, isbn, thumbnail, description)
    }

    suspend fun isBookSaved(id: String, isbn: String? = null, title: String? = null, author: String? = null): Boolean {
        if (bookDao.getBook(id) != null) return true
        
        val normalizedIsbn = isbn?.replace(Regex("[^0-9X]"), "")
        if (normalizedIsbn != null && bookDao.findByIsbn(normalizedIsbn) != null) return true
        
        if (title != null && author != null) {
            // Check by exact title/author first (database call)
            if (bookDao.findByTitleAndAuthor(title, author) != null) return true
            
            // Fuzzy check in memory for small variations
            val allBooks = bookDao.getAllBooks()
            val cleanTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")
            val cleanAuthor = author.lowercase().replace(Regex("[^a-z0-9]"), "")
            
            val matchFound = allBooks.any { 
                val itTitle = it.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                val itAuthor = it.author.lowercase().replace(Regex("[^a-z0-9]"), "")
                itTitle == cleanTitle && itAuthor == cleanAuthor
            }
            if (matchFound) return true
        }
        return false
    }
}
