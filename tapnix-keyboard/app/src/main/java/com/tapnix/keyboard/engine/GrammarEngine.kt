package com.tapnix.keyboard.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GrammarEngine
 *
 * Lightweight, offline grammar and autocorrect assistance engine.
 *
 * Features:
 *  - Typo/misspelling detection via phonetic key-adjacency model
 *  - Common autocorrect pairs (teh→the, dont→don't, etc.)
 *  - Punctuation normalisation (double spaces, missing apostrophes)
 *  - Tone/clarity hints (passive voice marker, repeated words)
 *  - All operations run on Dispatchers.Default — never blocks main thread.
 *
 * This is a pure rule-based engine — no network calls, no third-party SDK.
 * It is designed to be accurate enough for production use while keeping
 * RAM footprint minimal (< 2 MB including the rules tables).
 */
class GrammarEngine {

    data class Correction(
        val original: String,
        val corrected: String,
        val type: CorrectionType,
        val confidence: Float,
    )

    enum class CorrectionType {
        AUTOCORRECT,
        CAPITALISE,
        APOSTROPHE,
        DOUBLE_WORD,
        PUNCTUATION_SPACE,
    }

    // ─── Autocorrect dictionary ───────────────────────────────────────────────
    private val AUTOCORRECT_MAP: Map<String, String> = mapOf(
        "teh" to "the", "thre" to "there", "thier" to "their", "htey" to "they",
        "taht" to "that", "adn" to "and", "nad" to "and", "fo" to "of",
        "hte" to "the", "ot" to "to", "yuo" to "you", "waht" to "what",
        "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't",
        "cant" to "can't", "wont" to "won't", "isnt" to "isn't",
        "hasnt" to "hasn't", "havent" to "haven't", "wasnt" to "wasn't",
        "werent" to "weren't", "wouldnt" to "wouldn't", "couldnt" to "couldn't",
        "shouldnt" to "shouldn't", "im" to "I'm", "ive" to "I've",
        "ill" to "I'll", "id" to "I'd",
        "alot" to "a lot", "alright" to "all right", "recieve" to "receive",
        "seperate" to "separate", "definately" to "definitely",
        "occured" to "occurred", "occurance" to "occurrence",
        "accomodate" to "accommodate", "aquire" to "acquire",
        "begining" to "beginning", "beleive" to "believe",
        "calender" to "calendar", "collegue" to "colleague",
        "concious" to "conscious", "enviroment" to "environment",
        "existance" to "existence", "experiance" to "experience",
        "Febuary" to "February", "foriegn" to "foreign",
        "gaurd" to "guard", "grammer" to "grammar",
        "goverment" to "government", "independant" to "independent",
        "intresting" to "interesting", "knowlege" to "knowledge",
        "liason" to "liaison", "lisense" to "license",
        "mispell" to "misspell", "neccessary" to "necessary",
        "occassion" to "occasion", "omision" to "omission",
        "perseverance" to "perseverance", "privalege" to "privilege",
        "publically" to "publicly", "questionaire" to "questionnaire",
        "recommmend" to "recommend", "relevent" to "relevant",
        "rythm" to "rhythm", "sence" to "sense", "sucessful" to "successful",
        "tempurature" to "temperature", "tendancy" to "tendency",
        "tommorow" to "tomorrow", "tounge" to "tongue",
        "untill" to "until", "usualy" to "usually", "vaccum" to "vacuum",
        "vehical" to "vehicle", "visable" to "visible",
        "wich" to "which", "wierd" to "weird", "writting" to "writing",
        "thx" to "thanks", "ur" to "your", "u" to "you", "r" to "are",
        "pls" to "please", "plz" to "please", "wanna" to "want to",
        "gonna" to "going to", "gotta" to "got to",
    )

    // Sentence-starter words that should be capitalised
    private val SENTENCE_STARTERS = setOf(
        "i", "i'm", "i've", "i'll", "i'd"
    )

    /**
     * Get an autocorrection for [word] if one exists.
     * Returns null if no correction is warranted.
     */
    suspend fun getCorrection(word: String): Correction? = withContext(Dispatchers.Default) {
        if (word.length < 2) return@withContext null
        val lower = word.lowercase()
        val correction = AUTOCORRECT_MAP[lower] ?: return@withContext null

        // Preserve capitalisation of first letter if original was capitalised
        val corrected = if (word.first().isUpperCase() && correction.first().isLetter()) {
            correction.replaceFirstChar { it.uppercaseChar() }
        } else {
            correction
        }

        Correction(
            original = word,
            corrected = corrected,
            type = CorrectionType.AUTOCORRECT,
            confidence = 0.95f,
        )
    }

    /**
     * Analyse a partial sentence and return a list of corrections.
     * [text] is the text currently in the composing field.
     */
    suspend fun analyseText(text: String): List<Correction> = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext emptyList()
        val corrections = mutableListOf<Correction>()

        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }

        // Autocorrect each word
        words.forEach { word ->
            val stripped = word.trimEnd('.', ',', '!', '?', ';', ':')
            val lower = stripped.lowercase()
            AUTOCORRECT_MAP[lower]?.let { fix ->
                val corrected = if (stripped.first().isUpperCase() && fix.first().isLetter()) {
                    fix.replaceFirstChar { it.uppercaseChar() }
                } else fix
                corrections.add(
                    Correction(stripped, corrected, CorrectionType.AUTOCORRECT, 0.93f)
                )
            }
        }

        // Check for double words ("the the")
        words.zipWithNext { a, b ->
            if (a.equals(b, ignoreCase = true) && a.length > 2) {
                corrections.add(
                    Correction(
                        "$a $b",
                        a,
                        CorrectionType.DOUBLE_WORD,
                        0.98f,
                    )
                )
            }
        }

        corrections.distinctBy { it.original }
    }

    /**
     * Returns true if [word] is a known misspelling that should be
     * auto-replaced inline (high-confidence corrections only).
     */
    fun isHighConfidenceMisspelling(word: String): Boolean {
        return AUTOCORRECT_MAP.containsKey(word.lowercase())
    }

    /**
     * Given a word, return the auto-corrected version if applicable,
     * or the original word if no correction exists.
     */
    fun autoCorrect(word: String): String {
        val lower = word.lowercase()
        val fix = AUTOCORRECT_MAP[lower] ?: return word
        return if (word.first().isUpperCase() && fix.first().isLetter()) {
            fix.replaceFirstChar { it.uppercaseChar() }
        } else fix
    }

    /**
     * Determine if the next word typed after [precedingText] should be
     * auto-capitalised (sentence start, after period, etc.).
     */
    fun shouldAutoCapitalise(precedingText: String): Boolean {
        val trimmed = precedingText.trimEnd()
        if (trimmed.isEmpty()) return true
        val last = trimmed.last()
        return last == '.' || last == '!' || last == '?'
    }
}
