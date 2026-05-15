package com.tapnix.keyboard.engine

import kotlinx.coroutines.*

/**
 * LongPressEngine
 *
 * Production-safe, adaptive long-press repeat mechanism for emoji spam
 * and character repeat. Key properties:
 *  - Initial threshold before repeat starts (configurable, default 500ms)
 *  - Adaptive acceleration: starts slow, ramps up
 *  - Hard floor interval prevents CPU overload
 *  - Per-pointer job map for multitouch safety
 *  - Zero ANR: compute on Default dispatcher, emit on Main
 *  - Zero leaks: structured concurrency via CoroutineScope
 */
class LongPressEngine(
    private val scope: CoroutineScope,
    private val config: LongPressConfig = LongPressConfig(),
) {
    private val activeJobs = mutableMapOf<Int, Job>()

    /**
     * Start a long-press repeat for a given pointer.
     *
     * @param pointerId unique pointer ID (for multitouch safety)
     * @param payload   text or emoji to emit on each tick
     * @param onEmit    callback invoked on Main thread for each emission
     */
    fun start(
        pointerId: Int,
        payload: String,
        onEmit: suspend (String) -> Unit,
    ) {
        cancel(pointerId)

        activeJobs[pointerId] = scope.launch(Dispatchers.Default) {
            delay(config.initialDelayMs)
            if (!isActive) return@launch

            var intervalMs = config.startIntervalMs

            while (isActive) {
                withContext(Dispatchers.Main.immediate) {
                    onEmit(payload)
                }
                delay(intervalMs)
                intervalMs = (intervalMs * config.accelerationFactor)
                    .toLong()
                    .coerceAtLeast(config.minIntervalMs)
            }
        }
    }

    /** Cancel repeat for a specific pointer (called on finger up). */
    fun cancel(pointerId: Int) {
        activeJobs.remove(pointerId)?.cancel()
    }

    /** Cancel all active sequences (call from onFinishInput). */
    fun cancelAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}

data class LongPressConfig(
    /** How long the user must hold before repeat starts (ms). */
    val initialDelayMs: Long = 500L,
    /** Starting repeat interval (ms) — slow at first. */
    val startIntervalMs: Long = 110L,
    /** Minimum interval floor (ms) — prevents CPU overload at ~35/sec. */
    val minIntervalMs: Long = 28L,
    /** Each tick the interval is multiplied by this (< 1.0 = faster). */
    val accelerationFactor: Double = 0.90,
)
