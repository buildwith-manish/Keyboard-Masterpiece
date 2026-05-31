package com.keyboardmasterpiece.engine

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.LruCache
import android.util.SparseArray
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

class SuggestionEngine(private val prefs: UserPreferences) {

    private val handlerThread = HandlerThread("suggestions-thread", Thread.MIN_PRIORITY).apply { start() }
    private val bgHandler = Handler(handlerThread.looper)
    private val mainHandler = Handler(android.os.Looper.getMainLooper())

    private val latest = AtomicReference(listOf("the", "and", "you"))

    // LRU Cache for prefix -> suggestions mapping to optimize suggestions lookup speed
    private val suggestionCache = LruCache<String, List<String>>(128)

    companion object {
        private val SUGGESTION_TOKEN = Any()
        private const val BASE_DEBOUNCE_DELAY_MS = 40L
        private const val MAX_DEBOUNCE_DELAY_MS = 100L
    }

    // Adaptive debounce based on typing velocity
    private var lastQueryTime = 0L
    private var averageInterval = 250L

    private val commonWords = ("the be to of and a in that have i it for not on with he as you do at this but his by from " +
        "they we say her she or an will my one all would there their what so up out if about who get which go me " +
        "when make can like time no just him know take people into year your good some could them see other than then " +
        "now look only come its over think also back after use two how our work first well way even new want because " +
        "any these give day most us hello thanks please keyboard message today tomorrow love great yes no ok sure " +
        "been call world still each long much hand high old right tell find here thing many before small turn own " +
        "same big play end run read help few point change large off need house try again move live place man should " +
        "part head every last never left under line hard idea open seem next kind begin life city always away real " +
        "start close night stop home state group country both paper might while picture since study keep child eye " +
        "never last let thought school important family side door side car already body class white feel black order " +
        "late young write story develop present face car face power field problem light school hold result rest " +
        "morning show water money plan example student group girl guy wait table area century team outside car " +
        "morning show city early building figure certain food remember nothing stand develop name kind service " +
        "company program market force result experience class matter sense product effect class market reason " +
        "center stage form today moment among stand however six remember local further level face rate teacher " +
        "question maybe little pretty sure love often enough probably actually usually together different really " +
        "important possible national following social economic development education probably certainly management " +
        "political research community available simply general information health support including whether especially " +
        "specific several likely actually usually environment simply window practice standard material conference " +
        "brother sister mother father daughter son husband wife friend colleague professor doctor student teacher " +
        "monday tuesday wednesday thursday friday saturday sunday january february march april may june july " +
        "august september october november december spring summer winter autumn yesterday today tomorrow morning " +
        "evening night afternoon weekend birthday holiday christmas thanksgiving halloween valentine easter").split(' ')

    private val bigrams = mapOf(
        "thank" to listOf("you", "you!", "you."),
        "good" to listOf("morning", "night", "luck", "job"),
        "see" to listOf("you", "this", "that", "later"),
        "how" to listOf("are", "is", "was", "do", "about"),
        "i" to listOf("am", "will", "have", "was", "can", "don't", "didn't", "think", "know", "want", "need"),
        "you" to listOf("are", "can", "will", "have", "know", "want", "need", "think"),
        "let" to listOf("me", "us", "it", "them"),
        "happy" to listOf("birthday", "to", "new"),
        "we" to listOf("are", "have", "will", "can", "need", "should"),
        "they" to listOf("are", "have", "will", "can", "were"),
        "it" to listOf("is", "was", "will", "has", "can"),
        "that" to listOf("is", "was", "will", "the", "this"),
        "this" to listOf("is", "was", "morning", "time", "week"),
        "the" to listOf("other", "same", "next", "first", "last", "new", "old", "most"),
        "my" to listOf("friend", "name", "phone", "house", "car", "family"),
        "is" to listOf("the", "a", "not", "very", "really", "going"),
        "was" to listOf("the", "a", "not", "very", "really", "going"),
        "are" to listOf("the", "you", "we", "they", "not", "very"),
        "have" to listOf("a", "the", "been", "to", "some"),
        "will" to listOf("be", "have", "do", "go", "make"),
        "can" to listOf("you", "we", "i", "do", "be", "see"),
        "do" to listOf("you", "we", "they", "not", "it"),
        "not" to listOf("the", "a", "going", "very", "really"),
        "very" to listOf("much", "good", "nice", "well", "big"),
        "going" to listOf("to", "on", "out"),
        "want" to listOf("to", "a", "the"),
        "need" to listOf("to", "a", "the", "some"),
        "like" to listOf("to", "the", "a", "this", "that"),
        "what" to listOf("is", "are", "was", "were", "do", "time"),
        "please" to listOf("help", "send", "call", "let", "tell"),
        "just" to listOf("like", "want", "need", "got", "did"),
        "really" to listOf("like", "want", "need", "appreciate", "enjoy"),
        "also" to listOf("want", "need", "like", "know", "think")
    )

