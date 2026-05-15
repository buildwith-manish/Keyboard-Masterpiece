package com.tapnix.keyboard.engine

import com.tapnix.keyboard.data.Suggestion
import com.tapnix.keyboard.database.daos.WordDao
import com.tapnix.keyboard.database.entities.WordFrequencyEntity
import kotlinx.coroutines.*

/**
 * SuggestionEngine
 *
 * Generates next-word suggestions from a frequency database.
 * Falls back to built-in common English words for cold-start.
 *
 * Design:
 *  - Runs on IO dispatcher — never blocks main thread
 *  - User-typed words are recorded and frequency-incremented
 *  - Results sorted by frequency descending (most-used first)
 */
class SuggestionEngine(
    private val dao: WordDao,
    private val ioScope: CoroutineScope,
    private val language: String = "en",
) {
    /**
     * Get up to 5 suggestions for the given word prefix.
     * Must be called from a suspend context on IO dispatcher.
     */
    suspend fun getSuggestions(prefix: String): List<Suggestion> = withContext(Dispatchers.IO) {
        if (prefix.length < 2) return@withContext emptyList()

        val dbResults = dao.getSuggestions(prefix.lowercase(), language)

        if (dbResults.isNotEmpty()) {
            dbResults.map { Suggestion(it.word, it.frequency.toFloat()) }
        } else {
            // Cold-start fallback — match against built-in word list
            COMMON_WORDS
                .filter { it.startsWith(prefix.lowercase()) }
                .take(5)
                .map { Suggestion(it, 0f) }
        }
    }

    /**
     * Record that a word was committed. Updates existing frequency or
     * inserts a new record. Fire-and-forget.
     */
    fun recordWord(word: String) {
        if (word.length < 2) return
        val clean = word.lowercase().filter { it.isLetter() }
        if (clean.isEmpty()) return

        ioScope.launch(Dispatchers.IO) {
            try {
                dao.insert(WordFrequencyEntity(word = clean, language = language))
            } catch (_: Exception) {}
            dao.incrementFrequency(clean, language)
        }
    }

    /**
     * Called when the system signals memory pressure.
     * Suggestions are fetched on-demand from Room; no hot cache to evict.
     */
    fun trimMemory(level: Int) {
        // No in-memory suggestion cache to release.
    }

    companion object {
        private val COMMON_WORDS = listOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "it",
            "for", "not", "on", "with", "he", "as", "you", "do", "at", "this",
            "but", "his", "by", "from", "they", "we", "say", "her", "she", "or",
            "an", "will", "my", "one", "all", "would", "there", "their", "what",
            "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
            "when", "make", "can", "like", "time", "no", "just", "him", "know",
            "take", "people", "into", "year", "your", "good", "some", "could",
            "them", "see", "other", "than", "then", "now", "look", "only", "come",
            "its", "over", "think", "also", "back", "after", "use", "two", "how",
            "our", "work", "first", "well", "way", "even", "new", "want", "because",
            "any", "these", "give", "day", "most", "us", "great", "between", "need",
            "large", "often", "hand", "high", "place", "hold", "turn", "here", "why",
            "ask", "went", "men", "read", "need", "land", "different", "home", "move",
            "try", "kind", "hand", "picture", "again", "change", "off", "play", "spell",
            "air", "away", "animal", "house", "point", "page", "letter", "mother",
            "answer", "found", "still", "learn", "should", "America", "world", "show",
        )
    }
}
