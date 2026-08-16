package com.booklog.inventory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val isbn: String?,
    val thumbnail: String?,
    val description: String?,
    val isInWishlist: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