    private val trigrams = mapOf<Pair<String, String>, List<String>>(
        ("i" to "am") to listOf("going", "not", "so", "doing", "happy", "sorry", "glad", "fine"),
        ("i" to "will") to listOf("be", "do", "go", "have", "not", "try", "call", "send", "let"),
        ("i" to "have") to listOf("a", "been", "to", "not", "the", "some", "never"),
        ("i" to "can") to listOf("do", "not", "see", "help", "make", "get", "tell"),
        ("i" to "don't") to listOf("know", "want", "think", "have", "like", "need"),
        ("i" to "didn't") to listOf("know", "want", "think", "mean", "say", "do"),
        ("i" to "think") to listOf("it", "that", "we", "you", "i", "so", "this"),
        ("i" to "know") to listOf("that", "you", "what", "how", "it", "i"),
        ("i" to "want") to listOf("to", "a", "the", "you", "some"),
        ("i" to "need") to listOf("to", "a", "the", "some", "your"),
        ("how" to "are") to listOf("you", "you?", "you!"),
        ("nice" to "to") to listOf("meet", "see", "have"),
        ("good" to "morning") to listOf("!", "everyone", "dear"),
        ("thank" to "you") to listOf("very", "so", "for"),
        ("see" to "you") to listOf("later", "soon", "tomorrow", "then"),
        ("what" to "do") to listOf("you", "we", "they", "i"),
        ("what" to "is") to listOf("the", "your", "this", "that", "a"),
        ("what" to "are") to listOf("you", "the", "they", "we"),
        ("that" to "is") to listOf("a", "the", "not", "very", "why", "right"),
        ("it" to "is") to listOf("a", "the", "not", "very", "really", "going"),
        ("this" to "is") to listOf("a", "the", "not", "very", "my", "our"),
        ("do" to "you") to listOf("want", "need", "know", "think", "have", "like"),
        ("we" to "are") to listOf("going", "not", "the", "so", "very"),
        ("we" to "have") to listOf("a", "been", "to", "the", "not"),
        ("they" to "are") to listOf("going", "not", "the", "very", "so"),
        ("there" to "is") to listOf("a", "no", "not", "the"),
        ("let" to "me") to listOf("know", "go", "see", "help", "think", "tell"),
        ("let" to "us") to listOf("know", "go", "see", "think"),
        ("in" to "the") to listOf("morning", "evening", "afternoon", "world", "future"),
        ("on" to "the") to listOf("way", "other", "phone", "table", "weekend"),
        ("at" to "the") to listOf("moment", "time", "end", "beginning"),
        ("going" to "to") to listOf("be", "do", "go", "have", "make", "the"),
        ("want" to "to") to listOf("go", "do", "be", "have", "see", "make", "know"),
        ("need" to "to") to listOf("go", "do", "be", "have", "see", "make", "know", "get"),
        ("have" to "to") to listOf("go", "do", "be", "make", "get", "see"),
        ("used" to "to") to listOf("be", "do", "go", "have"),
        ("it" to "was") to listOf("a", "the", "not", "very", "really", "so"),
        ("it" to "has") to listOf("been", "a", "not", "the")
    )

