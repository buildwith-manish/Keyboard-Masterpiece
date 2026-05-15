package com.tapnix.keyboard.engine

import com.tapnix.keyboard.data.Suggestion
import com.tapnix.keyboard.database.daos.BigramDao
import com.tapnix.keyboard.database.daos.WordDao
import com.tapnix.keyboard.database.entities.BigramEntity
import com.tapnix.keyboard.database.entities.WordFrequencyEntity
import kotlinx.coroutines.*

/**
 * SuggestionEngine
 *
 * Generates word suggestions combining:
 *  1. Prefix-matching from the user's personal frequency database
 *  2. Built-in common English words as cold-start fallback
 *  3. Bigram next-word prediction ("after word X, word Y is likely")
 *
 * All operations run on IO dispatcher — never blocks main thread.
 *
 * Adaptive Learning:
 *   recordWord(prev, word) updates both unigram frequency and bigram pair,
 *   so predictions personalise over time without any cloud dependency.
 */
class SuggestionEngine(
    private val dao: WordDao,
    private val bigramDao: BigramDao,
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
            COMMON_WORDS
                .filter { it.startsWith(prefix.lowercase()) }
                .take(5)
                .map { Suggestion(it, 0f) }
        }
    }

    /**
     * Get next-word predictions based on the previously typed word.
     * Returns up to 3 suggestions flagged as [Suggestion.isNextWord].
     */
    suspend fun getNextWordSuggestions(previousWord: String): List<Suggestion> =
        withContext(Dispatchers.IO) {
            if (previousWord.length < 2) return@withContext emptyList()
            val bigrams = bigramDao.getFollowOns(
                word1 = previousWord.lowercase(),
                language = language,
                limit = 3,
            )
            bigrams.map { Suggestion(it.word2, it.frequency.toFloat(), isNextWord = true) }
        }

    /**
     * Record that [word] was committed. Updates unigram frequency.
     * If [previousWord] is provided, also records the bigram pair.
     * Fire-and-forget — does not block the caller.
     */
    fun recordWord(word: String, previousWord: String? = null) {
        if (word.length < 2) return
        val clean = word.lowercase().filter { it.isLetter() }
        if (clean.isEmpty()) return

        ioScope.launch(Dispatchers.IO) {
            try {
                dao.insert(WordFrequencyEntity(word = clean, language = language))
            } catch (_: Exception) {}
            dao.incrementFrequency(clean, language)

            if (previousWord != null && previousWord.length >= 2) {
                val prev = previousWord.lowercase().filter { it.isLetter() }
                if (prev.isNotEmpty()) {
                    recordBigram(prev, clean)
                }
            }
        }
    }

    private suspend fun recordBigram(word1: String, word2: String) {
        try {
            bigramDao.insert(
                BigramEntity(word1 = word1, word2 = word2, language = language)
            )
        } catch (_: Exception) {}
        bigramDao.incrementFrequency(word1, word2, language)

        // Prune table if it grows too large (keep it under ~2000 entries)
        val count = bigramDao.count()
        if (count > 2000) {
            bigramDao.pruneOldLowFrequency(count - 1800)
        }
    }

    /**
     * Called when the system signals memory pressure.
     */
    fun trimMemory(level: Int) {
        // No in-memory cache to release.
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
