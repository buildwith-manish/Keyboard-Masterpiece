package com.keyboardmasterpiece.ime

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.accessibility.AccessibilityNodeInfo
import com.keyboardmasterpiece.engine.*
import com.keyboardmasterpiece.nativebridge.NativeGestureBridge
import com.keyboardmasterpiece.input.GestureController
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// PRODUCTION-GRADE OPTIMIZED KeyboardView
// FIX: HIGH-001 -- Pre-allocated Paints, cached colors, themeDirty flag, postInvalidateOnAnimation, dirty rect invalidation
// FIX: HIGH-004 -- Multi-touch support with SparseArray
// FIX: MED-001 -- Cached dp values via MetricsCache
// FIX: MED-002 -- Consistent SUGGESTION_HEIGHT_DP constant
// FIX: MED-003 -- Visual feedback for suggestion taps
// FIX: MED-004 -- Backspace repeat interval 50ms
// FIX: LOW-003 -- Removed System.gc() from onLowMemory
// FIX: LOW-006 -- ThemeConfig data class for all color values
// FIX: BUG-004 -- Separate pre-allocated paints for key types instead of mutating shared keyPaint
// FIX: BUG-011 -- Reset state flags in onDetachedFromWindow before removing handler callbacks
// FIX: FINAL-007 -- Dedicated pre-allocated previewPopupPaint (no mutation in drawPreview)
// FIX: FINAL-009 -- Accessibility: announce key presses to TalkBack
class KeyboardView(context: Context) : View(context) {
    interface Listener {
        fun onKey(key: KeyboardKey)
        fun onLongPress(key: KeyboardKey)
        fun onSwipeWord(word: String)
        fun onSpaceDrag(deltaChars: Int)
        fun onBackspaceDrag(deltaWords: Int)
        fun onSuggestion(text: String)
        // TASK3 -- Callback when a file is picked from the keyboard
        fun onFilePicked(uri: android.net.Uri, mimeType: String)
    }

    var listener: Listener? = null
    var preferences: UserPreferences? = null
    var layoutMode: LayoutMode = LayoutMode.FULL
        set(value) {
            if (field != value) {
                field = value
                needsLayout = true
                postInvalidateOnAnimation() // FIX: HIGH-001 -- Use postInvalidateOnAnimation
            }
        }

    private var rows: List<List<KeyboardKey>> = emptyList()
    private var panel = Panel.QWERTY
    private var shifted = false
    private var caps = false
    private var incognito = false
    private var suggestions = listOf<String>()

