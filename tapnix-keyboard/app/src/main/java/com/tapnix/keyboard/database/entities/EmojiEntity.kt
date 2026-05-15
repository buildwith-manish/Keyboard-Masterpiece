package com.tapnix.keyboard.database.entities

import androidx.room.*

@Entity(tableName = "emoji_history")
data class EmojiEntity(
    @PrimaryKey
    @ColumnInfo(name = "unicode")
    val unicode: String,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "use_count")
    val useCount: Int = 1,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "last_used", index = true)
    val lastUsed: Long = System.currentTimeMillis(),
)
