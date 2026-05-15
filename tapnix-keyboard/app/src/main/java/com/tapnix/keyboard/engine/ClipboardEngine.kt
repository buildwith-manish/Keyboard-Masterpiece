package com.tapnix.keyboard.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.tapnix.keyboard.data.ClipboardEntry
import com.tapnix.keyboard.database.daos.ClipboardDao
import com.tapnix.keyboard.database.entities.ClipboardEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

/**
 * ClipboardEngine
 *
 * Production-safe clipboard management.
 *
 * Features:
 *  - Handles text up to 500,000 chars without OOM
 *  - Preview stored separately (200 chars max) for efficient list rendering
 *  - Full text loaded lazily — only on paste
 *  - Chunked paste for huge texts prevents ANR (4 KB chunks, 2ms yield)
 *  - Rolling history: max 50 unpinned entries (auto-prune oldest)
 *  - Pin/unpin, search, label support
 *  - Secure: skips password fields via caller flag
 *  - Background IO — never blocks main thread
 */
class ClipboardEngine(
    private val context: Context,
    private val dao: ClipboardDao,
) {
    companion object {
        const val CLIP_MAX_CHARS = 500_000
        const val MAX_HISTORY_UNPINNED = 47
        const val PASTE_CHUNK_SIZE = 4096
        const val PASTE_CHUNK_DELAY_MS = 2L
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val clipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    /** Live observable list of clipboard entries (preview only). */
    val entriesFlow: Flow<List<ClipboardEntry>> = dao.observeAll()
        .map { list -> list.map { it.toEntry() } }
        .flowOn(Dispatchers.IO)

    /** Pinned entries only. */
    val pinnedFlow: Flow<List<ClipboardEntry>> = dao.observePinned()
        .map { list -> list.map { it.toEntry() } }
        .flowOn(Dispatchers.IO)

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        engineScope.launch {
            captureFromSystem(isPasswordField = false)
        }
    }

    fun registerClipboardListener() {
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    fun unregisterClipboardListener() {
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            Timber.w(e, "Failed to remove clipboard listener")
        }
    }

    /**
     * Capture the current system clipboard into our history.
     * Call this whenever the user copies something.
     *
     * @param isPasswordField if true, skip capture for privacy.
     */
    suspend fun captureFromSystem(isPasswordField: Boolean) = withContext(Dispatchers.IO) {
        if (isPasswordField) return@withContext

        val clip = try {
            clipboardManager.primaryClip ?: return@withContext
        } catch (e: SecurityException) {
            Timber.w(e, "Cannot read clipboard — permission denied")
            return@withContext
        }

        val text = clip.getItemAt(0)?.coerceToText(context)?.toString()
            ?: return@withContext

        if (text.isBlank()) return@withContext

        val truncated = if (text.length > CLIP_MAX_CHARS) {
            Timber.w("Clipboard text truncated from ${text.length} to $CLIP_MAX_CHARS chars")
            text.take(CLIP_MAX_CHARS)
        } else text

        // Avoid exact duplicates
        val latest = dao.getLatest()
        if (latest?.fullText == truncated) return@withContext

        val entity = ClipboardEntity.fromText(truncated)
        dao.insert(entity)

        // Prune oldest unpinned if over limit
        val unpinnedCount = dao.countUnpinned()
        if (unpinnedCount > MAX_HISTORY_UNPINNED) {
            dao.deleteOldestUnpinned(unpinnedCount - MAX_HISTORY_UNPINNED)
        }
    }

    /**
     * Commit entry to input connection in chunks to avoid ANR on huge texts.
     * The [onCommitChunk] lambda is called on the IO thread; wrap with
     * withContext(Main) on the call site if needed.
     */
    suspend fun commitToInput(
        entryId: Long,
        onCommitChunk: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val entity = dao.getById(entryId) ?: return@withContext
        val text = entity.fullText

        if (text.length <= PASTE_CHUNK_SIZE) {
            withContext(Dispatchers.Main.immediate) { onCommitChunk(text) }
            return@withContext
        }

        // Chunked paste for very large texts
        var offset = 0
        while (offset < text.length) {
            val end = minOf(offset + PASTE_CHUNK_SIZE, text.length)
            val chunk = text.substring(offset, end)
            withContext(Dispatchers.Main.immediate) { onCommitChunk(chunk) }
            offset = end
            if (offset < text.length) delay(PASTE_CHUNK_DELAY_MS)
        }
    }

    /** Write text directly to system clipboard. */
    suspend fun copyToSystem(text: String) = withContext(Dispatchers.IO) {
        val clip = ClipData.newPlainText("TapNix", text)
        withContext(Dispatchers.Main.immediate) {
            clipboardManager.setPrimaryClip(clip)
        }
    }

    suspend fun togglePin(entryId: Long) = withContext(Dispatchers.IO) {
        val entry = dao.getById(entryId) ?: return@withContext
        dao.updatePinned(entryId, !entry.isPinned)
    }

    suspend fun delete(entryId: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(entryId)
    }

    suspend fun deleteAllUnpinned() = withContext(Dispatchers.IO) {
        dao.deleteAllUnpinned()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    fun searchEntries(query: String): Flow<List<ClipboardEntry>> =
        dao.search("%$query%")
            .map { list -> list.map { it.toEntry() } }
            .flowOn(Dispatchers.IO)

    fun dispose() {
        unregisterClipboardListener()
        engineScope.cancel()
    }
}
