package com.booklog.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorAdapterTest {

    @Test
    fun filter_filtersListByQueryCaseInsensitivelyAndIncludesAllAuthorsHeader() {
        val authors = listOf("George R.R. Martin", "J.R.R. Tolkien", "Brandon Sanderson")
        val adapter = AuthorAdapter(authors) { }

        assertEquals(4, adapter.itemCount)

        adapter.filter("Tolkien")
        assertEquals(2, adapter.itemCount)

        adapter.filter("brand")
        assertEquals(2, adapter.itemCount)

        adapter.filter("")
        assertEquals(4, adapter.itemCount)
    }
}
