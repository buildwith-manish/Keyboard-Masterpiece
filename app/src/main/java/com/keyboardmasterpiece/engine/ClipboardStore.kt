package com.keyboardmasterpiece.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * FIX: HIGH-006 — TTL-based expiration (1 hour) for clipboard entries.
 * FIX: LOW-005 — Clipboard history expiration (covered by TTL).
 * FIX: BUG-002 — pasteText() now uses applicationContext instead of null for coerceToText().
 * FIX: FINAL-001 — Replaced Base64 obfuscation with EncryptedSharedPreferences.
 *   Clipboard data is now encrypted at rest using AES256_SIV (key) + AES256_GCM (value).
 *   No more trivially-reversible Base64 encoding of sensitive user data.
 */
class ClipboardStore(context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    /** FIX: FINAL-001 — Use EncryptedSharedPreferences for clipboard data at rest */
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "clipboard_store_encrypted",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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
     * FIX: HIGH-006 — Store text with encryption and track timestamps.
     * FIX: LOW-005 — Entries older than TTL are pruned on access.
     * FIX: FINAL-001 — No more Base64; EncryptedSharedPreferences handles encryption.
     */
    fun remember(text: String) {
        if (text.isBlank()) return
        pruneExpired()

        val all = mutableListOf<Pair<String, Long>>()
        val existingItems = decodeItems()
        val existingTimestamps = decodeTimestamps()

        all.add(Pair(text, System.currentTimeMillis()))

        for (i in existingItems.indices) {
            val existingText = existingItems[i]
            if (existingText != text && i < existingTimestamps.size) {
                all.add(Pair(existingText, existingTimestamps[i]))
            }
        }

        val trimmed = all.take(MAX_ENTRIES)

        sp.edit()
            .putString(KEY_ITEMS, trimmed.joinToString(SEPARATOR) { it.first })
            .putString(KEY_TIMESTAMPS, trimmed.joinToString(SEPARATOR) { it.second.toString() })
            .apply()
    }

    fun history(): List<String> {
        pruneExpired()
        return decodeItems()
    }

    fun clearAll() {
        sp.edit().clear().apply()
    }

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
                .putString(KEY_ITEMS, validItems.joinToString(SEPARATOR) { it })
                .putString(KEY_TIMESTAMPS, validTimestamps.joinToString(SEPARATOR) { it.toString() })
                .apply()
        }
    }

    /** FIX: FINAL-001 — Read items directly from encrypted storage (no Base64 decode) */
    private fun decodeItems(): List<String> {
        val raw = sp.getString(KEY_ITEMS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    private fun decodeTimestamps(): List<Long> {
        val raw = sp.getString(KEY_TIMESTAMPS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
    }
}
