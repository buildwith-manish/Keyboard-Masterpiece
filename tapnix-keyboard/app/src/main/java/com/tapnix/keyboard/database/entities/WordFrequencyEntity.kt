package com.tapnix.keyboard.database.entities

import androidx.room.*

@Entity(
    tableName = "word_frequency",
    indices = [Index(value = ["word", "language"], unique = true)],
)
data class WordFrequencyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "word")
    val word: String,

    @ColumnInfo(name = "frequency")
    val frequency: Int = 1,

    @ColumnInfo(name = "last_used", index = true)
    val lastUsed: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "language")
    val language: String = "en",
)
