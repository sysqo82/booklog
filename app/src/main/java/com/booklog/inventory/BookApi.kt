package com.booklog.inventory

import retrofit2.http.GET
import retrofit2.http.Query

data class BookResponse(
    val items: List<BookItem>?,
    val totalItems: Int
)

data class BookItem(
    val id: String,
    val volumeInfo: VolumeInfo
)

data class VolumeInfo(
    val title: String,
    val authors: List<String>?,
    val imageLinks: ImageLinks?,
    val description: String?,
    val publishedDate: String?
)

data class ImageLinks(
    val thumbnail: String?
)

interface BookApiService {
    @GET("volumes")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("startIndex") startIndex: Int = 0,
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") apiKey: String
    ): BookResponse

    @GET("volumes")
    suspend fun searchByISBN(
        @Query("q") isbn: String,
        @Query("key") apiKey: String
    ): BookResponse
}