    private val wordFrequency = mutableMapOf<String, Int>()
    private var isAtSentenceStart = false

    private val trie = TrieDictionary()

    init {
        for (word in commonWords) {
            trie.insert(word)
            wordFrequency[word] = (wordFrequency[word] ?: 0) + 1
        }
        for (word in prefs.personalWords()) {
            trie.insert(word)
            wordFrequency[word] = (wordFrequency[word] ?: 0) + 1
        }
    }

    fun suggestAsync(prefix: String, previousWord: String?, previousWord2: String? = null, callback: (List<String>) -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (lastQueryTime != 0L) {
            val interval = now - lastQueryTime
            averageInterval = (averageInterval * 0.7f + interval * 0.3f).toLong().coerceIn(100L, 1000L)
        }
        lastQueryTime = now

        // Adaptive debounce delay: longer delay if typing extremely fast to avoid layout jitter
        val debounceDelay = if (averageInterval < 180L) MAX_DEBOUNCE_DELAY_MS else BASE_DEBOUNCE_DELAY_MS

        val cacheKey = "$prefix|$previousWord|$previousWord2|$isAtSentenceStart"
        val cached = suggestionCache.get(cacheKey)
        if (cached != null) {
            latest.set(cached)
            callback(cached)
            return
        }

        bgHandler.removeCallbacksAndMessages(SUGGESTION_TOKEN)
        bgHandler.postAtTime({
            val result = suggest(prefix, previousWord, previousWord2)
            suggestionCache.put(cacheKey, result)
            latest.set(result)
            mainHandler.post { callback(result) }
        }, SUGGESTION_TOKEN, SystemClock.uptimeMillis() + debounceDelay)
    }

    fun current(): List<String> = latest.get()

    fun learn(word: String) {
        val clean = word.lowercase(Locale.US).filter { it.isLetter() || it == '\'' }
        if (clean.length < 2 || prefs.incognito) return
        val set = prefs.personalWords()
        set.add(clean)
        prefs.savePersonalWords(set.toSet())
        trie.insert(clean)
        wordFrequency[clean] = (wordFrequency[clean] ?: 0) + 1
        suggestionCache.evictAll()
    }

    fun shutdown() {
        handlerThread.quitSafely()
    }

    fun clearCacheIfNeeded() {
        latest.set(listOf("the", "and", "you"))
        suggestionCache.evictAll()
    }

    fun rebuildTrie() {
        trie.clear()
        for (word in commonWords) {
            trie.insert(word)
        }
        for (word in prefs.personalWords()) {
            trie.insert(word)
        }
        suggestionCache.evictAll()
    }

    fun setSentenceStart(atStart: Boolean) {
        isAtSentenceStart = atStart
    }

