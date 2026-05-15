package com.tapnix.keyboard.database.daos

import androidx.room.*
import com.tapnix.keyboard.database.entities.BigramEntity

@Dao
interface BigramDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bigram: BigramEntity)

    @Query("""
        UPDATE bigrams
        SET frequency = frequency + 1, last_used = :now
        WHERE word1 = :word1 AND word2 = :word2 AND language = :language
    """)
    suspend fun incrementFrequency(
        word1: String,
        word2: String,
        language: String,
        now: Long = System.currentTimeMillis(),
    )

    /**
     * Get the top [limit] words most likely to follow [word1],
     * ordered by descending frequency.
     */
    @Query("""
        SELECT * FROM bigrams
        WHERE word1 = :word1 AND language = :language
        ORDER BY frequency DESC
        LIMIT :limit
    """)
    suspend fun getFollowOns(
        word1: String,
        language: String,
        limit: Int = 5,
    ): List<BigramEntity>

    /** Prune oldest low-frequency bigrams to keep the table lean. */
    @Query("""
        DELETE FROM bigrams
        WHERE id IN (
            SELECT id FROM bigrams
            WHERE frequency <= 1
            ORDER BY last_used ASC
            LIMIT :count
        )
    """)
    suspend fun pruneOldLowFrequency(count: Int)

    @Query("SELECT COUNT(*) FROM bigrams")
    suspend fun count(): Int

    @Query("DELETE FROM bigrams")
    suspend fun deleteAll()
}
