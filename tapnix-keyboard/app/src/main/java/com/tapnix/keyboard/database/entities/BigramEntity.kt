package com.tapnix.keyboard.database.entities

import androidx.room.*

/**
 * BigramEntity
 *
 * Stores bigram (word-pair) frequency for next-word prediction.
 * "word1 word2" appears [frequency] times in the user's typing history.
 *
 * Indexed on (word1, language) for fast lookups of all possible follow-on words.
 */
@Entity(
    tableName = "bigrams",
    indices = [
        Index(value = ["word1", "language"]),
        Index(value = ["word1", "word2", "language"], unique = true),
    ],
)
data class BigramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "word1")
    val word1: String,

    @ColumnInfo(name = "word2")
    val word2: String,

    @ColumnInfo(name = "frequency")
    val frequency: Int = 1,

    @ColumnInfo(name = "last_used", index = true)
    val lastUsed: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "language")
    val language: String = "en",
)