    private fun suggest(prefixRaw: String, previousWord: String?, previousWord2: String? = null): List<String> {
        val prefix = prefixRaw.lowercase(Locale.US).filter { it.isLetter() || it == '\'' }
        val out = LinkedHashSet<String>()

        if (prefix.isEmpty() && previousWord != null && previousWord2 != null) {
            val key = Pair(previousWord2.lowercase(Locale.US), previousWord.lowercase(Locale.US))
            trigrams[key]?.let { out.addAll(it) }
        }

        if (prefix.isEmpty() && previousWord != null && out.size < 3) {
            bigrams[previousWord.lowercase(Locale.US)]?.let { out.addAll(it) }
        }

        if (prefix.isNotEmpty()) {
            val trieResults = trie.getSuggestions(prefix, maxResults = 8)
            val sorted = trieResults.sortedByDescending { wordFrequency[it] ?: 0 }
            for (word in sorted) {
                if (word != prefix) out.add(word)
            }

            val fuzzyCandidates = trie.getSuggestions(prefix.substring(0, min(prefix.length, 2)), maxResults = 40)
            fuzzyCandidates.asSequence()
                .filter { it.length in (prefix.length - 1)..(prefix.length + 3) && distance(prefix, it) <= 1 }
                .sortedByDescending { wordFrequency[it] ?: 0 }
                .take(5)
                .forEach(out::add)
        }

        if (out.isEmpty() && prefix.isEmpty() && previousWord == null) {
            commonWords.asSequence()
                .filter { it.length > 2 }
                .sortedByDescending { wordFrequency[it] ?: 0 }
                .take(6)
                .forEach(out::add)
        }

        if (out.isEmpty()) {
            out.addAll(listOf("the", "and", "you", "to", "for"))
        }

        val results = out.take(3).toMutableList()
        if (isAtSentenceStart && results.isNotEmpty()) {
            val first = results[0]
            if (first.all { it.isLetter() }) {
                results[0] = first.replaceFirstChar { it.uppercase() }
            }
        }

        return results
    }

    fun autocorrect(word: String): String? {
        if (prefs.incognito) return null
        val clean = word.lowercase(Locale.US).filter { it.isLetter() || it == '\'' }
        if (clean.length < 3) return null

        val prefix = clean.substring(0, min(clean.length, 2))
        val candidates = trie.getSuggestions(prefix, maxResults = 50) + commonWords.take(50)
        val candidate = candidates.minByOrNull { distance(clean, it) + (1000 - (wordFrequency[it] ?: 0)) * 0.01 } ?: return null

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

    inner class TrieNode {
        val children = SparseArray<TrieNode>()
        var isEndOfWord = false
        // Cache top predictions directly in the node for O(1) retrieval
        val cachedPredictions = mutableListOf<String>()

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
        private val rootRef = AtomicReference(TrieNode())

        @Synchronized
        fun insert(word: String) {
            var node = rootRef.get()
            val path = mutableListOf<TrieNode>()
            path.add(node)
            for (char in word) {
                node = node.getOrCreateChild(char)
                path.add(node)
            }
            node.isEndOfWord = true

            // Update top predictions on nodes along the insertion path
            for (pNode in path) {
                if (!pNode.cachedPredictions.contains(word)) {
                    pNode.cachedPredictions.add(word)
                    pNode.cachedPredictions.sortByDescending { wordFrequency[it] ?: 0 }
                    if (pNode.cachedPredictions.size > 5) {
                        pNode.cachedPredictions.removeAt(pNode.cachedPredictions.lastIndex)
                    }
                }
            }
        }

        fun getSuggestions(prefix: String, maxResults: Int = 10): List<String> {
            var node = rootRef.get()
            for (char in prefix) {
                node = node.getChild(char) ?: return emptyList()
            }
            // Return pre-cached suggestions instantly if available
            if (node.cachedPredictions.isNotEmpty()) {
                return node.cachedPredictions.take(maxResults)
            }
            val results = mutableListOf<String>()
            // DFS with depth limit (capped at max prefix depth = 4 characters forward)
            collectWords(node, StringBuilder(prefix), results, maxResults, maxDepth = 4)
            return results
        }

        private fun collectWords(node: TrieNode, current: StringBuilder, results: MutableList<String>, maxResults: Int, maxDepth: Int) {
            if (results.size >= maxResults || maxDepth <= 0) return
            if (node.isEndOfWord) results.add(current.toString())
            for (i in 0 until node.children.size()) {
                val charCode = node.children.keyAt(i)
                val child = node.children.valueAt(i)
                current.append(charCode.toChar())
                collectWords(child, current, results, maxResults, maxDepth - 1)
                current.deleteCharAt(current.length - 1)
            }
        }

        fun clear() {
            rootRef.set(TrieNode())
        }
    }
}
