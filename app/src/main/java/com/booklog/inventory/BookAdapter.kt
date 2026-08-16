package com.booklog.inventory

import android.content.Context
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.booklog.inventory.databinding.ItemBookBinding
import kotlinx.parcelize.Parcelize

@Parcelize
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val isbn: String?,
    val thumbnail: String?,
    val description: String?,
    val isInWishlist: Boolean = false
) : Parcelable

class BookAdapter(
    private val onBookClick: (Book) -> Unit = {},
    private val onQuickAddClick: (Book) -> Unit = {},
    private val onQuickWishlistClick: (Book) -> Unit = {}
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {
    private val books = mutableListOf<Book>()
    private var savedBookIds = setOf<String>()
    private var savedIsbns = setOf<String>()
    private var savedTitleAuthors = setOf<String>()
    private var wishlistBookIds = setOf<String>()
    private var wishlistIsbns = setOf<String>()
    private var wishlistTitleAuthors = setOf<String>()
    var isSearchMode = false

    class BookViewHolder(
        val binding: ItemBookBinding,
        val onBookClick: (Book) -> Unit,
        val onQuickAddClick: (Book) -> Unit,
        val onQuickWishlistClick: (Book) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book, isSaved: Boolean, isInWishlist: Boolean, isSearchMode: Boolean) {
            binding.title.text = book.title
            binding.author.text = book.author
            Glide.with(binding.root).load(book.thumbnail).into(binding.cover)
            
            binding.inCollectionRibbon.visibility = if (isSaved && isSearchMode) android.view.View.VISIBLE else android.view.View.GONE
            binding.inWishlistRibbon.visibility = if (isInWishlist && isSearchMode) android.view.View.VISIBLE else android.view.View.GONE
            
            if (!isSearchMode) {
                binding.inCollectionRibbon.visibility = android.view.View.GONE
                binding.inWishlistRibbon.visibility = if (book.isInWishlist) android.view.View.VISIBLE else android.view.View.GONE
            }

            // Quick add to collection visible if not already in collection
            binding.quickAddBtn.visibility = if (isSearchMode && !isSaved) android.view.View.VISIBLE else android.view.View.GONE
            // Quick add to wishlist visible if not already in wishlist AND not in collection
            binding.quickWishlistBtn.visibility = if (isSearchMode && !isSaved && !isInWishlist) android.view.View.VISIBLE else android.view.View.GONE
            
            binding.quickAddBtn.setOnClickListener {
                onQuickAddClick(book)
            }

            binding.quickWishlistBtn.setOnClickListener {
                onQuickWishlistClick(book)
            }
            
            binding.root.setOnClickListener {
                Log.d("BookLog", "Book clicked: ${book.title}")
                onBookClick(book)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding, onBookClick, onQuickAddClick, onQuickWishlistClick)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        val normalizedIsbn = book.isbn?.replace(Regex("[^0-9X]"), "")
        
        val isSaved = savedBookIds.contains(book.id) || 
                      (normalizedIsbn != null && savedIsbns.contains(normalizedIsbn)) ||
                      savedTitleAuthors.contains(normalizeTitleAuthor(book.title, book.author))
        
        val isInWishlist = wishlistBookIds.contains(book.id) || 
                          (normalizedIsbn != null && wishlistIsbns.contains(normalizedIsbn)) ||
                          wishlistTitleAuthors.contains(normalizeTitleAuthor(book.title, book.author))
                          
        holder.bind(book, isSaved, isInWishlist, isSearchMode)
    }

    private fun normalizeTitleAuthor(title: String, author: String): String {
        val cleanTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanAuthor = author.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "$cleanTitle|$cleanAuthor"
    }

    override fun getItemCount() = books.size

    fun addBooks(newBooks: List<Book>) {
        books.addAll(newBooks)
        notifyDataSetChanged()
    }

    fun setSavedBooks(
        ids: Set<String>, isbns: Set<String>, titleAuthors: Set<String>,
        wIds: Set<String> = emptySet(), wIsbns: Set<String> = emptySet(), wTitleAuthors: Set<String> = emptySet()
    ) {
        savedBookIds = ids
        savedIsbns = isbns
        savedTitleAuthors = titleAuthors
        wishlistBookIds = wIds
        wishlistIsbns = wIsbns
        wishlistTitleAuthors = wTitleAuthors
        notifyDataSetChanged()
    }

    fun clear() {
        books.clear()
        notifyDataSetChanged()
    }
}
