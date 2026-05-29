package com.keyboardmasterpiece.ime

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * TASK3 — Transparent activity for file picking from the keyboard.
 *
 * InputMethodService cannot use ActivityResultLauncher directly,
 * so this activity acts as a bridge:
 * 1. Launched by KeyboardImeService with a MIME type
 * 2. Opens the system file/photo picker
 * 3. Receives the picked file URI
 * 4. Sends the file via InputConnectionCompat.commitContent()
 * 5. Finishes immediately (transparent to the user)
 */
class FilePickerActivity : Activity() {

    companion object {
        const val EXTRA_MIME_TYPE = "mime_type"
        const val REQUEST_CODE_PHOTO = 2001
        const val REQUEST_CODE_FILE = 2002
        private const val TAG = "FilePickerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mimeType = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "*/*"

        if (mimeType.startsWith("image/")) {
            launchPhotoPicker()
        } else {
            launchFilePicker(mimeType)
        }
    }

    /**
     * TASK3 — Launch photo picker.
     * Uses the Android 13+ photo picker or falls back to GET_CONTENT.
     */
    private fun launchPhotoPicker() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use Android 13+ photo picker
                Intent(android.provider.MediaStore.ACTION_PICK_IMAGES).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                // Fallback for older versions
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            }
            startActivityForResult(intent, REQUEST_CODE_PHOTO)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch photo picker", e)
            showError("Unable to open photo picker")
            finish()
        }
    }

    /**
     * TASK3 — Launch file picker for PDF, docs, audio, video, etc.
     */
    private fun launchFilePicker(mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addCategory(Intent.CATEGORY_OPENABLE)
                // Allow any file type that matches the MIME type
                putExtra(Intent.EXTRA_MIME_TYPES, getSupportedMimeTypes())
            }
            startActivityForResult(intent, REQUEST_CODE_FILE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch file picker", e)
            showError("Unable to open file picker")
            finish()
        }
    }

    /**
     * TASK3 — Supported MIME types for file picking.
     */
    private fun getSupportedMimeTypes(): Array<String> {
        return arrayOf(
            "image/*",
            "application/pdf",
            "audio/*",
            "video/*",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }

    /**
     * TASK3 — Determine the MIME type of the selected file from its URI.
     */
    private fun resolveMimeType(uri: Uri): String {
        // Try content resolver first
        val cr = contentResolver
        val resolved = cr.getType(uri)
        if (!resolved.isNullOrBlank()) return resolved

        // Fallback: guess from URI path
        val path = uri.lastPathSegment ?: uri.toString()
        return when {
            path.contains(".pdf", ignoreCase = true) -> "application/pdf"
            path.contains(".png", ignoreCase = true) -> "image/png"
            path.contains(".jpg", ignoreCase = true) || path.contains(".jpeg", ignoreCase = true) -> "image/jpeg"
            path.contains(".gif", ignoreCase = true) -> "image/gif"
            path.contains(".webp", ignoreCase = true) -> "image/webp"
            path.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
            path.contains(".wav", ignoreCase = true) -> "audio/wav"
            path.contains(".mp4", ignoreCase = true) -> "video/mp4"
            path.contains(".3gp", ignoreCase = true) -> "video/3gpp"
            path.contains(".doc", ignoreCase = true) -> "application/msword"
            path.contains(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }

    @Deprecated("Using onActivityResult for API 26+ compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data == null) {
            finish()
            return
        }

        val uri = data.data
        if (uri == null) {
            showError("No file selected")
            finish()
            return
        }

        // Take persistable URI permission
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // May not be a persistable URI (e.g., from photo picker)
        }

        // Determine MIME type
        val mimeType = resolveMimeType(uri)

        // Send the file via the IME service
        val imeService = getInputMethodService()
        if (imeService != null) {
            imeService.sendFileViaInputConnection(uri, mimeType)
        } else {
            // Fallback: share the file via intent
            fallbackShare(uri, mimeType)
        }

        finish()
    }

    /**
     * TASK3 — Get reference to the KeyboardImeService.
     */
    private fun getInputMethodService(): KeyboardImeService? {
        // The service is accessible through the system input method manager
        // But since we can't directly access the service instance,
        // we use a static reference pattern instead.
        return KeyboardImeServiceHolder.instance
    }

    /**
     * TASK3 — Fallback: share the file via a share intent.
     */
    private fun fallbackShare(uri: Uri, mimeType: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        startActivity(Intent.createChooser(shareIntent, "Share file"))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed", e)
            showError("Unable to share file")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * TASK3 — Static holder for the IME service instance.
 * This allows FilePickerActivity to communicate with the active IME service.
 * The reference is set in onCreate and cleared in onDestroy of the service.
 */
object KeyboardImeServiceHolder {
    var instance: KeyboardImeService? = null
}
