package com.tapnix.keyboard.database.daos

import androidx.room.*
import com.tapnix.keyboard.database.entities.ClipboardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    @Query("SELECT * FROM clipboard ORDER BY is_pinned DESC, created_at DESC LIMIT 50")
    fun observeAll(): Flow<List<ClipboardEntity>>

    @Query("SELECT * FROM clipboard WHERE is_pinned = 1 ORDER BY created_at DESC")
    fun observePinned(): Flow<List<ClipboardEntity>>

    @Query("SELECT * FROM clipboard WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ClipboardEntity?

    @Query("SELECT * FROM clipboard ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(): ClipboardEntity?

    @Query("SELECT COUNT(*) FROM clipboard WHERE is_pinned = 0")
    suspend fun countUnpinned(): Int

    @Query("""
        SELECT * FROM clipboard
        WHERE preview LIKE :query OR label LIKE :query
        ORDER BY is_pinned DESC, created_at DESC
        LIMIT 50
    """)
    fun search(query: String): Flow<List<ClipboardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ClipboardEntity): Long

    @Query("UPDATE clipboard SET is_pinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: Long, pinned: Boolean)

    @Query("UPDATE clipboard SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: Long, label: String?)

    @Query("DELETE FROM clipboard WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clipboard WHERE is_pinned = 0")
    suspend fun deleteAllUnpinned()

    @Query("DELETE FROM clipboard")
    suspend fun deleteAll()

    @Query("""
        DELETE FROM clipboard
        WHERE is_pinned = 0
        AND id IN (
            SELECT id FROM clipboard
            WHERE is_pinned = 0
            ORDER BY created_at ASC
            LIMIT :count
        )
    """)
    suspend fun deleteOldestUnpinned(count: Int)

    @Query("SELECT COUNT(*) FROM clipboard")
    suspend fun count(): Int
}