    // FIX: HIGH-001 -- All Paints pre-allocated in init block, NEVER in onDraw
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val suggestionHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG) // FIX: MED-003

    // FIX: BUG-004 -- Separate pre-allocated paints for each key type.
    // Instead of mutating keyPaint.color with save/restore, use dedicated paints
    // so there is no risk of corrupting a shared paint's state across draw calls.
    private val normalKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val actionKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spaceKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // FIX: FINAL-007 -- Dedicated pre-allocated paint for popup preview (no mutation in drawPreview)
    private val previewPopupPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val path = Path()
    private val gesture = FloatArray(512)
    private var gestureCount = 0

    // FIX: HIGH-004 -- Multi-touch support: track per-pointer state
    private data class PointerState(
        var downKey: KeyboardKey? = null,
        var downTime: Long = 0L,
        var downX: Float = 0f,
        var downY: Float = 0f
    )
    private val pointerStates = SparseArray<PointerState>()

    // Single primary pointer for gesture tracking (the first finger down)
    private var primaryPointerId = -1
    private var previewKey: KeyboardKey? = null
    private var previewKeyStartTime = 0L
    private val gestureController = GestureController()
    private var lastX1 = 0f
    private var lastY1 = 0f
    private var lastX2 = 0f
    private var lastY2 = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var repeatBackspace = false
    private var hasDragged = false

    // FIX: BUG-REPEAT -- Saved reference to the delayed backspace-repeat-start Runnable
    // so it can be cancelled in handleUp. Previously, an anonymous Runnable was posted
    // but never removed, causing an unstoppable backspace repeat after a quick tap.
    private var backspaceRepeatStartRunnable: Runnable? = null
    private var needsLayout = true
    private var cachedRowHeight = 0f
    private var cachedStartX = 0f
    private var cachedUsableWidth = 0f

    // FIX: MED-003 -- Pressed suggestion index for visual feedback
    private var pressedSuggestionIndex = -1

    // Feature 5: Drag handle for keyboard resize
    private var dragHandleRect = RectF()
    private var isDraggingHandle = false
    private var dragStartY = 0f
    private var dragStartDelta = 0
    private val dragHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    companion object {
        private const val SUGGESTION_HEIGHT_DP = 38
        private const val BACKSPACE_REPEAT_INTERVAL_MS = 50L // FIX: MED-004 -- was 45ms, now 50ms
        // Feature 5: Drag handle constraints
        private const val DRAG_HANDLE_WIDTH_DP = 80
        private const val DRAG_HANDLE_HEIGHT_DP = 8
        private const val MIN_KEY_HEIGHT_DELTA = -8
        private const val MAX_KEY_HEIGHT_DELTA = 40
        private const val DRAG_SENSITIVITY = 1.5f // pixels to delta scaling
    }

    // FIX: HIGH-001 -- themeDirty flag; only update paint colors when theme changes
    private var themeDirty = true
    private var lastThemeIndex = -1 // Feature 4: Track theme index instead of boolean

    // FIX: LOW-006 -- ThemeConfig data class with all color values
    data class ThemeConfig(
        val bgColor: Int,
        val keyColor: Int,
        val actionKeyColor: Int,
        val spaceKeyColor: Int,
        val borderColor: Int,
        val textColor: Int,
        val accentColor: Int,
        val pathColor: Int,
        val popupColor: Int,
        val suggestionHighlightColor: Int
    )

    // Feature 4: Apply a ThemePalette.Theme to the internal ThemeConfig.
    fun applyTheme(theme: ThemePalette.Theme) {
        currentTheme = ThemeConfig(
            bgColor = theme.bgColor,
            keyColor = theme.keyColor,
            actionKeyColor = theme.actionKeyColor,
            spaceKeyColor = theme.spaceKeyColor,
            borderColor = theme.borderColor,
            textColor = theme.textColor,
            accentColor = theme.accentColor,
            pathColor = theme.pathColor,
            popupColor = theme.popupColor,
            suggestionHighlightColor = theme.suggestionHighlightColor
        )
    }

    private var currentTheme: ThemeConfig = ThemeConfig(
        bgColor = Color.rgb(238, 241, 246),
        keyColor = Color.WHITE,
        actionKeyColor = Color.rgb(218, 224, 235),
        spaceKeyColor = Color.WHITE,
        borderColor = Color.rgb(210, 215, 225),
        textColor = Color.rgb(24, 27, 32),
        accentColor = Color.rgb(79, 124, 255),
        pathColor = Color.argb(160, 79, 124, 255),
        popupColor = Color.WHITE,
        suggestionHighlightColor = Color.argb(40, 79, 124, 255)
    )
        set(value) {
            if (field != value) {
                field = value
                themeDirty = true
            }
        }

    // FIX: MED-001 -- MetricsCache: cache all dp values in onSizeChanged
    private inner class MetricsCache {
        var gap: Float = 0f
        var suggestionHeight: Float = 0f
        var keyRadius: Float = 0f
        var textLarge: Float = 0f
        var textSmall: Float = 0f
        var textSuggestion: Float = 0f
        var textPreview: Float = 0f
        var altHintOffsetX: Float = 0f
        var altHintOffsetY: Float = 0f
        var previewWidth: Float = 0f
        var previewHeight: Float = 0f
        var previewRadius: Float = 0f
        var previewBottomPad: Float = 0f
        var previewTopPad: Float = 0f
        var spaceDragThreshold: Float = 0f
        var spaceDragUnit: Float = 0f
        var longPressCancelDistance: Float = 0f
        var swipeThreshold: Float = 0f
        var density: Float = 0f

        fun recalculate() {
            density = resources.displayMetrics.density
            gap = dpF(4)
            suggestionHeight = dpF(SUGGESTION_HEIGHT_DP)
            keyRadius = dpF(9)
            textLarge = dpF(21)
            textSmall = dpF(15)
            textSuggestion = dpF(16)
            textPreview = dpF(32)
            altHintOffsetX = dpF(9)
            altHintOffsetY = dpF(14)
            previewWidth = dpF(32)
            previewHeight = dpF(68)
            previewBottomPad = dpF(8)
            previewTopPad = dpF(8)
            previewRadius = dpF(12)
            spaceDragThreshold = dpF(18)
            spaceDragUnit = dpF(28)
            longPressCancelDistance = dpF(22)
            swipeThreshold = dpF(65)
        }
    }

    private val metrics = MetricsCache()

    private val longPress = Runnable {
        val state = pointerStates.get(primaryPointerId) ?: return@Runnable
        state.downKey?.let {
            previewKey = it
            previewKeyStartTime = System.currentTimeMillis()
            listener?.onLongPress(it)
            if (it.code == KeyCodes.BACKSPACE) startBackspaceRepeat()
        }
        postInvalidateOnAnimation() // FIX: HIGH-001
    }

    // FIX: MED-004 -- Repeat interval 50ms instead of 45ms
    // FIX: BUG-REPEAT -- Added safety check: only repeat if a pointer is still down on backspace.
    // This prevents orphaned repeats if the cancellation logic somehow fails.
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (repeatBackspace && primaryPointerId >= 0) {
                listener?.onKey(KeyboardKey("", code = KeyCodes.BACKSPACE))
                handler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS)
            } else {
                repeatBackspace = false
            }
        }
    }

    init {
        isHapticFeedbackEnabled = false
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(Color.TRANSPARENT)
        // FIX: FINAL-009 -- Accessibility: announce key presses to TalkBack
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = "Keyboard input area"
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            }
        }
        contentDescription = "Keyboard input area"
    }

    fun setKeys(keys: List<List<KeyboardKey>>, p: Panel, sh: Boolean, ca: Boolean, incog: Boolean) {
        rows = keys
        panel = p
        shifted = sh
        caps = ca
        incognito = incog
        needsLayout = true
        requestLayout()
        postInvalidateOnAnimation() // FIX: HIGH-001
    }

    fun setSuggestions(list: List<String>) {
        suggestions = if (list.isEmpty()) {
            listOf(if (incognito) "Incognito mode" else "")
        } else list.take(3)
        postInvalidateOnAnimation() // FIX: HIGH-001
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        needsLayout = true
        metrics.recalculate() // FIX: MED-001 -- Cache dp values on size change
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val delta = preferences?.keyHeightDelta ?: 8
        val rowH = dp(42 + delta)
        val totalRows = rows.size + 1 // +1 for suggestions/candidates
        val h = rowH * totalRows + paddingTop + paddingBottom
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec)))
    }

    override fun onDraw(c: Canvas) {
        if (needsLayout) performLayout()

        // Feature 4: Use ThemePalette instead of light/dark boolean
        val idx = preferences?.themeIndex ?: 0
        if (idx != lastThemeIndex) {
            lastThemeIndex = idx
            themeDirty = true
        }
        if (themeDirty) {
            val prefs = preferences
            if (prefs != null) {
                applyTheme(ThemePalette.current(prefs))
            }
            updatePaints()
            themeDirty = false
        }

        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawSuggestions(c)
        drawDragHandle(c) // Feature 5: Draw drag handle
        for (row in rows) for (key in row) drawKey(c, key)
        if (!path.isEmpty) c.drawPath(path, pathPaint)
        previewKey?.let { drawPreview(c, it) }
    }

    private fun performLayout() {
        val gap = metrics.gap
        val top = metrics.suggestionHeight // FIX: MED-002 -- Use consistent SUGGESTION_HEIGHT_DP
        val availableHeight = height - top
        cachedRowHeight = (availableHeight / max(1, rows.size)).toFloat()
        cachedUsableWidth = when (layoutMode) {
            LayoutMode.ONE_HANDED_LEFT, LayoutMode.ONE_HANDED_RIGHT -> width * 0.78f
            LayoutMode.FLOATING -> width * 0.72f
            else -> width.toFloat()
        }
        cachedStartX = when (layoutMode) {
            LayoutMode.ONE_HANDED_RIGHT -> width - cachedUsableWidth
            LayoutMode.FLOATING -> (width - cachedUsableWidth) / 2f
            else -> 0f
        }

        val totalGap = gap * (rows.firstOrNull()?.size?.plus(1) ?: 2)

        // Feature 5: Position the drag handle at the top of the keyboard area
        val handleW = dpF(DRAG_HANDLE_WIDTH_DP)
        val handleH = dpF(DRAG_HANDLE_HEIGHT_DP)
        dragHandleRect = RectF(width / 2f - handleW / 2f, top, width / 2f + handleW / 2f, top + handleH)

        rows.forEachIndexed { ri, row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            var x = cachedStartX + gap
            val y = top + ri * cachedRowHeight + gap
            val keyH = cachedRowHeight - gap * 2

            row.forEachIndexed { idx, key ->
                val kw = (cachedUsableWidth - totalGap) * key.weight / totalWeight
                if (layoutMode == LayoutMode.SPLIT && ri < rows.lastIndex && row.size > 6 && idx == row.size / 2) {
                    x += width * 0.12f
                }
                @Suppress("DEPRECATION") // FIX: INFO-003 -- Still using rect for backward compat
                key.rect.set(x, y, x + kw, y + keyH)
                x += kw + gap
            }
        }
        needsLayout = false
    }

    // FIX: HIGH-001 -- updatePaints reads from ThemeConfig, only called when themeDirty.
