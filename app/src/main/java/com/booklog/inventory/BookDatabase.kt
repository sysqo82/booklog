package com.booklog.inventory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [BookEntity::class], version = 2)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: BookDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(books)")
                val existingColumns = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    existingColumns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                cursor.close()

                if (!existingColumns.contains("isbn")) {
                    db.execSQL("ALTER TABLE books ADD COLUMN isbn TEXT")
                }
                if (!existingColumns.contains("thumbnail")) {
                    db.execSQL("ALTER TABLE books ADD COLUMN thumbnail TEXT")
                }
                if (!existingColumns.contains("description")) {
                    db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
                }
                if (!existingColumns.contains("addedAt")) {
                    db.execSQL("ALTER TABLE books ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        fun getDatabase(context: Context): BookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    "book_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
