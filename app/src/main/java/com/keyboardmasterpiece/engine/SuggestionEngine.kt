package com.keyboardmasterpiece.engine

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.SparseArray
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * FIX: HIGH-002 — Single Handler + HandlerThread for background execution.
 * FIX: HIGH-003 — Trie-based dictionary for prefix lookup instead of linear scan.
 * FIX: MED-006 — Consistent MAX_PERSONAL_WORDS constant from UserPreferences.
 * FIX: BUG-001 — TrieDictionary.clear() now atomically replaces root via AtomicReference.
 * FIX: BUG-010 — Debounce suggestAsync() to avoid flooding the background thread.
 */
class SuggestionEngine(private val prefs: UserPreferences) {

    // FIX: HIGH-002 — Background thread with HandlerThread, single handler pair
    private val handlerThread = HandlerThread("suggestions-thread", Thread.MIN_PRIORITY).apply { start() }
    private val bgHandler = Handler(handlerThread.looper)
    private val mainHandler = Handler(android.os.Looper.getMainLooper())

    private val latest = AtomicReference(listOf("the", "and", "you"))

    // FIX: BUG-010 — Token for debouncing suggestion requests
    companion object {
        private val SUGGESTION_TOKEN = Any()
        private const val DEBOUNCE_DELAY_MS = 30L
    }

    private val commonWords = ("the be to of and a in that have i it for not on with he as you do at this but his by from " +
        "they we say her she or an will my one all would there their what so up out if about who get which go me " +
        "when make can like time no just him know take people into year your good some could them see other than then " +
        "now look only come its over think also back after use two how our work first well way even new want because " +
        "any these give day most us hello thanks please keyboard message today tomorrow love great yes no ok sure").split(' ')

    private val bigrams = mapOf(
        "thank" to listOf("you", "you!", "you."),
        "good" to listOf("morning", "night", "luck"),
        "see" to listOf("you", "this", "that"),
        "how" to listOf("are", "is", "was"),
        "i" to listOf("am", "will", "have"),
        "you" to listOf("are", "can", "will"),
        "let" to listOf("me", "us"),
        "happy" to listOf("birthday", "to")
    )

    // FIX: HIGH-003 — Trie dictionary for O(k) prefix lookup
    private val trie = TrieDictionary()

    init {
        // Build the trie with common words at startup
        for (word in commonWords) {
            trie.insert(word)
        }
        // Also load personal words into trie
        for (word in prefs.personalWords()) {
            trie.insert(word)
        }
    }

    /**
     * FIX: HIGH-002 — Use single bgHandler instead of creating new Handler each call.
     * FIX: BUG-010 — Debounce: remove pending suggestion runs before posting a new one
     * with a short delay to avoid flooding the background thread on rapid key presses.
     * Post result back to main thread via mainHandler.
     */
    fun suggestAsync(prefix: String, previousWord: String?, callback: (List<String>) -> Unit) {
        bgHandler.removeCallbacksAndMessages(SUGGESTION_TOKEN)
        bgHandler.postAtTime({
            val result = suggest(prefix, previousWord)
            latest.set(result)
            mainHandler.post { callback(result) }
        }, SUGGESTION_TOKEN, SystemClock.uptimeMillis() + DEBOUNCE_DELAY_MS)
    }

    fun current(): List<String> = latest.get()

    /** FIX: MED-006 — Use consistent MAX_PERSONAL_WORDS constant. */
    fun learn(word: String) {
        val clean = word.lowercase(Locale.US).filter { it.isLetter() || it == '\'' }
        if (clean.length < 2 || prefs.incognito) return
        val set = prefs.personalWords()
        set.add(clean)
        prefs.savePersonalWords(set.toSet())
        // Keep trie in sync
        trie.insert(clean)
    }

    /** FIX: HIGH-002 — Proper shutdown of HandlerThread. */
    fun shutdown() {
        handlerThread.quitSafely()
    }

    fun clearCacheIfNeeded() {
        latest.set(listOf("the", "and", "you"))
    }

    /** Rebuild trie when personal words change externally. */
    fun rebuildTrie() {
        trie.clear()
        for (word in commonWords) {
            trie.insert(word)
        }
        for (word in prefs.personalWords()) {
            trie.insert(word)
        }
    }

