package com.booklog.inventory

import retrofit2.http.GET
import retrofit2.http.Query

data class OLSearchResponse(
    val numFound: Int,
    val docs: List<OLDoc>?
)

data class OLDoc(
    val title: String,
    val author_name: List<String>?,
    val isbn: List<String>?,
    val cover_i: Long?,
    val key: String,
    val first_publish_year: Int?
)

interface OpenLibraryService {
    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): OLSearchResponse

    @GET("api/books")
    suspend fun getBookByISBN(
        @Query("bibkeys") bibkeys: String, // e.g. "ISBN:9780140328721"
        @Query("format") format: String = "json",
        @Query("jscmd") jscmd: String = "data"
    ): Map<String, OLBookData>
}

data class OLBookData(
    val title: String,
    val authors: List<OLAuthor>?,
    val isbn_13: List<String>?,
    val isbn_10: List<String>?,
    val cover: OLCover?,
    val subjects: List<OLSubject>?,
    val url: String?
)

data class OLAuthor(val name: String)
data class OLCover(val small: String?, val medium: String?, val large: String?)
data class OLSubject(val name: String)