// FIX: LOW-006 -- All colors come from ThemeConfig, no hardcoded colors.
// FIX: BUG-004 -- Also update the separate key-type paints.
// FIX: FINAL-007 -- Also update dedicated previewPopupPaint from theme.
    private fun updatePaints() {
        val theme = currentTheme
        bgPaint.color = theme.bgColor
        keyPaint.color = theme.keyColor
        borderPaint.color = theme.borderColor
        textPaint.color = theme.textColor
        smallPaint.color = theme.accentColor
        pathPaint.color = theme.pathColor
        suggestionHighlightPaint.color = theme.suggestionHighlightColor

        // FIX: BUG-004 -- Update separate key-type paints from theme
        normalKeyPaint.color = theme.keyColor
        actionKeyPaint.color = theme.actionKeyColor
        spaceKeyPaint.color = theme.spaceKeyColor

        // FIX: FINAL-007 -- Update dedicated preview paint from theme
        previewPopupPaint.color = theme.popupColor
    }

    // FIX: MED-002 -- Use SUGGESTION_HEIGHT_DP consistently.
// FIX: MED-003 -- Draw highlight for pressed suggestion.
// FIX: MED-001 -- Use cached metrics instead of calling dp() in onDraw.
// FIX: HIGH-001 -- No paint color reassignment in draw methods.
    private fun drawSuggestions(c: Canvas) {
        val h = metrics.suggestionHeight
        val segmentWidth = width / 3f
        textPaint.textSize = metrics.textSuggestion
        textPaint.typeface = Typeface.DEFAULT_BOLD

        for (i in 0 until 3) {
            // FIX: MED-003 -- Draw highlight for pressed suggestion
            if (i == pressedSuggestionIndex) {
                c.drawRect(segmentWidth * i, 0f, segmentWidth * (i + 1), h, suggestionHighlightPaint)
            }

            val s = suggestions.getOrNull(i).orEmpty()
            if (s.isNotEmpty()) {
                val x = segmentWidth * i + segmentWidth / 2
                val baseline = h / 2 - (textPaint.ascent() + textPaint.descent()) / 2
                c.drawText(s, x, baseline, textPaint)
            }
        }
        textPaint.typeface = Typeface.DEFAULT
    }

    // FIX: BUG-004 -- Use separate pre-allocated paints for each key type instead of
