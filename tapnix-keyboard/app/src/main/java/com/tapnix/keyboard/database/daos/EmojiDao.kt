package com.tapnix.keyboard.database.daos

import androidx.room.*
import com.tapnix.keyboard.database.entities.EmojiEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmojiDao {

    @Query("SELECT * FROM emoji_history ORDER BY last_used DESC LIMIT 30")
    fun observeRecent(): Flow<List<EmojiEntity>>

    @Query("SELECT * FROM emoji_history WHERE is_favorite = 1 ORDER BY use_count DESC")
    fun observeFavorites(): Flow<List<EmojiEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EmojiEntity)

    @Query("""
        UPDATE emoji_history
        SET use_count = use_count + 1, last_used = :ts
        WHERE unicode = :unicode
    """)
    suspend fun incrementUse(unicode: String, ts: Long = System.currentTimeMillis())

    @Query("UPDATE emoji_history SET is_favorite = :fav WHERE unicode = :unicode")
    suspend fun setFavorite(unicode: String, fav: Boolean)

    @Query("SELECT * FROM emoji_history WHERE unicode = :unicode LIMIT 1")
    suspend fun getByUnicode(unicode: String): EmojiEntity?
}
