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

class BookAdapter(private val onBookClick: (Book) -> Unit = {}) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {
    private val books = mutableListOf<Book>()

    class BookViewHolder(val binding: ItemBookBinding, val onBookClick: (Book) -> Unit) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book) {
            binding.title.text = book.title
            binding.author.text = book.author
            Glide.with(binding.root).load(book.thumbnail).into(binding.cover)
            binding.root.setOnClickListener {
                Log.d("BookLog", "Book clicked: ${book.title}")
                onBookClick(book)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookViewHolder(binding, onBookClick)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(books[position])
    }

    override fun getItemCount() = books.size

    fun addBooks(newBooks: List<Book>) {
        books.addAll(newBooks)
        notifyDataSetChanged()
    }

    fun clear() {
        books.clear()
        notifyDataSetChanged()
    }
}
