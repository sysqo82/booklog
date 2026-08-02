package com.booklog.inventory

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.LinkedHashSet

private const val TAG = "BookLog"

class MainActivity : AppCompatActivity() {
    private lateinit var retrofit: Retrofit
    private lateinit var apiService: BookApiService
    private lateinit var olService: OpenLibraryService
    private lateinit var adapter: BookAdapter
    private lateinit var repository: BookRepository
    private var currentPage = 0
    private var isLoading = false
    private var hasMore = true
    private var isViewingCollection = false
    private var searchJob: kotlinx.coroutines.Job? = null

    private fun cancelOngoingOperations() {
        searchJob?.cancel()
        searchJob = null
        isLoading = false
    }

    private val scannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val isbn = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_RESULT_ISBN)
            if (isbn != null) {
                Log.d(TAG, "ISBN received from scanner: $isbn")
                searchByISBN(isbn)
            }
        }
    }

    private fun launchScanner() {
        val intent = Intent(this, BarcodeScannerActivity::class.java)
        scannerLauncher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.statusBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        Log.d(TAG, "onCreate called, layout inflated")

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isViewingCollection) {
                    Log.d(TAG, "Back pressed: in search mode, returning to collection")
                    cancelOngoingOperations()
                    findViewById<SearchView>(R.id.search)?.setQuery("", false)
                    findViewById<SearchView>(R.id.search)?.clearFocus()
                    loadCollection()
                } else {
                    Log.d(TAG, "Back pressed: in collection mode, exiting")
                    finish()
                }
            }
        })

        try {
            val logging = HttpLoggingInterceptor { message -> Log.d("OkHttp", message) }
            logging.level = HttpLoggingInterceptor.Level.BODY
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/books/v1/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            Log.d(TAG, "Retrofit initialized")

            repository = BookRepository(this)
            Log.d(TAG, "Repository initialized")

            apiService = retrofit.create(BookApiService::class.java)

            val olRetrofit = Retrofit.Builder()
                .baseUrl("https://openlibrary.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            olService = olRetrofit.create(OpenLibraryService::class.java)

            scheduleBookEnrichment()

            adapter = BookAdapter(
                onBookClick = { book ->
                    Log.d(TAG, "Book selected: ${book.title}")
                    val sheet = BookDetailBottomSheet.newInstance(book)
                    sheet.onCollectionChanged = {
                        isViewingCollection = true
                        findViewById<SearchView>(R.id.search)?.let {
                            it.setQuery("", false)
                            it.clearFocus()
                        }
                        updateSavedIdsAndRefresh()
                        loadCollection()
                    }
                    sheet.show(supportFragmentManager, "BookDetail")
                },
                onQuickAddClick = { book ->
                    lifecycleScope.launch {
                        try {
                            repository.addBook(book)
                            android.widget.Toast.makeText(this@MainActivity, "Added ${book.title}", android.widget.Toast.LENGTH_SHORT).show()
                            updateSavedIdsAndRefresh()
                            if (isViewingCollection) {
                                loadCollection()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error adding book", e)
                        }
                    }
                }
            )
            Log.d(TAG, "API service and adapter created")

            val recyclerView = findViewById<RecyclerView>(R.id.recycler)
            if (recyclerView == null) {
                Log.e(TAG, "ERROR: RecyclerView not found!")
                return
            }
            Log.d(TAG, "RecyclerView found")
            recyclerView.layoutManager = GridLayoutManager(this, 2)
            recyclerView.adapter = adapter

            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val layoutManager = recyclerView.layoutManager as GridLayoutManager
                    if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 5 && hasMore && !isLoading) {
                        loadMore()
                    }
                }
            })

            val searchView = findViewById<SearchView>(R.id.search)
            if (searchView == null) {
                Log.e(TAG, "ERROR: SearchView not found!")
                return
            }
            Log.d(TAG, "SearchView found, attaching listener")

            // Find the underlying EditText to better control its behavior
            val searchEditTextId = searchView.context.resources.getIdentifier("android:id/search_src_text", null, null)
            val searchEditText = searchView.findViewById<android.widget.EditText>(searchEditTextId)

            searchView.setOnClickListener {
                searchView.isIconified = false
                searchView.requestFocus()
            }
            searchView.setOnCloseListener {
                Log.d(TAG, "SearchView closed via X button")
                cancelOngoingOperations()
                isViewingCollection = true
                searchView.setQuery("", false)
                searchView.clearFocus()
                searchEditText?.clearFocus()
                
                // Force hide keyboard
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchView.windowToken, 0)
                
                loadCollection()
                true
            }
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    Log.d(TAG, "Query submitted: $query")
                    if (!query.isNullOrBlank()) {
                        search(query)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    if (newText.isNullOrEmpty()) {
                        // When the 'X' button is clicked or text is cleared manually
                        if (!isViewingCollection) {
                            isViewingCollection = true
                            searchView.clearFocus()
                            loadCollection()
                        }
                    }
                    return false
                }
            })

            val fab = findViewById<FloatingActionButton>(R.id.fab)
            if (fab == null) {
                Log.e(TAG, "ERROR: FAB not found!")
                return
            }
            Log.d(TAG, "FAB found, attaching listener")
            Log.d(TAG, "FAB enabled: ${fab.isEnabled}, clickable: ${fab.isClickable}")
            fab.setOnClickListener { v ->
                Log.d(TAG, "FAB clicked, launching barcode scanner")
                launchScanner()
            }
            Log.d(TAG, "FAB listener attached")

            val scanBtn = findViewById<ImageView>(R.id.scan_btn)
            if (scanBtn == null) {
                Log.e(TAG, "ERROR: Camera button not found!")
                return
            }
            Log.d(TAG, "Camera button found, attaching listener")
            scanBtn.setOnClickListener { v ->
                Log.d(TAG, "Camera button clicked, launching barcode scanner")
                launchScanner()
            }
            Log.d(TAG, "Camera listener attached")

            val filterBtn = findViewById<ImageView>(R.id.filter_btn)
            filterBtn?.setOnClickListener {
                showAuthorFilterDialog()
            }

            // Open on collection by default
            isViewingCollection = true
            updateSavedIdsAndRefresh()
            searchView.clearFocus()
            loadCollection()

            Log.d(TAG, "All views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception in onCreate", e)
        }
    }

    private fun updateSavedIdsAndRefresh() {
        lifecycleScope.launch {
            val myBooks = repository.getMyBooks()
            val ids = myBooks.map { it.id }.toSet()
            val isbns = myBooks.mapNotNull { it.isbn?.replace(Regex("[^0-9X]"), "") }.toSet()
            val titleAuthors = myBooks.map { 
                val cleanTitle = it.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                val cleanAuthor = it.author.lowercase().replace(Regex("[^a-z0-9]"), "")
                "$cleanTitle|$cleanAuthor"
            }.toSet()
            adapter.setSavedBooks(ids, isbns, titleAuthors)
            findViewById<TextView>(R.id.book_counter)?.text = "${myBooks.size} books"
        }
    }

    private fun mapBookItemToBook(item: BookItem): Book {
        val isbn = item.volumeInfo.industryIdentifiers?.find { it.type == "ISBN_13" }?.identifier
            ?: item.volumeInfo.industryIdentifiers?.find { it.type == "ISBN_10" }?.identifier
        return Book(
            item.id,
            item.volumeInfo.title,
            item.volumeInfo.authors?.firstOrNull() ?: "Unknown",
            isbn,
            item.volumeInfo.imageLinks?.thumbnail?.replace("http:", "https:"),
            item.volumeInfo.description
        )
    }

    private fun mapOLDocToBook(doc: OLDoc): Book {
        val coverUrl = doc.cover_i?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
        return Book(
            id = doc.key.replace("/works/", ""),
            title = doc.title,
            author = doc.author_name?.firstOrNull() ?: "Unknown",
            isbn = doc.isbn?.firstOrNull(),
            thumbnail = coverUrl,
            description = null
        )
    }

    private fun mapOLBookDataToBook(key: String, data: OLBookData): Book {
        return Book(
            id = key.replace("ISBN:", ""),
            title = data.title,
            author = data.authors?.firstOrNull()?.name ?: "Unknown",
            isbn = data.isbn_13?.firstOrNull() ?: data.isbn_10?.firstOrNull(),
            thumbnail = data.cover?.medium ?: data.cover?.large,
            description = data.subjects?.joinToString(", ") { it.name }
        )
    }

    private fun loadCollection(filterAuthor: String? = null) {
        cancelOngoingOperations()
        isViewingCollection = true
        adapter.isSearchMode = false
        isLoading = true
        findViewById<TextView>(R.id.book_counter)?.visibility = android.view.View.VISIBLE
        searchJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading user's collection${if (filterAuthor != null) " filtered by $filterAuthor" else ""}")
                var myBooks = repository.getMyBooks()
                
                if (filterAuthor != null) {
                    myBooks = myBooks.filter { it.author.equals(filterAuthor, ignoreCase = true) }
                }

                Log.d(TAG, "Fetched ${myBooks.size} books from collection")
                findViewById<TextView>(R.id.book_counter)?.text = "${myBooks.size} books"
                adapter.clear()
                
                // Sort by author's last name
                val sortedBooks = myBooks.map {
                    Book(it.id, it.title, it.author, it.isbn, it.thumbnail, it.description)
                }.sortedBy { book ->
                    val names = book.author.split(" ").filter { it.isNotBlank() }
                    if (names.isNotEmpty()) names.last().lowercase() else ""
                }
                
                adapter.addBooks(sortedBooks)
                hasMore = false
                
                // Show side index for collection
                val sideIndex = findViewById<SideIndexView>(R.id.side_index)
                
                // Only show letters that exist
                val existingLetters = sortedBooks.map { book ->
                    val names = book.author.split(" ").filter { it.isNotBlank() }
                    val lastName = if (names.isNotEmpty()) names.last() else ""
                    if (lastName.isNotEmpty() && lastName[0].isLetter()) {
                        lastName[0].uppercaseChar()
                    } else {
                        '#'
                    }
                }.distinct().sorted()
                
                sideIndex?.setLetters(existingLetters)
                sideIndex?.visibility = if (existingLetters.isNotEmpty() && filterAuthor == null) android.view.View.VISIBLE else android.view.View.GONE

                sideIndex?.onLetterSelected = { letter ->
                    val position = sortedBooks.indexOfFirst { book ->
                        val names = book.author.split(" ").filter { it.isNotBlank() }
                        val lastName = if (names.isNotEmpty()) names.last() else ""
                        if (letter == '#') {
                            lastName.isNotEmpty() && !lastName[0].isLetter()
                        } else {
                            lastName.startsWith(letter, ignoreCase = true)
                        }
                    }
                    if (position != -1) {
                        (findViewById<RecyclerView>(R.id.recycler).layoutManager as GridLayoutManager)
                            .scrollToPositionWithOffset(position, 0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading collection", e)
                e.printStackTrace()
            } finally {
                if (isActive) {
                    isLoading = false
                }
            }
        }
    }

    private suspend fun <T> retryIO(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 5000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: HttpException) {
                if (e.code() == 503 || e.code() == 429) {
                    val errorType = if (e.code() == 503) "503 Service Unavailable" else "429 Too Many Requests"
                    Log.w(TAG, "$errorType (attempt ${attempt + 1}), retrying in ${currentDelay}ms...")
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                } else {
                    throw e
                }
            } catch (e: java.io.IOException) {
                // Retry on network failures (timeout, connection lost)
                Log.w(TAG, "Network error (attempt ${attempt + 1}), retrying in ${currentDelay}ms: ${e.message}")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
        }
        return block() // last attempt
    }

    private fun getCountryCode(): String? {
        return try {
            val locale = resources.configuration.locales[0]
            val country = locale.country
            if (country.isNullOrEmpty()) null else country
        } catch (_: Exception) {
            null
        }
    }

    private fun search(query: String) {
        Log.d(TAG, "search() called with query: $query")
        cancelOngoingOperations()
        isViewingCollection = false
        adapter.isSearchMode = true
        findViewById<SideIndexView>(R.id.side_index)?.visibility = android.view.View.GONE
        adapter.clear()
        updateSavedIdsAndRefresh()
        currentPage = 0
        isLoading = true

        searchJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "Fetching books for query: $query")
                val apiKey = if (BuildConfig.GOOGLE_BOOKS_API_KEY.isNotEmpty()) BuildConfig.GOOGLE_BOOKS_API_KEY else null
                val country = getCountryCode()
                
                val response = try {
                    retryIO {
                        apiService.searchBooks(query, 0, 20, apiKey, country)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Google Books search failed completely, trying Open Library fallback...", e)
                    null
                }

                if (response == null || response.items.isNullOrEmpty()) {
                    Log.d(TAG, "Google Books search returned 0 results or failed, trying Open Library...")
                    val olResponse = olService.search(query)
                    val olBooks = olResponse.docs?.map { mapOLDocToBook(it) } ?: emptyList()
                    
                    if (olBooks.isNotEmpty()) {
                        Log.d(TAG, "Found ${olBooks.size} books on Open Library")
                        findViewById<TextView>(R.id.book_counter)?.visibility = android.view.View.GONE
                        adapter.addBooks(olBooks)
                        hasMore = olBooks.size >= 20
                        currentPage = 20
                    } else {
                        Log.d(TAG, "No books found on Open Library either")
                        if (response == null) {
                             android.widget.Toast.makeText(this@MainActivity, "Search failed. Please check connection.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    isLoading = false
                    return@launch
                }

                Log.d(TAG, "API response received, totalItems: ${response.totalItems}")
                val books = response.items?.map { mapBookItemToBook(it) } ?: emptyList()
                Log.d(TAG, "Mapped ${books.size} books")
                findViewById<TextView>(R.id.book_counter)?.visibility = android.view.View.GONE
                adapter.addBooks(books)
                hasMore = books.size < response.totalItems
                currentPage = 20
                Log.d(TAG, "Books added to adapter")
                
                // Hide keyboard after results are loaded
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                findViewById<SearchView>(R.id.search)?.let {
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in search()", e)
                e.printStackTrace()
            } finally {
                if (isActive) {
                    isLoading = false
                }
            }
        }
    }

    private fun loadMore() {
        if (isViewingCollection) return // Don't load more search results if we are in collection view
        isLoading = true
        searchJob = lifecycleScope.launch {
            try {
                val query = findViewById<SearchView>(R.id.search).query.toString()
                val apiKey = if (BuildConfig.GOOGLE_BOOKS_API_KEY.isNotEmpty()) BuildConfig.GOOGLE_BOOKS_API_KEY else null
                val country = getCountryCode()
                
                val response = retryIO {
                    apiService.searchBooks(query, currentPage, 20, apiKey, country)
                }
                val books = response.items?.map { mapBookItemToBook(it) } ?: emptyList()
                adapter.addBooks(books)
                hasMore = (currentPage + books.size) < response.totalItems
                currentPage += 20
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (isActive) {
                    isLoading = false
                }
            }
        }
    }

    private fun searchByISBN(isbn: String) {
        val normalizedIsbn = isbn.replace(Regex("[^0-9X]"), "")
        cancelOngoingOperations()
        isViewingCollection = false
        adapter.isSearchMode = true
        findViewById<SideIndexView>(R.id.side_index)?.visibility = android.view.View.GONE
        adapter.clear()
        updateSavedIdsAndRefresh()
        currentPage = 0
        isLoading = true

        searchJob = lifecycleScope.launch {
            try {
                Log.d(TAG, "Searching by ISBN: $normalizedIsbn")
                val apiKey = if (BuildConfig.GOOGLE_BOOKS_API_KEY.isNotEmpty()) BuildConfig.GOOGLE_BOOKS_API_KEY else null
                val country = getCountryCode()

                val queryCandidates = listOf("isbn:$normalizedIsbn", normalizedIsbn)
                val countryCandidates = LinkedHashSet<String?>().apply {
                    add(country)
                    add(null)
                }

                var response: BookResponse? = null
                var useFallback = false

                try {
                    var shouldStop = false
                    for (query in queryCandidates) {
                        for (countryCandidate in countryCandidates) {
                            Log.d(TAG, "Trying ISBN lookup query='$query', country='${countryCandidate ?: "none"}'")
                            val candidateResponse = try {
                                retryIO {
                                    apiService.searchByISBN(query, apiKey, countryCandidate)
                                }
                            } catch (e: HttpException) {
                                Log.w(TAG, "Candidate ISBN lookup failed with HTTP ${e.code()}")
                                if (e.code() == 503 || e.code() == 429) {
                                    shouldStop = true
                                    useFallback = true
                                    break
                                }
                                continue
                            } catch (e: Exception) {
                                Log.w(TAG, "Candidate ISBN lookup failed with error", e)
                                shouldStop = true
                                useFallback = true
                                break
                            }
                            val candidateCount = candidateResponse.items?.size ?: 0
                            Log.d(TAG, "Candidate response received: $candidateCount items")
                            if (!candidateResponse.items.isNullOrEmpty()) {
                                response = candidateResponse
                                shouldStop = true
                                break
                            }
                            if (response == null) {
                                response = candidateResponse
                            }
                        }
                        if (shouldStop) break
                    }
                } catch (e: Exception) {
                    useFallback = true
                }

                if (useFallback || (response?.items.isNullOrEmpty())) {
                    Log.w(TAG, "Google ISBN search returned 0 results or failed, trying Open Library fallback...")
                    
                    // Try direct ISBN lookup first
                    val bibKey = "ISBN:$normalizedIsbn"
                    val directOlResponse = try {
                        olService.getBookByISBN(bibKey)
                    } catch (e: Exception) {
                        Log.e(TAG, "Direct OL lookup failed", e)
                        null
                    }

                    if (!directOlResponse.isNullOrEmpty() && directOlResponse.containsKey(bibKey)) {
                        val book = mapOLBookDataToBook(bibKey, directOlResponse[bibKey]!!)
                        Log.d(TAG, "Found book via direct OL ISBN lookup: ${book.title}")
                        adapter.addBooks(listOf(book))
                        findViewById<TextView>(R.id.book_counter)?.visibility = android.view.View.GONE
                        isLoading = false
                        return@launch
                    }

                    val olResponse = try {
                        olService.search("isbn:$normalizedIsbn")
                    } catch (e: Exception) {
                        Log.e(TAG, "General OL search failed", e)
                        null
                    }

                    val olBooks = olResponse?.docs?.map { mapOLDocToBook(it) } ?: emptyList()
                    if (olBooks.isNotEmpty()) {
                        Log.d(TAG, "Found ${olBooks.size} books via general OL search")
                        adapter.addBooks(olBooks)
                        findViewById<TextView>(R.id.book_counter)?.visibility = android.view.View.GONE
                        isLoading = false
                        return@launch
                    }
                    
                    Log.d(TAG, "No results found for ISBN on either service")
                    android.widget.Toast.makeText(this@MainActivity, "Book not found", android.widget.Toast.LENGTH_LONG).show()
                    loadCollection()
                } else {
                    val books = response?.items?.map { mapBookItemToBook(it) } ?: emptyList()
                    Log.d(TAG, "Mapped ${books.size} books from search results")
                    findViewById<TextView>(R.id.book_counter)?.visibility = android.view.View.GONE
                    adapter.addBooks(books)
                }
                
                // Hide keyboard after ISBN search
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                findViewById<SearchView>(R.id.search)?.let {
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                }

                hasMore = false
            } catch (e: HttpException) {
                Log.e(TAG, "HTTP error in searchByISBN()", e)
                val message = if (e.code() == 429) {
                    "Google Books API quota reached. Add an API key in local.properties to restore lookups."
                } else {
                    "Error searching for book"
                }
                android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                loadCollection()
            } catch (e: Exception) {
                Log.e(TAG, "Error in searchByISBN()", e)
                android.widget.Toast.makeText(this@MainActivity, "Error searching for book", android.widget.Toast.LENGTH_SHORT).show()
                loadCollection()
            } finally {
                if (isActive) {
                    isLoading = false
                }
            }
        }
    }

    private fun scheduleBookEnrichment() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val enrichmentRequest = PeriodicWorkRequestBuilder<BookEnrichmentWorker>(12, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "BookEnrichment",
            ExistingPeriodicWorkPolicy.REPLACE,
            enrichmentRequest
        )
    }

    private fun showAuthorFilterDialog() {
        lifecycleScope.launch {
            val allBooks = repository.getMyBooks()
            val authors = allBooks.map { it.author }.distinct().sorted()
            
            val dialogView = layoutInflater.inflate(R.layout.dialog_author_filter, null)
            val searchEdit = dialogView.findViewById<android.widget.EditText>(R.id.author_search)
            val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.author_list)
            
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setView(dialogView)
                .create()

            val authorAdapter = AuthorAdapter(authors) { selectedAuthor ->
                isViewingCollection = true
                val searchView = findViewById<android.widget.SearchView>(R.id.search)
                if (selectedAuthor == null) {
                    searchView?.setQuery("", false)
                    loadCollection(null)
                } else {
                    searchView?.setQuery("", false)
                    loadCollection(selectedAuthor)
                }
                searchView?.clearFocus()
                dialog.dismiss()
            }
            
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
            recyclerView.adapter = authorAdapter
            
            searchEdit.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    authorAdapter.filter(s.toString())
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            
            dialog.show()
        }
    }
}
