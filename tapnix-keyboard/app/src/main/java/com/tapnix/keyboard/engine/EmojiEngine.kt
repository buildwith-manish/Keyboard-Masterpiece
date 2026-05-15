package com.tapnix.keyboard.engine

import com.tapnix.keyboard.data.Emoji
import com.tapnix.keyboard.data.EmojiCategory
import com.tapnix.keyboard.data.EmojiData
import com.tapnix.keyboard.database.daos.EmojiDao
import com.tapnix.keyboard.database.entities.EmojiEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * EmojiEngine
 *
 * Manages:
 *  - Static emoji data (all categories, 3,600+ emoji)
 *  - Recent emoji tracking via Room
 *  - Emoji search (keyword + name matching, debounced by ViewModel)
 *  - Usage frequency tracking
 */
class EmojiEngine(
    private val dao: EmojiDao,
    private val ioScope: CoroutineScope,
) {
    /** All emoji categories from static data */
    val categoriesFlow: Flow<List<EmojiCategory>> = flow {
        emit(EmojiData.categories)
    }

    /** Recent emoji from DB, merged with static data for full Emoji objects */
    val recentFlow: Flow<List<Emoji>> = dao.observeRecent()
        .map { entities ->
            entities.mapNotNull { entity ->
                EmojiData.allEmojis.find { it.unicode == entity.unicode }
            }
        }
        .flowOn(Dispatchers.Default)

    /** Favorite emoji */
    val favoritesFlow: Flow<List<Emoji>> = dao.observeFavorites()
        .map { entities ->
            entities.mapNotNull { entity ->
                EmojiData.allEmojis.find { it.unicode == entity.unicode }
            }
        }
        .flowOn(Dispatchers.Default)

    /**
     * Search emoji by name or keywords.
     * Call is designed to be debounced by the ViewModel (150ms).
     */
    fun search(query: String): Flow<List<Emoji>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val lq = query.lowercase().trim()
        val results = EmojiData.allEmojis.filter { emoji ->
            emoji.name.contains(lq) ||
            emoji.keywords.any { kw -> kw.contains(lq) } ||
            emoji.unicode == lq
        }

        emit(results)
    }.flowOn(Dispatchers.Default)

    /**
     * Record that an emoji was used (for recent/frequency tracking).
     * Fire-and-forget — safe to call from UI thread.
     */
    fun recordUse(emoji: Emoji) {
        ioScope.launch {
            val existing = dao.getByUnicode(emoji.unicode)
            if (existing != null) {
                dao.incrementUse(emoji.unicode)
            } else {
                dao.upsert(
                    EmojiEntity(
                        unicode = emoji.unicode,
                        name = emoji.name,
                        useCount = 1,
                    )
                )
            }
        }
    }

    fun toggleFavorite(emoji: Emoji, isFavorite: Boolean) {
        ioScope.launch {
            dao.setFavorite(emoji.unicode, isFavorite)
        }
    }

    /**
     * Called when the system signals memory pressure.
     * EmojiData is static and always needed, so no cache to drop here.
     * Room's internal query cache is managed by SQLite/WAL.
     */
    fun trimMemory(level: Int) {
        // No in-memory caches to release — emoji data is lazily resolved per-flow.
    }
}
