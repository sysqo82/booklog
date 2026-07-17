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
    val description: String?
) : Parcelable

class BookAdapter(
    private val onBookClick: (Book) -> Unit = {},
    private val onQuickAddClick: (Book) -> Unit = {}
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {
    private val books = mutableListOf<Book>()
    private var savedBookIds = setOf<String>()
    private var savedIsbns = setOf<String>()
    private var savedTitleAuthors = setOf<String>()
    var isSearchMode = false

    class BookViewHolder(
        val binding: ItemBookBinding,
        val onBookClick: (Book) -> Unit,
        val onQuickAddClick: (Book) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book, isSaved: Boolean, isSearchMode: Boolean) {
            binding.title.text = book.title
            binding.author.text = book.author
            Glide.with(binding.root).load(book.thumbnail).into(binding.cover)
            
            binding.inCollectionRibbon.visibility = if (isSaved && isSearchMode) android.view.View.VISIBLE else android.view.View.GONE
            binding.quickAddBtn.visibility = if (isSaved) android.view.View.GONE else android.view.View.VISIBLE
            
            binding.quickAddBtn.setOnClickListener {
                onQuickAddClick(book)
            }
            
            binding.root.setOnClickListener {
                Log.d("BookLog", "Book clicked: ${book.title}")
                onBookClick(book)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding, onBookClick, onQuickAddClick)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        val normalizedIsbn = book.isbn?.replace(Regex("[^0-9X]"), "")
        
        val isSaved = savedBookIds.contains(book.id) || 
                      (normalizedIsbn != null && savedIsbns.contains(normalizedIsbn)) ||
                      savedTitleAuthors.contains(normalizeTitleAuthor(book.title, book.author))
        holder.bind(book, isSaved, isSearchMode)
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

    fun setSavedBooks(ids: Set<String>, isbns: Set<String>, titleAuthors: Set<String>) {
        savedBookIds = ids
        savedIsbns = isbns
        savedTitleAuthors = titleAuthors
        notifyDataSetChanged()
    }

    fun clear() {
        books.clear()
        notifyDataSetChanged()
    }
}
