package com.booklog.inventory

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AuthorAdapter(
    private var authors: List<String>,
    private val onAuthorClick: (String?) -> Unit
) : RecyclerView.Adapter<AuthorAdapter.ViewHolder>() {

    private var filteredAuthors = authors

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_author, parent, false) as TextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == 0) {
            holder.textView.text = "All Authors"
            holder.textView.setTypeface(null, android.graphics.Typeface.BOLD)
            holder.textView.setTextColor(android.graphics.Color.BLUE)
            holder.textView.setOnClickListener { onAuthorClick(null) }
        } else {
            val author = filteredAuthors[position - 1]
            holder.textView.text = author
            holder.textView.setTypeface(null, android.graphics.Typeface.NORMAL)
            holder.textView.setTextColor(android.graphics.Color.BLACK)
            holder.textView.setOnClickListener { onAuthorClick(author) }
        }
    }

    override fun getItemCount() = filteredAuthors.size + 1

    fun filter(query: String) {
        filteredAuthors = if (query.isEmpty()) {
            authors
        } else {
            authors.filter { it.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }
}