    private fun suggest(prefixRaw: String, previousWord: String?): List<String> {
        val prefix = prefixRaw.lowercase(Locale.US).filter { it.isLetter() || it == '\'' }
        val out = LinkedHashSet<String>()

        // Context from previous word (bigrams)
        if (prefix.isEmpty() && previousWord != null) {
            bigrams[previousWord.lowercase(Locale.US)]?.let { out.addAll(it) }
        }

        // FIX: HIGH-003 — Use trie for prefix-based lookup instead of linear scan
        if (prefix.isNotEmpty()) {
            // Prefix match from Trie — much faster than linear scan
            val trieResults = trie.getSuggestions(prefix, maxResults = 8)
            for (word in trieResults) {
                if (word != prefix) out.add(word)
            }

            // Simple fuzzy (edit distance 1) — limited candidates from trie first
            val fuzzyCandidates = trie.getSuggestions(prefix.substring(0, min(prefix.length, 2)), maxResults = 50)
            fuzzyCandidates.asSequence()
                .filter { it.length in (prefix.length - 1)..(prefix.length + 3) && distance(prefix, it) <= 1 }
                .take(5)
                .forEach(out::add)
        }

        if (out.isEmpty()) {
            out.addAll(listOf("the", "and", "you", "to", "for"))
        }

        return out.take(3)
    }

    fun autocorrect(word: String): String? {
        if (prefs.incognito) return null
        val clean = word.lowercase(Locale.US).filter { it.isLetter() || it == '\'' }
        if (clean.length < 3) return null

        // FIX: HIGH-003 — Limit edit-distance candidates from trie prefix
        val prefix = clean.substring(0, min(clean.length, 2))
        val candidates = trie.getSuggestions(prefix, maxResults = 80) + commonWords.take(50)
        val candidate = candidates.minByOrNull { distance(clean, it) } ?: return null

        val dist = distance(clean, candidate)
        return if (candidate != clean && dist <= 1) candidate else null
    }

    private fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /**
     * FIX: HIGH-003 — Trie data structure for efficient prefix-based word lookup.
     * Uses SparseArray<TrieNode> for children (more efficient than HashMap on Android).
     * FIX: BUG-001 — Root is stored in AtomicReference so clear() can atomically replace it.
     */
    inner class TrieNode {
        val children = SparseArray<TrieNode>()
        var isEndOfWord = false

        fun getChild(char: Char): TrieNode? = children.get(char.code)
        fun getOrCreateChild(char: Char): TrieNode {
            var node = children.get(char.code)
            if (node == null) {
                node = TrieNode()
                children.put(char.code, node)
            }
            return node
        }
    }

    inner class TrieDictionary {
        // FIX: BUG-001 — Use AtomicReference for root so clear() can atomically replace it
        private val rootRef = AtomicReference(TrieNode())

        fun insert(word: String) {
            var node = rootRef.get()
            for (char in word) {
                node = node.getOrCreateChild(char)
            }
            node.isEndOfWord = true
        }

        /**
         * Get all words with the given prefix, up to maxResults.
         * Performs DFS from the prefix node.
         */
        fun getSuggestions(prefix: String, maxResults: Int = 10): List<String> {
            var node = rootRef.get()
            for (char in prefix) {
                node = node.getChild(char) ?: return emptyList()
            }
            val results = mutableListOf<String>()
            collectWords(node, prefix, results, maxResults)
            return results
        }

        private fun collectWords(node: TrieNode, current: String, results: MutableList<String>, maxResults: Int) {
            if (results.size >= maxResults) return
            if (node.isEndOfWord) results.add(current)
            for (i in 0 until node.children.size()) {
                val charCode = node.children.keyAt(i)
                val child = node.children.valueAt(i)
                collectWords(child, current + charCode.toChar(), results, maxResults)
            }
        }

        /**
         * FIX: BUG-001 — clear() now atomically replaces the root node.
         * The old trie structure will be garbage collected since no references remain.
         */
        fun clear() {
            rootRef.set(TrieNode())
        }
    }
}
