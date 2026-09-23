package com.booklog.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookMapperTest {

    @Test
    fun mapBookItemToBook_extractsIsbn13AndHttpsThumbnail() {
        val item = BookItem(
            id = "google_123",
            volumeInfo = VolumeInfo(
                title = "Clean Code",
                authors = listOf("Robert C. Martin"),
                imageLinks = ImageLinks(thumbnail = "http://books.google.com/cover.jpg"),
                description = "A Handbook of Agile Software Craftsmanship",
                publishedDate = "2008",
                industryIdentifiers = listOf(
                    IndustryIdentifier("ISBN_10", "0132350882"),
                    IndustryIdentifier("ISBN_13", "9780132350884")
                )
            )
        )

        val book = BookMapper.mapBookItemToBook(item)

        assertEquals("google_123", book.id)
        assertEquals("Clean Code", book.title)
        assertEquals("Robert C. Martin", book.author)
        assertEquals("9780132350884", book.isbn)
        assertEquals("https://books.google.com/cover.jpg", book.thumbnail)
        assertEquals("A Handbook of Agile Software Craftsmanship", book.description)
    }

    @Test
    fun mapBookItemToBook_fallbackToIsbn10WhenIsbn13Missing() {
        val item = BookItem(
            id = "google_456",
            volumeInfo = VolumeInfo(
                title = "Refactoring",
                authors = null,
                imageLinks = null,
                description = null,
                publishedDate = "1999",
                industryIdentifiers = listOf(
                    IndustryIdentifier("ISBN_10", "0201485672")
                )
            )
        )

        val book = BookMapper.mapBookItemToBook(item)

        assertEquals("google_456", book.id)
        assertEquals("Refactoring", book.title)
        assertEquals("Unknown", book.author)
        assertEquals("0201485672", book.isbn)
        assertNull(book.thumbnail)
        assertNull(book.description)
    }

    @Test
    fun mapOLDocToBook_mapsCorrectly() {
        val doc = OLDoc(
            key = "/works/OL27448W",
            title = "The Hobbit",
            author_name = listOf("J.R.R. Tolkien"),
            isbn = listOf("9780261102217"),
            cover_i = 8400123
        )

        val book = BookMapper.mapOLDocToBook(doc)

        assertEquals("OL27448W", book.id)
        assertEquals("The Hobbit", book.title)
        assertEquals("J.R.R. Tolkien", book.author)
        assertEquals("9780261102217", book.isbn)
        assertEquals("https://covers.openlibrary.org/b/id/8400123-M.jpg", book.thumbnail)
        assertNull(book.description)
    }

    @Test
    fun mapOLBookDataToBook_mapsCorrectly() {
        val data = OLBookData(
            title = "Design Patterns",
            authors = listOf(OLAuthor("Erich Gamma")),
            isbn_13 = listOf("9780201633610"),
            isbn_10 = listOf("0201633612"),
            cover = OLCover(medium = "https://covers.openlibrary.org/b/id/100-M.jpg", large = "https://covers.openlibrary.org/b/id/100-L.jpg"),
            subjects = listOf(OLSubject("Software Design"), OLSubject("Object Oriented"))
        )

        val book = BookMapper.mapOLBookDataToBook("ISBN:9780201633610", data)

        assertEquals("9780201633610", book.id)
        assertEquals("Design Patterns", book.title)
        assertEquals("Erich Gamma", book.author)
        assertEquals("9780201633610", book.isbn)
        assertEquals("https://covers.openlibrary.org/b/id/100-M.jpg", book.thumbnail)
        assertEquals("Software Design, Object Oriented", book.description)
    }

    @Test
    fun normalizeIsbn_stripsHyphensAndSpaces() {
        assertEquals("9780132350884", BookMapper.normalizeIsbn("978-0-13-235088-4"))
        assertEquals("020163361X", BookMapper.normalizeIsbn("0 201 63361 X"))
        assertNull(BookMapper.normalizeIsbn(""))
        assertNull(BookMapper.normalizeIsbn(null))
    }

    @Test
    fun normalizeTitleAuthor_createsConsistentKey() {
        val key1 = BookMapper.normalizeTitleAuthor("Clean Code: A Handbook", "Robert C. Martin")
        val key2 = BookMapper.normalizeTitleAuthor("clean code a handbook", "robert c martin")

        assertEquals("cleancodeahandbook|robertcmartin", key1)
        assertEquals(key1, key2)
    }
}
