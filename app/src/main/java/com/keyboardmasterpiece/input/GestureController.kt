package com.keyboardmasterpiece.input

import android.graphics.PointF
import kotlin.math.sqrt

// Advanced Gesture Controller that implements Kalman Filter smoothing,
// Dynamic Time Warping (DTW) alignment matching, and templates.
class GestureController {

    private val templates = mutableMapOf<String, List<PointF>>()
    var debugMode: Boolean = false

    companion object {
        private const val DTW_MATCH_THRESHOLD = 0.82f
    }

    init {
        // Pre-compute and cache gesture path templates for common words on a standard QWERTY grid
        // Normalized grid: q=(0.0, 0.0) -> p=(0.9, 0.0), z=(0.05, 0.5) -> m=(0.65, 0.5)
        loadTemplates()
    }

    // Smooth incoming touch points using a Kalman filter.
    // Restores structural smoothness and removes high-frequency jitter.
    fun applyKalmanFilter(points: List<PointF>): List<PointF> {
        if (points.size < 3) return points
        val smoothed = mutableListOf<PointF>()
        
        // Initial state estimates
        var xEst = points[0].x
        var yEst = points[0].y
        var pX = 1f
        var pY = 1f

        // Process noise covariance
        val q = 0.08f
        // Measurement noise covariance
        val r = 0.6f

        smoothed.add(points[0])

        for (i in 1 until points.size) {
            val pt = points[i]
            
            // Prediction
            pX += q
            pY += q

            // Measurement update
            val kX = pX / (pX + r)
            val kY = pY / (pY + r)

            xEst += kX * (pt.x - xEst)
            yEst += kY * (pt.y - yEst)

            pX *= (1f - kX)
            pY *= (1f - kY)

            smoothed.add(PointF(xEst, yEst))
        }
        return smoothed
    }

    // Dynamic Time Warping (DTW) to compare sequence of points.
    // Aligns two trajectories of different lengths and calculates similarity.
    fun computeDTWSimilarity(seqA: List<PointF>, seqB: List<PointF>): Float {
        val n = seqA.size
        val m = seqB.size
        if (n == 0 || m == 0) return 0f

        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f

        for (i in 1..n) {
            for (j in 1..m) {
                val dist = distance(seqA[i - 1], seqB[j - 1])
                val prevMin = minOf(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
                dtw[i][j] = dist + if (prevMin == Float.MAX_VALUE) 0f else prevMin
            }
        }

        val totalCost = dtw[n][m]
        val maxLen = maxOf(n, m)
        if (maxLen == 0) return 0f
        
        // Convert distance/cost to normalized similarity score [0..1]
        val avgCost = totalCost / maxLen
        return (1.0f / (1.0f + avgCost)).coerceIn(0f, 1f)
    }

    fun classify(rawPoints: List<PointF>): Pair<String, Float>? {
        if (rawPoints.size < 4) return null
        
        // Step 1: Smooth points
        val smoothed = applyKalmanFilter(rawPoints)
        
        // Step 2: Normalize scale & translation to match template coordinates
        val normalized = normalizePoints(smoothed)

        var bestMatch: String? = null
        var bestScore = 0f

        for ((word, template) in templates) {
            val score = computeDTWSimilarity(normalized, template)
            if (score > bestScore) {
                bestScore = score
                bestMatch = word
            }
        }

        return if (bestScore >= DTW_MATCH_THRESHOLD) {
            bestMatch?.let { it to bestScore }
        } else {
            null
        }
    }

    private fun normalizePoints(points: List<PointF>): List<PointF> {
        if (points.isEmpty()) return points
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (pt in points) {
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }

        val rangeX = (maxX - minX).coerceAtLeast(1f)
        val rangeY = (maxY - minY).coerceAtLeast(1f)

        return points.map { pt ->
            PointF((pt.x - minX) / rangeX, (pt.y - minY) / rangeY)
        }
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun loadTemplates() {
        // Define simple QWERTY key map normalized positions
        val keyPos = mapOf(
            'q' to PointF(0.0f, 0.0f), 'w' to PointF(0.1f, 0.0f), 'e' to PointF(0.2f, 0.0f),
            'r' to PointF(0.3f, 0.0f), 't' to PointF(0.4f, 0.0f), 'y' to PointF(0.5f, 0.0f),
            'u' to PointF(0.6f, 0.0f), 'i' to PointF(0.7f, 0.0f), 'o' to PointF(0.8f, 0.0f),
            'p' to PointF(0.9f, 0.0f),
            'a' to PointF(0.025f, 0.25f), 's' to PointF(0.125f, 0.25f), 'd' to PointF(0.225f, 0.25f),
            'f' to PointF(0.325f, 0.25f), 'g' to PointF(0.425f, 0.25f), 'h' to PointF(0.525f, 0.25f),
            'j' to PointF(0.625f, 0.25f), 'k' to PointF(0.725f, 0.25f), 'l' to PointF(0.825f, 0.25f),
            'z' to PointF(0.05f, 0.5f), 'x' to PointF(0.15f, 0.5f), 'c' to PointF(0.25f, 0.5f),
            'v' to PointF(0.35f, 0.5f), 'b' to PointF(0.45f, 0.5f), 'n' to PointF(0.55f, 0.5f),
            'm' to PointF(0.65f, 0.5f)
        )

        // Interpolate templates for common words
        val words = listOf(
            "the", "and", "you", "that", "have", "for", "not", "with", "this", "but", "from",
            "they", "we", "say", "her", "she", "or", "an", "will", "my", "one", "all", "would",
            "there", "what", "so", "up", "out", "if", "about", "who", "get", "which", "go", "me"
        )

        for (word in words) {
            val pathPoints = mutableListOf<PointF>()
            for (char in word) {
                keyPos[char]?.let { pathPoints.add(it) }
            }
            templates[word] = normalizePoints(pathPoints)
        }
    }
}
