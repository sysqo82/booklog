package com.booklog.inventory

import retrofit2.http.GET
import retrofit2.http.Query

data class OLSearchResponse(
    val numFound: Int,
    val docs: List<OLDoc>?
)

data class OLDoc(
    val title: String,
    val author_name: List<String>? = null,
    val isbn: List<String>? = null,
    val cover_i: Long? = null,
    val key: String,
    val first_publish_year: Int? = null,
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
    val authors: List<OLAuthor>? = null,
    val isbn_13: List<String>? = null,
    val isbn_10: List<String>? = null,
    val cover: OLCover? = null,
    val subjects: List<OLSubject>? = null,
    val url: String? = null,
)

data class OLAuthor(val name: String)
data class OLCover(val small: String? = null, val medium: String? = null, val large: String? = null)
data class OLSubject(val name: String, val url: String? = null)
