package com.booklog.inventory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookEnrichmentMatcherTest {

    @Test
    fun isTitleMatch_exactOnly_matchesNormalizedTitles() {
        assertTrue(BookEnrichmentMatcher.isTitleMatch("The Hobbit", "hobbit", exactOnly = true))
        assertTrue(BookEnrichmentMatcher.isTitleMatch("Clean Code!", "clean code", exactOnly = true))
        assertFalse(BookEnrichmentMatcher.isTitleMatch("Clean Code: A Handbook", "Clean Code", exactOnly = true))
    }

    @Test
    fun isTitleMatch_fuzzy_matchesSubtitlesAndSubstrings() {
        assertTrue(BookEnrichmentMatcher.isTitleMatch("Clean Code: A Handbook of Agile", "Clean Code"))
        assertTrue(BookEnrichmentMatcher.isTitleMatch("The Hobbit", "Hobbit: Or There and Back Again"))
        assertFalse(BookEnrichmentMatcher.isTitleMatch("Harry Potter", "Lord of the Rings"))
    }

    @Test
    fun isAuthorMatch_handlesNullOrEmptyAndSubstrings() {
        assertTrue(BookEnrichmentMatcher.isAuthorMatch("J.R.R. Tolkien", null))
        assertTrue(BookEnrichmentMatcher.isAuthorMatch("J.R.R. Tolkien", emptyList()))
        assertTrue(BookEnrichmentMatcher.isAuthorMatch("Tolkien", listOf("J. R. R. Tolkien")))
        assertFalse(BookEnrichmentMatcher.isAuthorMatch("Tolkien", listOf("George R.R. Martin")))
    }

    @Test
    fun isThumbnailUpgrade_upgradesFromOpenLibraryToGoogle() {
        assertTrue(
            BookEnrichmentMatcher.isThumbnailUpgrade(
                current = "https://covers.openlibrary.org/b/id/123-M.jpg",
                new = "https://books.google.com/books/content?id=123"
            )
        )
        assertFalse(
            BookEnrichmentMatcher.isThumbnailUpgrade(
                current = "https://books.google.com/books/content?id=123",
                new = "https://covers.openlibrary.org/b/id/123-M.jpg"
            )
        )
        assertTrue(BookEnrichmentMatcher.isThumbnailUpgrade(current = null, new = "https://books.google.com/cover.jpg"))
        assertFalse(BookEnrichmentMatcher.isThumbnailUpgrade(current = "https://books.google.com/cover.jpg", new = null))
    }

    @Test
    fun isDescriptionUpgrade_detectsQualityAndLengthImprovements() {
        // Short description upgrade
        assertTrue(
            BookEnrichmentMatcher.isDescriptionUpgrade(
                current = "A book about coding.",
                new = "A comprehensive handbook detailing agile software craftsmanship and best practices for developers."
            )
        )

        // List of keywords to synopsis upgrade
        assertTrue(
            BookEnrichmentMatcher.isDescriptionUpgrade(
                current = "Software, Java, Coding, Agile, Patterns",
                new = "This book introduces key concepts in agile software development. It covers principles, patterns, and practices."
            )
        )

        // Length increase upgrade
        val current = "A ".repeat(10)
        val newLong = "A detailed explanation of " + "words ".repeat(25)
        assertTrue(BookEnrichmentMatcher.isDescriptionUpgrade(current = current, new = newLong))

        // Same or shorter description should not upgrade
        assertFalse(BookEnrichmentMatcher.isDescriptionUpgrade(current = newLong, new = current))
    }
}
