package com.tapnix.keyboard.engine

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * SwipeTypingEngine
 *
 * Production swipe-typing (gesture keyboard) engine.
 *
 * Algorithm:
 *  1. Receive a list of Offset samples collected during a finger-drag gesture.
 *  2. Normalize points against keyboard bounds (width, height).
 *  3. For each sample, find the nearest QWERTY key center.
 *  4. Deduplicate consecutive identical keys → produces a raw key path.
 *  5. Filter candidate words from the built-in dictionary that:
 *       a. Start with the same key as the path's first key
 *       b. End with the same key as the path's last key
 *       c. Have their unique key sequence as a subsequence of the path
 *  6. Score candidates by path similarity and return top 5.
 *
 * Key positions use a normalised grid:
 *   Row 0 (Q–P): 10 keys, x in [0..9], y = 0
 *   Row 1 (A–L):  9 keys, x in [0.5..8.5], y = 1
 *   Row 2 (Z–M):  7 keys, x in [1.5..7.5], y = 2
 *
 * Runs entirely on Dispatchers.Default — never touches the main thread.
 */
class SwipeTypingEngine {

    companion object {
        private val KEY_GRID: Map<Char, Pair<Float, Float>> = mapOf(
            'q' to Pair(0f, 0f), 'w' to Pair(1f, 0f), 'e' to Pair(2f, 0f),
            'r' to Pair(3f, 0f), 't' to Pair(4f, 0f), 'y' to Pair(5f, 0f),
            'u' to Pair(6f, 0f), 'i' to Pair(7f, 0f), 'o' to Pair(8f, 0f),
            'p' to Pair(9f, 0f),
            'a' to Pair(0.5f, 1f), 's' to Pair(1.5f, 1f), 'd' to Pair(2.5f, 1f),
            'f' to Pair(3.5f, 1f), 'g' to Pair(4.5f, 1f), 'h' to Pair(5.5f, 1f),
            'j' to Pair(6.5f, 1f), 'k' to Pair(7.5f, 1f), 'l' to Pair(8.5f, 1f),
            'z' to Pair(1.5f, 2f), 'x' to Pair(2.5f, 2f), 'c' to Pair(3.5f, 2f),
            'v' to Pair(4.5f, 2f), 'b' to Pair(5.5f, 2f), 'n' to Pair(6.5f, 2f),
            'm' to Pair(7.5f, 2f),
        )

        // Minimum path points to attempt a word match
        private const val MIN_PATH_POINTS = 3

        // Cell width in grid units for proximity threshold
        private const val PROXIMITY_THRESHOLD_GRID = 0.9f
    }

    /**
     * Convert a raw touch path into ranked word candidates.
     *
     * @param path List of Offset points in keyboard-local coordinates.
     * @param keyboardWidth  Width of the keyboard area in pixels.
     * @param keyboardRowHeight Height of a single key row in pixels.
     * @param userWords Words the user has typed (scored higher if matching).
     * @return Up to 5 word candidates, best first.
     */
    suspend fun getSwipeCandidates(
        path: List<Offset>,
        keyboardWidth: Float,
        keyboardRowHeight: Float,
        userWords: List<String> = emptyList(),
    ): List<String> = withContext(Dispatchers.Default) {
        if (path.size < MIN_PATH_POINTS || keyboardWidth <= 0f || keyboardRowHeight <= 0f) {
            return@withContext emptyList()
        }

        val cellW = keyboardWidth / 10f
        val cellH = keyboardRowHeight

        val keyPath = extractKeyPath(path, cellW, cellH)
        if (keyPath.size < 2) return@withContext emptyList()

        val candidates = findCandidates(keyPath, userWords)
        candidates
    }

    private fun extractKeyPath(
        path: List<Offset>,
        cellWidth: Float,
        cellHeight: Float,
    ): List<Char> {
        val result = mutableListOf<Char>()
        for (point in path) {
            val key = nearestKey(point, cellWidth, cellHeight) ?: continue
            if (result.lastOrNull() != key) {
                result.add(key)
            }
        }
        return result
    }

