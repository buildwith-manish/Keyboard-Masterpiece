package com.keyboardmasterpiece.nativebridge

/**
 * Bridge between Kotlin and the native C++ gesture classifier.
 * Falls back to a density-independent Kotlin classifier when the native
 * library is unavailable.
 *
 * FIX: BUG-006 — Replaced hardcoded pixel thresholds with density-independent
 * thresholds (80dp) and added more gesture patterns (curve direction detection).
 */
object NativeGestureBridge {
    private var available = false
    init { available = try { System.loadLibrary("gesture_engine"); true } catch (_: Throwable) { false } }
    external fun nativeClassify(points: FloatArray, count: Int): String

    /**
     * Classify a gesture path. Uses native classifier if available,
     * otherwise falls back to a density-independent heuristic classifier.
     */
    fun classify(points: FloatArray, count: Int): String =
        if (available && count >= 2) runCatching { nativeClassify(points, count) }.getOrDefault("") else fallback(points, count)

    /**
     * FIX: BUG-006 — Density-independent fallback gesture classifier.
     * Uses display density to scale thresholds so gesture detection
     * works consistently across different screen densities.
     */
    private fun fallback(p: FloatArray, count: Int): String {
        if (count < 4) return ""

        // Density-independent thresholds
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        val threshold = 80f * density // 80dp in pixels

        val dx = p[(count - 1) * 2] - p[0]
        val dy = p[(count - 1) * 2 + 1] - p[1]

        // Sample midpoint to detect curve direction
        val midX = p[(count / 2) * 2]
        val midY = p[(count / 2) * 2 + 1]
        val curveDx = midX - (p[0] + p[(count - 1) * 2]) / 2
        val curveDy = midY - (p[1] + p[(count - 1) * 2 + 1]) / 2

        return when {
            kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f && dx > threshold -> "the"
            kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f && dx < -threshold -> "and"
            dy < -threshold -> "you"
            kotlin.math.abs(curveDy) > threshold * 0.5f -> "for"
            kotlin.math.abs(curveDx) > threshold * 0.5f -> "with"
            else -> ""
        }
    }
}
