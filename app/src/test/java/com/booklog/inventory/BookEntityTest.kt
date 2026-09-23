package com.booklog.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookEntityTest {

    @Test
    fun bookEntity_defaultValuesAreSetCorrectly() {
        val before = System.currentTimeMillis()
        val entity = BookEntity(
            id = "test_1",
            title = "Test Title",
            author = "Test Author",
            isbn = "1234567890",
            thumbnail = "http://example.com/cover.jpg",
            description = "Test Description"
        )
        val after = System.currentTimeMillis()

        assertEquals("test_1", entity.id)
        assertEquals("Test Title", entity.title)
        assertEquals("Test Author", entity.author)
        assertEquals("1234567890", entity.isbn)
        assertEquals("http://example.com/cover.jpg", entity.thumbnail)
        assertEquals("Test Description", entity.description)
        assertFalse(entity.isInWishlist)
        assertTrue(entity.addedAt in before..after)
    }
}
