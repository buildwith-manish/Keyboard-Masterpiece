package com.keyboardmasterpiece.ime

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.keyboardmasterpiece.engine.*
import com.keyboardmasterpiece.nativebridge.NativeGestureBridge
import kotlin.math.max
import kotlin.math.min

class KeyboardView(context: Context) : View(context) {
    interface Listener { fun onKey(key: KeyboardKey); fun onLongPress(key: KeyboardKey); fun onSwipeWord(word: String); fun onSpaceDrag(deltaChars: Int); fun onSuggestion(text: String) }
    var listener: Listener? = null
    var preferences: UserPreferences? = null
    var layoutMode: LayoutMode = LayoutMode.FULL
    private var rows: List<List<KeyboardKey>> = emptyList()
    private var panel = Panel.QWERTY
    private var shifted = false
    private var caps = false
    private var incognito = false
    private var suggestions = listOf("the", "and", "you")
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 79, 124, 255); style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val gesture = FloatArray(512)
    private var gestureCount = 0
    private var downKey: KeyboardKey? = null
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var activePointer = -1
    private var previewKey: KeyboardKey? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repeatBackspace = false
    private val longPress = Runnable { downKey?.let { previewKey = it; listener?.onLongPress(it); if (it.code == KeyCodes.BACKSPACE) startBackspaceRepeat() }; invalidate() }
    private val repeatRunnable = object : Runnable { override fun run() { if (repeatBackspace) { listener?.onKey(KeyboardKey("", code = KeyCodes.BACKSPACE)); handler.postDelayed(this, 44) } } }

    init { isHapticFeedbackEnabled = false; setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun setKeys(keys: List<List<KeyboardKey>>, p: Panel, sh: Boolean, ca: Boolean, incog: Boolean) { rows = keys; panel = p; shifted = sh; caps = ca; incognito = incog; requestLayout(); invalidate() }
    fun setSuggestions(list: List<String>) { suggestions = if (list.isEmpty()) listOf(if (incognito) "Incognito" else "") else list; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val delta = preferences?.keyHeightDelta ?: 8
        val rowH = dp(42 + delta)
        val h = rowH * (rows.size + 1) + paddingTop + paddingBottom
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onDraw(c: Canvas) {
        val dark = preferences?.darkTheme == true
        bgPaint.color = if (dark) Color.rgb(21,23,28) else Color.rgb(238,241,246)
        keyPaint.color = if (dark) Color.rgb(42,45,52) else Color.WHITE
        borderPaint.color = if (dark) Color.rgb(68,72,82) else Color.rgb(210,215,225)
        textPaint.color = if (dark) Color.WHITE else Color.rgb(24,27,32)
        smallPaint.color = Color.rgb(79,124,255)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawSuggestions(c, dark)
        layoutKeys()
        for (row in rows) for (key in row) drawKey(c, key, dark)
        if (!path.isEmpty) c.drawPath(path, pathPaint)
        previewKey?.let { drawPreview(c, it, dark) }
    }

    private fun drawSuggestions(c: Canvas, dark: Boolean) {
        val h = dp(38).toFloat(); val w = width / 3f
        textPaint.textSize = dp(15 + (preferences?.fontSizeDelta ?: 4) / 2).toFloat(); textPaint.typeface = Typeface.DEFAULT_BOLD
        for (i in 0..2) {
            val s = suggestions.getOrNull(i).orEmpty(); if (s.isEmpty()) continue
            c.drawText(s, w * i + w / 2, h / 2 - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        }
        textPaint.typeface = Typeface.DEFAULT
    }

    private fun drawKey(c: Canvas, key: KeyboardKey, dark: Boolean) {
        val r = key.rect; val rad = dp(9).toFloat()
        keyPaint.color = when { key.isAction -> if (dark) Color.rgb(55,59,68) else Color.rgb(218,224,235); key.code == KeyCodes.SPACE -> if (dark) Color.rgb(48,52,60) else Color.WHITE; else -> if (dark) Color.rgb(42,45,52) else Color.WHITE }
        c.drawRoundRect(r, rad, rad, keyPaint); c.drawRoundRect(r, rad, rad, borderPaint)
        val fs = if (key.label.length > 3) 14 else 20
        textPaint.textSize = dp(fs + ((preferences?.fontSizeDelta ?: 4) - 4) / 2).toFloat()
        val y = r.centerY() - (textPaint.ascent() + textPaint.descent()) / 2
        c.drawText(key.label, r.centerX(), y, textPaint)
        if (key.alt.isNotEmpty()) { smallPaint.textSize = dp(9).toFloat(); c.drawText(key.alt.first().toString(), r.right - dp(10), r.top + dp(12), smallPaint) }
    }

    private fun drawPreview(c: Canvas, key: KeyboardKey, dark: Boolean) {
        if (key.label.length > 2) return
        val r = key.rect; popupPaint.color = if (dark) Color.rgb(64,68,78) else Color.WHITE
        val pr = RectF(r.centerX() - dp(28), r.top - dp(58), r.centerX() + dp(28), r.top - dp(6))
        c.drawRoundRect(pr, dp(14).toFloat(), dp(14).toFloat(), popupPaint); c.drawRoundRect(pr, dp(14).toFloat(), dp(14).toFloat(), borderPaint)
        textPaint.textSize = dp(28).toFloat(); c.drawText(key.label, pr.centerX(), pr.centerY() - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
    }

    private fun layoutKeys() {
        val gap = dp(4).toFloat(); val top = dp(40).toFloat(); val rowH = ((height - top - gap) / max(1, rows.size)).toFloat()
        val usable = when (layoutMode) { LayoutMode.ONE_HANDED_LEFT, LayoutMode.ONE_HANDED_RIGHT -> width * .78f; LayoutMode.FLOATING -> width * .72f; LayoutMode.SPLIT, LayoutMode.FULL -> width.toFloat() }
        val startX = when (layoutMode) { LayoutMode.ONE_HANDED_RIGHT -> width - usable; LayoutMode.FLOATING -> (width - usable) / 2f; else -> 0f }
        rows.forEachIndexed { ri, row ->
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat(); var x = startX + gap
            val y = top + ri * rowH + gap; val keyH = rowH - gap * 2
            row.forEachIndexed { idx, key ->
                val kw = (usable - gap * (row.size + 1)) * key.weight / totalWeight
                if (layoutMode == LayoutMode.SPLIT && ri < rows.lastIndex && row.size > 6 && idx == row.size / 2) x += width * .12f
                key.rect.set(x, y, x + kw, y + keyH); x += kw + gap
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = e.getPointerId(0); downX = e.x; downY = e.y; downTime = System.currentTimeMillis(); downKey = hit(e.x, e.y); previewKey = downKey; gestureCount = 0; path.reset(); addGesture(e.x, e.y); downKey?.let { handler.postDelayed(longPress, 360); if (it.code == KeyCodes.BACKSPACE) handler.postDelayed({ startBackspaceRepeat() }, 520) }; invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activePointer); if (i < 0) return true
                val x = e.getX(i); val y = e.getY(i); addGesture(x, y)
                if (downKey?.code == KeyCodes.SPACE && kotlin.math.abs(x - downX) > dp(18)) { listener?.onSpaceDrag(((x - downX) / dp(28)).toInt()); downX = x }
                if (distance(x, y, downX, downY) > dp(20)) { handler.removeCallbacks(longPress); previewKey = null }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress); repeatBackspace = false; handler.removeCallbacks(repeatRunnable)
                val x = e.x; val y = e.y; val key = hit(x, y) ?: downKey
                val moved = distance(x, y, downX, downY) > dp(70)
                if (moved && panel == Panel.QWERTY && downKey?.code != KeyCodes.SPACE) listener?.onSwipeWord(NativeGestureBridge.classify(gesture, gestureCount))
                else if (y < dp(38)) listener?.onSuggestion(suggestions.getOrNull((x / (width / 3f)).toInt()).orEmpty())
                else key?.let { listener?.onKey(it) }
                previewKey = null; path.reset(); invalidate(); return true
            }
        }
        return true
    }

    private fun hit(x: Float, y: Float): KeyboardKey? { for (r in rows) for (k in r) if (k.rect.contains(x, y)) return k; return null }
    private fun addGesture(x: Float, y: Float) { if (gestureCount < 256) { gesture[gestureCount * 2] = x; gesture[gestureCount * 2 + 1] = y; if (gestureCount == 0) path.moveTo(x, y) else path.lineTo(x, y); gestureCount++ } }
    private fun startBackspaceRepeat() { if (downKey?.code == KeyCodes.BACKSPACE) { repeatBackspace = true; handler.post(repeatRunnable) } }
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float { val dx = x1 - x2; val dy = y1 - y2; return kotlin.math.sqrt(dx * dx + dy * dy) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()
}
