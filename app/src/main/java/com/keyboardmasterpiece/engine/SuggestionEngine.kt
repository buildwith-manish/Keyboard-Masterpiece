package com.keyboardmasterpiece.engine

import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

class SuggestionEngine(private val prefs: UserPreferences) {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "suggestions").apply { isDaemon = true } }
    private val latest = AtomicReference(listOf("the", "and", "you"))
    private val common = ("the be to of and a in that have i it for not on with he as you do at this but his by from " +
        "they we say her she or an will my one all would there their what so up out if about who get which go me " +
        "when make can like time no just him know take people into year your good some could them see other than then " +
        "now look only come its over think also back after use two how our work first well way even new want because " +
        "any these give day most us hello thanks please keyboard message today tomorrow love great yes no ok sure").split(' ')
    private val bigrams = mapOf(
        "thank" to listOf("you", "you!", "you."), "good" to listOf("morning", "night", "luck"),
        "see" to listOf("you", "this", "that"), "how" to listOf("are", "is", "was"), "i" to listOf("am", "will", "have"),
        "you" to listOf("are", "can", "will"), "let" to listOf("me", "us"), "happy" to listOf("birthday", "to")
    )

    fun suggestAsync(prefix: String, previousWord: String?, callback: (List<String>) -> Unit) {
        executor.execute {
            val result = suggest(prefix, previousWord)
            latest.set(result)
            callback(result)
        }
    }

    fun current(): List<String> = latest.get()

    fun learn(word: String) {
        val clean = word.lowercase(Locale.US).filter { it.isLetter() }
        if (clean.length < 3 || prefs.incognito) return
        val set = prefs.personalWords(); set.add(clean); prefs.savePersonalWords(set)
    }

    fun shutdown() = executor.shutdownNow()

    private fun suggest(prefixRaw: String, previousWord: String?): List<String> {
        val prefix = prefixRaw.lowercase(Locale.US).filter { it.isLetter() }
        val out = LinkedHashSet<String>()
        if (prefix.isEmpty() && previousWord != null) bigrams[previousWord.lowercase(Locale.US)]?.let(out::addAll)
        val words = prefs.personalWords() + common
        if (prefix.isNotEmpty()) {
            words.asSequence().filter { it.startsWith(prefix) && it != prefix }.take(6).forEach(out::add)
            words.asSequence().filter { it.length in (prefix.length - 1)..(prefix.length + 2) && distance(prefix, it) <= 1 }.take(3).forEach(out::add)
        }
        if (out.isEmpty()) out.addAll(listOf("the", "and", "you"))
        return out.take(3)
    }

    fun autocorrect(word: String): String? {
        val clean = word.lowercase(Locale.US).filter { it.isLetter() }
        if (clean.length < 3) return null
        val candidate = (prefs.personalWords() + common).minByOrNull { distance(clean, it) } ?: return null
        return if (candidate != clean && distance(clean, candidate) == 1) candidate else null
    }

    private fun distance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
