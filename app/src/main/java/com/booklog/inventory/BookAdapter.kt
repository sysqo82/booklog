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
    val thumbnail: String?,
    val description: String?
) : Parcelable

class BookAdapter(
    private val onBookClick: (Book) -> Unit = {},
    private val onQuickAddClick: (Book) -> Unit = {}
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {
    private val books = mutableListOf<Book>()
    private var savedBookIds = setOf<String>()
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
        holder.bind(book, savedBookIds.contains(book.id), isSearchMode)
    }

    override fun getItemCount() = books.size

    fun addBooks(newBooks: List<Book>) {
        books.addAll(newBooks)
        notifyDataSetChanged()
    }

    fun setSavedBookIds(ids: Set<String>) {
        savedBookIds = ids
        notifyDataSetChanged()
    }

    fun clear() {
        books.clear()
        notifyDataSetChanged()
    }
}
