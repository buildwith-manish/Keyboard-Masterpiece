package com.tapnix.keyboard.database.entities

import androidx.room.*
import com.tapnix.keyboard.data.ClipboardEntry

@Entity(tableName = "clipboard")
data class ClipboardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Short preview shown in list (max 200 chars) */
    @ColumnInfo(name = "preview")
    val preview: String,

    /** Full text content — may be up to 500,000 chars */
    @ColumnInfo(name = "full_text")
    val fullText: String,

    /** Whether this entry is pinned */
    @ColumnInfo(name = "is_pinned", index = true)
    val isPinned: Boolean = false,

    /** Optional user label */
    @ColumnInfo(name = "label")
    val label: String? = null,

    /** Creation timestamp */
    @ColumnInfo(name = "created_at", index = true)
    val createdAt: Long = System.currentTimeMillis(),

    /** Character count (stored to avoid recomputing) */
    @ColumnInfo(name = "size_chars")
    val sizeChars: Int,
) {
    fun toEntry() = ClipboardEntry(
        id = id,
        preview = preview,
        fullText = fullText,
        isPinned = isPinned,
        label = label,
        createdAt = createdAt,
        sizeChars = sizeChars,
    )

    companion object {
        const val PREVIEW_LENGTH = 200

        fun fromText(text: String, isPinned: Boolean = false): ClipboardEntity {
            val trimmed = text.trimEnd()
            return ClipboardEntity(
                preview = trimmed.take(PREVIEW_LENGTH),
                fullText = trimmed,
                isPinned = isPinned,
                sizeChars = trimmed.length,
            )
        }
    }
}