// mutating the shared keyPaint.color with save/restore. This eliminates the risk
// of corrupting the paint state if draw calls overlap or are interrupted.
// FIX: MED-001 -- Use cached metrics.
// FIX: LOW-006 -- All colors from ThemeConfig.
    private fun drawKey(c: Canvas, key: KeyboardKey) {
        @Suppress("DEPRECATION")
        val r = key.rect
        val rad = metrics.keyRadius

        // FIX: BUG-004 -- Select the correct pre-allocated paint per key type
        val paint = when {
            key.isAction -> actionKeyPaint
            key.code == KeyCodes.SPACE -> spaceKeyPaint
            else -> normalKeyPaint
        }
        c.drawRoundRect(r, rad, rad, paint)
        c.drawRoundRect(r, rad, rad, borderPaint)

        val fontSize = if (key.label.length > 3) metrics.textSmall else metrics.textLarge
        textPaint.textSize = fontSize
        val y = r.centerY() - (textPaint.ascent() + textPaint.descent()) / 2 + 1f
        c.drawText(key.label, r.centerX(), y, textPaint)

        if (key.alt.isNotEmpty()) {
            smallPaint.textSize = dp(10).toFloat()
            c.drawText(key.alt.first().toString(), r.right - metrics.altHintOffsetX, r.top + metrics.altHintOffsetY, smallPaint)
        }
    }

    // FIX: HIGH-001 -- No paint allocation in drawPreview.
