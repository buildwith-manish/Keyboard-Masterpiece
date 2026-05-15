package com.tapnix.keyboard.database.daos

import androidx.room.*
import com.tapnix.keyboard.database.entities.WordFrequencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("""
        SELECT * FROM word_frequency
        WHERE word LIKE :prefix || '%' AND language = :lang
        ORDER BY frequency DESC
        LIMIT 5
    """)
    suspend fun getSuggestions(prefix: String, lang: String = "en"): List<WordFrequencyEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: WordFrequencyEntity)

    @Query("""
        UPDATE word_frequency
        SET frequency = frequency + 1, last_used = :ts
        WHERE word = :word AND language = :lang
    """)
    suspend fun incrementFrequency(
        word: String,
        lang: String = "en",
        ts: Long = System.currentTimeMillis()
    )

    @Query("SELECT COUNT(*) FROM word_frequency")
    fun observeCount(): Flow<Int>
}