    private fun nearestKey(point: Offset, cellWidth: Float, cellHeight: Float): Char? {
        var best: Char? = null
        var minDist = Float.MAX_VALUE
        KEY_GRID.forEach { (ch, pos) ->
            val cx = pos.first * cellWidth + cellWidth * 0.5f
            val cy = pos.second * cellHeight + cellHeight * 0.5f
            val d = hypot(point.x - cx, point.y - cy)
            if (d < minDist) {
                minDist = d
                best = ch
            }
        }
        return best
    }

    private fun findCandidates(keyPath: List<Char>, userWords: List<String>): List<String> {
        val first = keyPath.first()
        val last = keyPath.last()
        val minLen = maxOf(2, keyPath.size - 3)
        val maxLen = keyPath.size + 4

        // Merge user words + dictionary, prioritising user words
        val combined = (userWords + SWIPE_DICTIONARY).distinct()

        return combined
            .filter { word ->
                word.length in minLen..maxLen &&
                        word.first() == first &&
                        word.last() == last &&
                        isKeyPathSubsequence(word, keyPath)
            }
            .sortedWith(
                compareByDescending<String> { userWords.contains(it) }
                    .thenByDescending { pathSimilarity(it, keyPath) }
                    .thenBy { it.length }
            )
            .take(5)
    }

    /**
     * Check whether the key sequence of [word] is a subsequence of [path].
     * Consecutive duplicate keys within the word are merged first.
     */
    private fun isKeyPathSubsequence(word: String, path: List<Char>): Boolean {
        val wordPath = word.toList().zipWithNext()
            .filter { (a, b) -> a != b }
            .map { it.first } + word.last()

        var wi = 0
        for (pk in path) {
            if (wi < wordPath.size && wordPath[wi] == pk) wi++
        }
        return wi == wordPath.size
    }

    /** Higher score = better match with path shape. */
    private fun pathSimilarity(word: String, path: List<Char>): Float {
        if (path.isEmpty()) return 0f
        var matches = 0
        var pi = 0
        for (ch in word) {
            while (pi < path.size && path[pi] != ch) pi++
            if (pi < path.size) { matches++; pi++ }
        }
        return matches.toFloat() / maxOf(word.length, path.size)
    }

    // ─── Built-in swipe dictionary ────────────────────────────────────────────
    private val SWIPE_DICTIONARY = listOf(
        "the", "that", "this", "they", "them", "then", "there", "their", "these",
        "be", "been", "best", "but", "by", "bit", "bat", "bet",
        "and", "are", "at", "all", "as", "an", "any",
        "have", "has", "had", "he", "her", "him", "his", "how",
        "in", "it", "is", "if", "its", "into",
        "for", "from", "first",
        "not", "now", "no", "new", "name",
        "on", "one", "our", "or", "out", "off", "over",
        "people", "place", "put",
        "said", "say", "she", "so", "some", "such",
        "to", "time", "than", "two", "their", "there", "take",
        "up", "use",
        "was", "we", "were", "what", "when", "which", "who", "will", "with", "word",
        "you", "your",
        "about", "after", "again", "also", "always", "another", "away",
        "back", "because", "been", "before", "between",
        "came", "can", "come", "could",
        "day", "did", "different", "do", "does",
        "each", "even", "every",
        "find", "follow", "found",
        "get", "give", "good", "great", "go", "going", "got",
        "here", "high",
        "just",
        "keep", "kind", "know",
        "large", "last", "later", "leave", "letter", "like", "little", "long", "look",
        "make", "man", "many", "may", "me", "mean", "might", "more", "most", "move", "much", "must", "my",
        "need", "next", "night",
        "often", "old", "open", "other",
        "part", "play", "point", "put",
        "read", "really", "right",
        "same", "seem", "sentence", "should", "show", "side", "small", "still", "study",
        "tell", "thing", "think", "three", "through", "together", "too", "turn",
        "under", "until", "us",
        "very",
        "want", "way", "well", "went", "where", "while", "why", "work", "world", "would", "write",
        "year", "yet",
        "able", "ask", "between", "car", "city", "down", "end", "family", "food", "hand",
        "home", "house", "left", "letter", "life", "live", "near", "once", "school", "second",
        "soon", "start", "stop", "those", "together", "try", "turn", "under", "walk", "water",
        "without",
    )
}
