package com.booklog.inventory

import android.content.Context

class BookRepository(context: Context) {
    private val bookDao = BookDatabase.getDatabase(context).bookDao()

    suspend fun addBook(book: Book) {
        bookDao.insert(BookEntity(
            id = book.id,
            title = book.title,
            author = book.author,
            thumbnail = book.thumbnail,
            description = book.description
        ))
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

    suspend fun isBookSaved(id: String): Boolean {
        return bookDao.getBook(id) != null
    }
}
