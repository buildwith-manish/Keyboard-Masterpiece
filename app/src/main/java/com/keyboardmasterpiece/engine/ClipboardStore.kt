package com.keyboardmasterpiece.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardStore(context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val sp = context.getSharedPreferences("clipboard_store", Context.MODE_PRIVATE)
    fun copy(text: String) { if (text.isNotBlank()) { clipboard.setPrimaryClip(ClipData.newPlainText("Keyboard Masterpiece", text)); remember(text) } }
    fun pasteText(): String = clipboard.primaryClip?.getItemAt(0)?.coerceToText(null)?.toString().orEmpty()
    fun remember(text: String) { if (text.isBlank()) return; val all = listOf(text) + history().filter { it != text }; sp.edit().putString("items", all.take(20).joinToString("\u001E")).apply() }
    fun history(): List<String> = sp.getString("items", "")!!.split("\u001E").filter { it.isNotBlank() }
}
