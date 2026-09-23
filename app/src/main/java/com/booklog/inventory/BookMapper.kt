package com.booklog.inventory

object BookMapper {

    fun mapBookItemToBook(item: BookItem): Book {
        val isbn = item.volumeInfo.industryIdentifiers?.find { it.type == "ISBN_13" }?.identifier
            ?: item.volumeInfo.industryIdentifiers?.find { it.type == "ISBN_10" }?.identifier
        return Book(
            id = item.id,
            title = item.volumeInfo.title,
            author = item.volumeInfo.authors?.firstOrNull() ?: "Unknown",
            isbn = isbn,
            thumbnail = item.volumeInfo.imageLinks?.thumbnail?.replace("http:", "https:"),
            description = item.volumeInfo.description,
            isInWishlist = false,
        )
    }

    fun mapOLDocToBook(doc: OLDoc): Book {
        val coverUrl = doc.cover_i?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
        return Book(
            id = doc.key.replace("/works/", ""),
            title = doc.title,
            author = doc.author_name?.firstOrNull() ?: "Unknown",
            isbn = doc.isbn?.firstOrNull(),
            thumbnail = coverUrl,
            description = null,
            isInWishlist = false,
        )
    }

    fun mapOLBookDataToBook(key: String, data: OLBookData): Book {
        return Book(
            id = key.replace("ISBN:", ""),
            title = data.title,
            author = data.authors?.firstOrNull()?.name ?: "Unknown",
            isbn = data.isbn_13?.firstOrNull() ?: data.isbn_10?.firstOrNull(),
            thumbnail = data.cover?.medium ?: data.cover?.large,
            description = data.subjects?.joinToString(", ") { it.name },
            isInWishlist = false,
        )
    }

    fun normalizeIsbn(isbn: String?): String? {
        if (isbn.isNullOrBlank()) return null
        return isbn.replace(Regex("[^0-9X]"), "")
    }

    fun normalizeTitleAuthor(title: String, author: String): String {
        val cleanTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanAuthor = author.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "$cleanTitle|$cleanAuthor"
    }
}