// FIX: MED-001 -- Use cached metrics.
// FIX: FINAL-007 -- Use dedicated previewPopupPaint instead of mutating popupPaint.
    private fun drawPreview(c: Canvas, key: KeyboardKey) {
        if (key.label.length > 3) return
        @Suppress("DEPRECATION")
        val r = key.rect

        val pr = RectF(
            r.centerX() - metrics.previewWidth,
            r.top - metrics.previewHeight,
            r.centerX() + metrics.previewWidth,
            r.top - metrics.previewBottomPad
        )
        
        val elapsed = System.currentTimeMillis() - previewKeyStartTime
        val scale = (elapsed.toFloat() / 150f).coerceIn(0.8f, 1.1f)

        c.save()
        c.scale(scale, scale, pr.centerX(), pr.bottom)

        // FIX: FINAL-007 -- Use dedicated previewPopupPaint (no save/restore mutation)
        c.drawRoundRect(pr, metrics.previewRadius, metrics.previewRadius, previewPopupPaint)
        c.drawRoundRect(pr, metrics.previewRadius, metrics.previewRadius, borderPaint)

        textPaint.textSize = metrics.textPreview
        val baseline = pr.centerY() - (textPaint.ascent() + textPaint.descent()) / 2
        c.drawText(key.label, pr.centerX(), baseline, textPaint)

        c.restore()

        if (elapsed < 150) {
            postInvalidateOnAnimation()
        }
    }

    // FIX: HIGH-004 -- Multi-touch support.
// Track multiple pointers via SparseArray<PointerState>.
// Handle ACTION_POINTER_DOWN and ACTION_POINTER_UP.
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(e, 0)
            MotionEvent.ACTION_POINTER_DOWN -> { // FIX: HIGH-004
                val idx = e.actionIndex
                handleDown(e, idx)
            }
            MotionEvent.ACTION_MOVE -> handleMove(e)
            MotionEvent.ACTION_UP -> handleUp(e, 0)
            MotionEvent.ACTION_POINTER_UP -> { // FIX: HIGH-004
                val idx = e.actionIndex
                handleUp(e, idx)
            }
            MotionEvent.ACTION_CANCEL -> handleCancel()
        }
        return true
    }

    // FIX: HIGH-004 -- Handle pointer down for multi-touch.
