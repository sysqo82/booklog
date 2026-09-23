package com.booklog.inventory

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import retrofit2.HttpException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class BookEnrichmentWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("BookEnrichment", "Starting background enrichment task")
        val repository = BookRepository(applicationContext)
        val booksToEnrich = repository.getBooksToEnrich().take(25)
        
        if (booksToEnrich.isEmpty()) {
            Log.d("BookEnrichment", "No books found needing enrichment or upgrade")
            return Result.success()
        }

        Log.d("BookEnrichment", "Found ${booksToEnrich.size} books to enrich/upgrade")

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/books/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(BookApiService::class.java)
        
        val olRetrofit = Retrofit.Builder()
            .baseUrl("https://openlibrary.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val olService = olRetrofit.create(OpenLibraryService::class.java)

        val apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY.ifEmpty { null }

        var updatedCount = 0
        for (book in booksToEnrich) {
            try {
                var foundThumbnail: String? = null
                var foundDescription: String? = null
                var foundIsbn: String? = null
                var source = "None"

                // --- 1. Try Google Books with progressive strategies ---
                val strategies = mutableListOf<String>()
                if (!book.isbn.isNullOrBlank()) {
                    strategies.add("isbn:${book.isbn}")
                }
                strategies.add("intitle:${book.title} inauthor:${book.author}")
                strategies.add("${book.title} ${book.author}") // Broad search
                strategies.add(book.title) // Title only

                for (query in strategies) {
                    Log.d("BookEnrichment", "Strategy: Google query '$query' for '${book.title}'")
                    val response = try { 
                        apiService.searchBooks(query, maxResults = 5, apiKey = apiKey) 
                    } catch (_: Exception) { 
                        null 
                    }
                    
                    val items = response?.items ?: emptyList()
                    val match = items.firstOrNull { item ->
                        val tMatch = isTitleMatch(book.title, item.volumeInfo.title)
                        val aMatch = isAuthorMatch(book.author, item.volumeInfo.authors)
                        
                        // Relaxed matching for first result if title is an exact match
                        val isFirstResultExactTitle = (items.indexOf(item) == 0) && isTitleMatch(book.title, item.volumeInfo.title, exactOnly = true)
                        
                        if (tMatch && (aMatch || isFirstResultExactTitle)) {
                            if (!aMatch) {
                                Log.d("BookEnrichment", "Relaxed author match for exact title: '${book.title}' matched API result with authors: ${item.volumeInfo.authors}")
                            }
                            true
                        } else {
                            if (tMatch) {
                                Log.d("BookEnrichment", "Candidate rejected: Title match but author mismatch. Local: '${book.author}', API: ${item.volumeInfo.authors}")
                            }
                            false
                        }
                    }

                    if (match != null) {
                        foundThumbnail = match.volumeInfo.imageLinks?.thumbnail?.replace("http:", "https:")
                        foundDescription = match.volumeInfo.description
                        foundIsbn = match.volumeInfo.industryIdentifiers?.find { it.type == "ISBN_13" }?.identifier
                                   ?: match.volumeInfo.industryIdentifiers?.find { it.type == "ISBN_10" }?.identifier
                        source = "Google"
                        Log.d("BookEnrichment", "Google match found via '$query': '${match.volumeInfo.title}'")
                        break
                    }
                }

                // --- 2. Try Open Library Fallback ---
                if (foundThumbnail == null && foundDescription == null) {
                    Log.d("BookEnrichment", "Trying Open Library fallback for: '${book.title}'")
                    
                    if (!book.isbn.isNullOrBlank()) {
                        val bibKey = "ISBN:${book.isbn}"
                        val directOlResponse = try { olService.getBookByISBN(bibKey) } catch (_: Exception) { null }
                        if (!directOlResponse.isNullOrEmpty() && directOlResponse.containsKey(bibKey)) {
                            val olData = directOlResponse[bibKey]!!
                            foundThumbnail = olData.cover?.medium ?: olData.cover?.large
                            foundDescription = olData.subjects?.joinToString(", ") { it.name }
                            source = "Open Library (ISBN)"
                        }
                    }
                    
                    if (foundThumbnail == null && foundDescription == null) {
                        val olSearchResponse = try { olService.search("${book.title} ${book.author}") } catch (_: Exception) { null }
                        val olMatch = olSearchResponse?.docs?.firstOrNull { doc ->
                            isTitleMatch(book.title, doc.title) && 
                            (isAuthorMatch(book.author, doc.author_name) || isTitleMatch(book.title, doc.title, exactOnly = true))
                        }
                        if (olMatch != null) {
                            foundThumbnail = olMatch.cover_i?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
                            foundIsbn = olMatch.isbn?.firstOrNull()
                            source = "Open Library (Search)"
                        }
                    }
                }

                // --- 3. Apply Upgrades ---
                if (foundThumbnail != null || foundDescription != null || (foundIsbn != null && book.isbn.isNullOrBlank())) {
                    var shouldUpdate = false
                    var thumbnailToUse = book.thumbnail
                    var descriptionToUse = book.description
                    var isbnToUse = book.isbn
                    
                    // Upgrade ISBN if missing
                    if (book.isbn.isNullOrBlank() && !foundIsbn.isNullOrBlank()) {
                        isbnToUse = foundIsbn
                        shouldUpdate = true
                        Log.d("BookEnrichment", "Found missing ISBN: $isbnToUse")
                    }

                    // Upgrade thumbnail
                    if (!foundThumbnail.isNullOrBlank() && isThumbnailUpgrade(book.thumbnail, foundThumbnail)) {
                        Log.d("BookEnrichment", "Thumbnail upgrade: '${book.thumbnail}' -> '$foundThumbnail'")
                        thumbnailToUse = foundThumbnail
                        shouldUpdate = true
                    }
                    
                    // Upgrade description
                    if (!foundDescription.isNullOrBlank() && isDescriptionUpgrade(book.description, foundDescription)) {
                        Log.d("BookEnrichment", "Description upgrade (len: ${book.description?.length ?: 0} -> ${foundDescription.length})")
                        descriptionToUse = foundDescription
                        shouldUpdate = true
                    }
                    
                    if (shouldUpdate) {
                        repository.updateFullMetadata(book.id, isbnToUse, thumbnailToUse, descriptionToUse)
                        updatedCount++
                        Log.d("BookEnrichment", "Successfully enriched via $source: ${book.title}")
                    } else {
                        Log.d("BookEnrichment", "Found results via $source for '${book.title}' but no quality upgrade detected.")
                    }
                } else {
                    Log.d("BookEnrichment", "No enrichment data found for: ${book.title} in any service.")
                }
            } catch (e: HttpException) {
                val code = e.code()
                if (code == 429 || code >= 500) {
                    val reason = if (code == 429) "Rate limit hit (429)" else "Server error ($code)"
                    Log.w("BookEnrichment", "$reason for ${book.title}. Stopping and retrying later.")
                    return Result.retry()
                }
                Log.e("BookEnrichment", "HTTP error enriching book: ${book.title}", e)
            } catch (e: Exception) {
                Log.e("BookEnrichment", "Error enriching book: ${book.title}", e)
            }
            kotlinx.coroutines.delay(2000.milliseconds)
        }

        Log.d("BookEnrichment", "Background enrichment finished. Updated $updatedCount books.")
        return Result.success()
    }

    private fun isTitleMatch(localTitle: String, apiTitle: String, exactOnly: Boolean = false): Boolean {
        return BookEnrichmentMatcher.isTitleMatch(localTitle, apiTitle, exactOnly)
    }

    private fun isAuthorMatch(localAuthor: String, apiAuthors: List<String>?): Boolean {
        return BookEnrichmentMatcher.isAuthorMatch(localAuthor, apiAuthors)
    }

    private fun isThumbnailUpgrade(current: String?, new: String?): Boolean {
        return BookEnrichmentMatcher.isThumbnailUpgrade(current, new)
    }

    private fun isDescriptionUpgrade(current: String?, new: String?): Boolean {
        return BookEnrichmentMatcher.isDescriptionUpgrade(current, new)
    }
}
