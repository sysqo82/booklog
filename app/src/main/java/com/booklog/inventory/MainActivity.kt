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
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val TAG = "BookLog"

class MainActivity : AppCompatActivity() {
    private lateinit var retrofit: Retrofit
    private lateinit var apiService: BookApiService
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
                    cancelOngoingOperations()
                    isViewingCollection = true
                    findViewById<SearchView>(R.id.search)?.clearFocus()
                    loadCollection()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        try {
            val logging = HttpLoggingInterceptor { message -> Log.d("OkHttp", message) }
            logging.level = HttpLoggingInterceptor.Level.BODY
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
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

    private fun loadCollection(filterAuthor: String? = null) {
        cancelOngoingOperations()
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
        times: Int = 5,
        initialDelay: Long = 2000,
        maxDelay: Long = 10000,
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
            } catch (e: Exception) {
                // Also retry on general network failures that might be transient
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
                
                val response = retryIO {
                    apiService.searchBooks(query, 0, 20, apiKey, country)
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
                
                var response = retryIO {
                    apiService.searchByISBN("isbn:$normalizedIsbn", apiKey, country)
                }
                
                Log.d(TAG, "ISBN search response received: ${response.items?.size ?: 0} items")
                
                if (response.items.isNullOrEmpty()) {
                    Log.d(TAG, "ISBN-specific search returned no results, trying general search for ISBN: $normalizedIsbn")
                    response = retryIO {
                        apiService.searchByISBN(normalizedIsbn, apiKey, country)
                    }
                    Log.d(TAG, "General search response received: ${response.items?.size ?: 0} items")
                }

                val books = response.items?.map { mapBookItemToBook(it) } ?: emptyList()
                Log.d(TAG, "Mapped ${books.size} books from search results")
                if (books.isEmpty()) {
                    android.widget.Toast.makeText(this@MainActivity, "Book not found for ISBN: $isbn", android.widget.Toast.LENGTH_LONG).show()
                    loadCollection()
                } else {
                    findViewById<TextView>(R.id.book_counter)?.visibility = android.view.View.GONE
                    adapter.addBooks(books)
                }
                
                // Hide keyboard after ISBN search
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                findViewById<SearchView>(R.id.search)?.let {
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                }

                hasMore = false
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