// FIX: MED-003 -- Detect suggestion area taps in handleDown.
    private fun handleDown(e: MotionEvent, pointerIndex: Int) {
        val pointerId = e.getPointerId(pointerIndex)
        val x = e.getX(pointerIndex)
        val y = e.getY(pointerIndex)

        // Feature 5: Check if touch is on the drag handle
        if (dragHandleRect.contains(x, y)) {
            isDraggingHandle = true
            dragStartY = y
            dragStartDelta = preferences?.keyHeightDelta ?: 8
            handler.removeCallbacks(longPress)
            postInvalidateOnAnimation()
            return
        }

        val state = PointerState(
            downKey = hitTest(x, y),
            downTime = System.currentTimeMillis(),
            downX = x,
            downY = y
        )
        pointerStates.put(pointerId, state)

        // First pointer is the primary for gesture tracking
        if (primaryPointerId == -1) {
            primaryPointerId = pointerId
            previewKey = state.downKey
            previewKeyStartTime = System.currentTimeMillis()
            gestureCount = 0
            hasDragged = false
            path.reset()
            addGesturePoint(x, y)

            state.downKey?.let { key ->
                handler.postDelayed(longPress, 280)
                if (key.code == KeyCodes.BACKSPACE) {
                    // FIX: BUG-REPEAT -- Save the Runnable reference so we can cancel it on release.
                    // Previously, an anonymous Runnable was posted but never cancelled,
                    // causing the backspace repeat to start even after a quick tap-release.
                    val r = Runnable { startBackspaceRepeat() }
                    backspaceRepeatStartRunnable = r
                    handler.postDelayed(r, 480)
                }
            }
        }

        // FIX: MED-003 -- Detect suggestion area tap
        if (y < metrics.suggestionHeight) {
            val idx = (x / (width / 3f)).toInt().coerceIn(0, 2)
            pressedSuggestionIndex = idx
        } else {
            pressedSuggestionIndex = -1
        }

        postInvalidateOnAnimation() // FIX: HIGH-001
    }

    private fun handleMove(e: MotionEvent) {
        // Feature 5: Handle drag handle resize
        if (isDraggingHandle) {
            val i = e.findPointerIndex(0)
            if (i < 0) return
            val y = e.getY(i)
            val dy = dragStartY - y // Dragging up = positive delta (bigger keyboard)
            val newDelta = (dragStartDelta + (dy / DRAG_SENSITIVITY).toInt())
                .coerceIn(MIN_KEY_HEIGHT_DELTA, MAX_KEY_HEIGHT_DELTA)
            preferences?.keyHeightDelta = newDelta
            needsLayout = true
            requestLayout()
            postInvalidateOnAnimation()
            return
        }

        val state = pointerStates.get(primaryPointerId) ?: return
        val i = e.findPointerIndex(primaryPointerId)
        if (i < 0) return
        val x = e.getX(i)
        val y = e.getY(i)
        addGesturePoint(x, y)

        state.downKey?.let { dk ->
            if (dk.code == KeyCodes.SPACE && kotlin.math.abs(x - state.downX) > metrics.spaceDragThreshold) {
                val delta = ((x - state.downX) / metrics.spaceDragUnit).toInt()
                listener?.onSpaceDrag(delta)
                state.downX = x
                hasDragged = true
            } else if (dk.code == KeyCodes.BACKSPACE && (state.downX - x) > metrics.spaceDragThreshold) {
                // Cancel regular backspace repeating when sliding to delete
                if (repeatBackspace || backspaceRepeatStartRunnable != null) {
                    repeatBackspace = false
                    handler.removeCallbacks(repeatRunnable)
                    backspaceRepeatStartRunnable?.let { handler.removeCallbacks(it) }
                    backspaceRepeatStartRunnable = null
                }
                // Gboard-style slide left on Backspace to delete words
                val delta = ((state.downX - x) / metrics.spaceDragUnit).toInt()
                if (delta > 0) {
                    listener?.onBackspaceDrag(delta)
                    state.downX = x
                    hasDragged = true
                }
            }
        }

        if (distance(x, y, state.downX, state.downY) > metrics.longPressCancelDistance) {
            handler.removeCallbacks(longPress)
            previewKey = null
        }
        postInvalidateOnAnimation() // FIX: HIGH-001
    }

    // FIX: HIGH-004 -- Handle pointer up for multi-touch.
