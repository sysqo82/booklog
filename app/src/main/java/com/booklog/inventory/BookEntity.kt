package com.booklog.inventory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val thumbnail: String?,
    val description: String?,
    val addedAt: Long = System.currentTimeMillis()
)
