package com.keyboardmasterpiece.nativebridge

object NativeGestureBridge {
    private var available = false
    init { available = try { System.loadLibrary("gesture_engine"); true } catch (_: Throwable) { false } }
    external fun nativeClassify(points: FloatArray, count: Int): String
    fun classify(points: FloatArray, count: Int): String = if (available && count >= 2) runCatching { nativeClassify(points, count) }.getOrDefault("") else fallback(points, count)
    private fun fallback(p: FloatArray, count: Int): String {
        if (count < 2) return ""
        val dx = p[(count - 1) * 2] - p[0]; val dy = p[(count - 1) * 2 + 1] - p[1]
        return when { kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2 && dx > 160 -> "the"; kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2 && dx < -160 -> "and"; dy < -180 -> "you"; else -> "" }
    }
}