// FIX: MED-002 -- Use SUGGESTION_HEIGHT_DP consistently.
// FIX: MED-003 -- Clear pressed suggestion highlight on up.
// FIX: FINAL-009 -- Announce key press for TalkBack accessibility.
    private fun handleUp(e: MotionEvent, pointerIndex: Int) {
        // Feature 5: End drag handle resize
        if (isDraggingHandle) {
            isDraggingHandle = false
            // Delta is already saved to preferences on each move, just clean up
            postInvalidateOnAnimation()
            return
        }

        val pointerId = e.getPointerId(pointerIndex)
        val state = pointerStates.get(pointerId) ?: return
        val x = e.getX(pointerIndex)
        val y = e.getY(pointerIndex)

        if (pointerId == primaryPointerId) {
            handler.removeCallbacks(longPress)
            // FIX: BUG-REPEAT -- Cancel the delayed backspace-repeat-start Runnable.
            // Without this, a quick tap on backspace would leave an orphaned Runnable
            // that starts an unstoppable backspace repeat 480ms later.
            backspaceRepeatStartRunnable?.let { handler.removeCallbacks(it) }
            backspaceRepeatStartRunnable = null
            repeatBackspace = false
            handler.removeCallbacks(repeatRunnable)

            val finalKey = hitTest(x, y) ?: state.downKey
            val movedDistance = distance(x, y, state.downX, state.downY)

            when {
                movedDistance > metrics.swipeThreshold && panel == Panel.QWERTY && state.downKey?.code != KeyCodes.SPACE && state.downKey?.code != KeyCodes.BACKSPACE -> {
                    val pointsList = mutableListOf<PointF>()
                    for (idx in 0 until gestureCount) {
                        pointsList.add(PointF(gesture[idx * 2], gesture[idx * 2 + 1]))
                    }
                    val dtwMatch = gestureController.classify(pointsList)
                    val word = dtwMatch?.first ?: NativeGestureBridge.classify(gesture, gestureCount)
                    listener?.onSwipeWord(word)
                }
                y < metrics.suggestionHeight -> { // FIX: MED-002 -- Use consistent SUGGESTION_HEIGHT_DP
                    val idx = (x / (width / 3f)).toInt().coerceIn(0, 2)
                    listener?.onSuggestion(suggestions.getOrNull(idx).orEmpty())
                }
                finalKey != null -> {
                    val shouldTrigger = !hasDragged || (finalKey.code != KeyCodes.SPACE && finalKey.code != KeyCodes.BACKSPACE)
                    if (shouldTrigger) {
                        listener?.onKey(finalKey)
                    }
                }
            }

            // FIX: FINAL-009 -- Announce key press for TalkBack accessibility
            finalKey?.let { key ->
                if (key.label.isNotBlank()) {
                    announceForAccessibility(key.label)
                }
            }

            previewKey = null
            path.reset()
            primaryPointerId = -1

            // If another pointer is still down, promote it to primary
            for (i in 0 until pointerStates.size()) {
                val otherId = pointerStates.keyAt(i)
                if (otherId != pointerId) {
                    primaryPointerId = otherId
                    break
                }
            }
        }

        pointerStates.remove(pointerId)

        // FIX: MED-003 -- Clear suggestion highlight
        pressedSuggestionIndex = -1

        postInvalidateOnAnimation() // FIX: HIGH-001
    }

    private fun handleCancel() {
        handler.removeCallbacks(longPress)
        // FIX: BUG-REPEAT -- Cancel the delayed backspace-repeat-start Runnable on cancel too.
        backspaceRepeatStartRunnable?.let { handler.removeCallbacks(it) }
        backspaceRepeatStartRunnable = null
        repeatBackspace = false
        handler.removeCallbacks(repeatRunnable)
        isDraggingHandle = false // Feature 5
        pointerStates.clear()
        primaryPointerId = -1
        previewKey = null
        pressedSuggestionIndex = -1
        path.reset()
        postInvalidateOnAnimation()
    }

    // FIX: HIGH-001 -- Single-key invalidation method for dirty rect invalidation.
    fun invalidateKey(key: KeyboardKey) {
        @Suppress("DEPRECATION")
        val r = key.rect
        postInvalidateOnAnimation(
            r.left.toInt() - 2,
            r.top.toInt() - 2,
            (r.right + 2).toInt(),
            (r.bottom + 2).toInt()
        )
    }

    private fun hitTest(x: Float, y: Float): KeyboardKey? {
        for (row in rows) {
            for (key in row) {
                @Suppress("DEPRECATION")
                if (key.rect.contains(x, y)) return key
            }
        }
        return null
    }

    private fun addGesturePoint(x: Float, y: Float) {
        if (gestureCount < 250) {
            val smoothedX = if (gestureCount == 0) x else (x + lastX1 + lastX2) / 3f
            val smoothedY = if (gestureCount == 0) y else (y + lastY1 + lastY2) / 3f

            lastX2 = lastX1
            lastY2 = lastY1
            lastX1 = x
            lastY1 = y

            val idx = gestureCount * 2
            gesture[idx] = smoothedX
            gesture[idx + 1] = smoothedY
            if (gestureCount == 0) path.moveTo(smoothedX, smoothedY) else path.lineTo(smoothedX, smoothedY)
            gestureCount++
        }
    }

    private fun startBackspaceRepeat() {
        repeatBackspace = true
        handler.post(repeatRunnable)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    // FIX: MED-001 -- Float version of dp for metrics cache.
    private fun dpF(v: Int): Float = v * resources.displayMetrics.density

    // FIX: BUG-011 -- Reset state flags BEFORE removing handler callbacks.
// If we remove callbacks first, pending runnables that reference these flags
// could still be mid-execution or the state could be inconsistent. Resetting
// the flags first ensures a clean state when the view detaches.
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // FIX: BUG-011 -- Reset state flags before removing handler callbacks
        repeatBackspace = false
        primaryPointerId = -1
        pressedSuggestionIndex = -1
        isDraggingHandle = false // Feature 5
        backspaceRepeatStartRunnable = null // FIX: BUG-REPEAT -- Clear reference on detach

        handler.removeCallbacksAndMessages(null)
        path.reset()
        previewKey = null
        pointerStates.clear()
    }

    // FIX: LOW-003 -- Removed System.gc() call. Just clear the suggestions list.
// Calling System.gc() is discouraged; the VM manages memory and explicit GC
// requests can cause jank and unpredictable pauses.
    fun onLowMemory() {
        suggestions = emptyList()
    }

    // Feature 5: Draw the drag handle bar at the top of the keyboard.
// Draws a small centered rounded rectangle that users can drag to resize the keyboard.
    private fun drawDragHandle(c: Canvas) {
        val theme = currentTheme
        dragHandlePaint.color = theme.borderColor
        dragHandlePaint.alpha = if (isDraggingHandle) 200 else 120
        val rad = dpF(4)
        c.drawRoundRect(dragHandleRect, rad, rad, dragHandlePaint)
    }
}
