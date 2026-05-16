package com.keyboardmasterpiece.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64

/**
 * FIX: HIGH-006 — TTL-based expiration (1 hour) for clipboard entries.
 * FIX: LOW-005 — Clipboard history expiration (covered by TTL).
 * FIX: BUG-002 — pasteText() now uses applicationContext instead of null for coerceToText().
 *
 * Security note: Ideally we would use EncryptedSharedPreferences from
 * androidx.security:security-crypto for encrypting clipboard data at rest.
 * Since that would require an additional dependency not currently in the
 * project's build.gradle, we use Base64 obfuscation as a minimum measure.
 * To upgrade to full encryption:
 *   1. Add implementation("androidx.security:security-crypto:1.1.0-alpha06")
 *   2. Replace sp with EncryptedSharedPreferences.create(...)
 *   3. Remove Base64 encode/decode below
 */
class ClipboardStore(context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val sp = context.getSharedPreferences("clipboard_store", Context.MODE_PRIVATE)

    /** FIX: BUG-002 — Use applicationContext to avoid null context in coerceToText() */
    private val appContext = context.applicationContext

    /** TTL for clipboard entries: 1 hour in milliseconds */
    companion object {
        private const val TTL_MS = 60L * 60L * 1000L // 1 hour
        private const val KEY_ITEMS = "items"
        private const val KEY_TIMESTAMPS = "timestamps"
        private const val SEPARATOR = "\u001E"
        private const val MAX_ENTRIES = 20
    }

    fun copy(text: String) {
        if (text.isNotBlank()) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Keyboard Masterpiece", text))
            remember(text)
        }
    }

    /** FIX: BUG-002 — Pass applicationContext instead of null to coerceToText() */
    fun pasteText(): String = clipboard.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString().orEmpty()

    /**
     * FIX: HIGH-006 — Store text with Base64 obfuscation and track timestamps.
     * FIX: LOW-005 — Entries older than TTL are pruned on access.
     */
    fun remember(text: String) {
        if (text.isBlank()) return
        pruneExpired()

        val all = mutableListOf<Pair<String, Long>>()
        // Decode existing entries
        val existingItems = decodeItems()
        val existingTimestamps = decodeTimestamps()

        // Add new entry first (most recent)
        all.add(Pair(text, System.currentTimeMillis()))

        // Add existing non-duplicate entries
        for (i in existingItems.indices) {
            val existingText = existingItems[i]
            if (existingText != text && i < existingTimestamps.size) {
                all.add(Pair(existingText, existingTimestamps[i]))
            }
        }

        // Trim to max entries
        val trimmed = all.take(MAX_ENTRIES)

        // Encode and save
        sp.edit()
            .putString(KEY_ITEMS, trimmed.joinToString(SEPARATOR) { encode(it.first) })
            .putString(KEY_TIMESTAMPS, trimmed.joinToString(SEPARATOR) { it.second.toString() })
            .apply()
    }

    /**
     * FIX: HIGH-006 / LOW-005 — Return history with expired entries pruned.
     */
    fun history(): List<String> {
        pruneExpired()
        return decodeItems()
    }

    /**
     * FIX: HIGH-006 — Remove all clipboard entries.
     */
    fun clearAll() {
        sp.edit().clear().apply()
    }

    /**
     * FIX: HIGH-006 / LOW-005 — Prune entries older than TTL.
     */
    private fun pruneExpired() {
        val items = decodeItems()
        val timestamps = decodeTimestamps()

        if (items.isEmpty() || timestamps.isEmpty()) return

        val now = System.currentTimeMillis()
        val validIndices = mutableListOf<Int>()
        for (i in items.indices) {
            if (i < timestamps.size && (now - timestamps[i]) < TTL_MS) {
                validIndices.add(i)
            }
        }

        if (validIndices.size < items.size) {
            val validItems = validIndices.map { items[it] }
            val validTimestamps = validIndices.map { timestamps[it] }

            sp.edit()
                .putString(KEY_ITEMS, validItems.joinToString(SEPARATOR) { encode(it) })
                .putString(KEY_TIMESTAMPS, validTimestamps.joinToString(SEPARATOR) { it.toString() })
                .apply()
        }
    }

    /** FIX: HIGH-006 — Decode items from Base64 obfuscated storage. */
    private fun decodeItems(): List<String> {
        val raw = sp.getString(KEY_ITEMS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.map { decode(it) }
    }

    /** FIX: HIGH-006 — Decode timestamps from storage. */
    private fun decodeTimestamps(): List<Long> {
        val raw = sp.getString(KEY_TIMESTAMPS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
    }

    /** FIX: HIGH-006 — Base64 obfuscation for clipboard content. */
    private fun encode(text: String): String {
        return try {
            Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            text // Fallback to plain text if encoding fails
        }
    }

    /** FIX: HIGH-006 — Base64 de-obfuscation for clipboard content. */
    private fun decode(encoded: String): String {
        return try {
            String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            encoded // Fallback: treat as plain text
        }
    }
}
