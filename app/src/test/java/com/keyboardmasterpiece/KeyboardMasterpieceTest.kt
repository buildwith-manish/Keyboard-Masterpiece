package com.keyboardmasterpiece

import android.graphics.PointF
import com.keyboardmasterpiece.core.CursorState
import com.keyboardmasterpiece.core.UndoEntry
import com.keyboardmasterpiece.input.GestureController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMasterpieceTest {

    @Test
    fun testKalmanFilterSmoothing() {
        val controller = GestureController()
        val noisyPoints = listOf(
            PointF(10f, 10f),
            PointF(12f, 11f),
            PointF(11f, 9f),
            PointF(15f, 15f),
            PointF(20f, 20f)
        )

        val smoothed = controller.applyKalmanFilter(noisyPoints)
        assertEquals(noisyPoints.size, smoothed.size)
        // Verify start position is preserved exactly
        assertEquals(noisyPoints[0].x, smoothed[0].x, 0.001f)
        assertEquals(noisyPoints[0].y, smoothed[0].y, 0.001f)
    }

    @Test
    fun testDTWTrajectorySimilarity() {
        val controller = GestureController()

        // Trajectory A: simple diagonal line
        val trajA = listOf(PointF(0f, 0f), PointF(0.5f, 0.5f), PointF(1f, 1f))
        // Trajectory B: identical line
        val trajB = listOf(PointF(0f, 0f), PointF(0.5f, 0.5f), PointF(1f, 1f))
        // Trajectory C: slightly curved line
        val trajC = listOf(PointF(0f, 0f), PointF(0.4f, 0.6f), PointF(1f, 1f))

        val scoreIdentical = controller.computeDTWSimilarity(trajA, trajB)
        val scoreCurved = controller.computeDTWSimilarity(trajA, trajC)

        assertEquals(1.0f, scoreIdentical, 0.001f)
        assertTrue(scoreCurved > 0.8f)
        assertTrue(scoreIdentical >= scoreCurved)
    }

    @Test
    fun testCursorStateAndUndoStructures() {
        val state = CursorState(5, 10)
        assertEquals(5, state.start)
        assertEquals(10, state.end)

        val entry = UndoEntry("hello", 5, 2, 2, 7)
        assertEquals("hello", entry.text)
        assertEquals(5, entry.actualTextLength)
        assertEquals(2, entry.cursorStart)
        assertEquals(7, entry.selectionEnd)
    }
}
