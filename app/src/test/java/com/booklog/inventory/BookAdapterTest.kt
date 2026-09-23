package com.booklog.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

class BookAdapterTest {

    @Test
    fun adapter_managesBookListCorrectly() {
        val adapter = BookAdapter()
        assertEquals(0, adapter.itemCount)

        val book1 = Book(
            id = "1",
            title = "Dune",
            author = "Frank Herbert",
            isbn = "9780441172719",
            thumbnail = null,
            description = "Sci-Fi Masterpiece",
            isInWishlist = false
        )
        val book2 = Book(
            id = "2",
            title = "Foundation",
            author = "Isaac Asimov",
            isbn = "9780553293357",
            thumbnail = null,
            description = "Sci-Fi Classic",
            isInWishlist = true
        )

        adapter.addBooks(listOf(book1, book2))
        assertEquals(2, adapter.itemCount)

        adapter.clear()
        assertEquals(0, adapter.itemCount)
    }
}
