package com.erol.glasskeys

import android.inputmethodservice.InputMethodService
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.view.View
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import kotlin.math.max

class GlassKeyboardService : InputMethodService() {
    private var keyboard: GlassKeyboardView? = null
    override fun onCreateInputView(): View {
        keyboard = GlassKeyboardView(this) { action -> handleKey(action) }
        return keyboard!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboard?.invalidate()
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return
        vibrateTap()
        when (key) {
            "⌫" -> ic.deleteSurroundingText(1, 0)
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action)
                else ic.commitText("\n", 1)
            }
            "space" -> ic.commitText(" ", 1)
            "⇧" -> keyboard?.toggleShift()
            "?123" -> keyboard?.toggleSymbols()
            "🌐" -> requestNextInputMethod()
            else -> ic.commitText(key, 1)
        }
    }

    private fun requestNextInputMethod() {
        try { switchToNextInputMethod(false) } catch (_: Exception) { }
    }

    private fun vibrateTap() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(9, 35)) else @Suppress("DEPRECATION") v.vibrate(9)
            }
        } catch (_: Exception) { }
    }
}

private class GlassKeyboardView(context: Context, val onKey: (String) -> Unit) : View(context) {
    private val bg = Paint(3)
    private val keyPaint = Paint(3)
    private val textPaint = Paint(3).apply { color = Color.rgb(36,42,54); textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val rows = listOf(
        listOf("q","w","e","r","t","y","u","ı","o","p","ğ","ü"),
        listOf("a","s","d","f","g","h","j","k","l","ş","i"),
        listOf("⇧","z","x","c","v","b","n","m","ö","ç","⌫"),
        listOf("?123",",","🌐","space",".","↵")
    )
    private var shift = false
    private var symbols = false
    private var keyRects = mutableListOf<Pair<RectF,String>>()

    fun toggleShift() { shift = !shift; invalidate() }
    fun toggleSymbols() { symbols = !symbols; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (resources.displayMetrics.density * 268).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        // Frosted-glass inspired base; native Android IME cannot use the exact iOS blur pipeline.
        bg.color = Color.rgb(225,230,239)
        canvas.drawColor(Color.rgb(225,230,239))
        bg.color = Color.argb(110,255,255,255)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24*d, 24*d, bg)
        keyRects.clear()
        val horizontal = 4*d
        val top = 9*d
        val rowGap = 9*d
        val rowHeight = 43*d
        rows.forEachIndexed { ri, row ->
            val keys = if (symbols && ri == 0) listOf("1","2","3","4","5","6","7","8","9","0","-","+") else if (symbols && ri == 1) listOf("@","#","\$","%","&","*","(",")","/",":",";") else if (symbols && ri == 2) listOf("=","_","\\","|","~","<",">","€","£","⌫") else row
            val y = top + ri * (rowHeight + rowGap)
            val totalWeight = keys.sumOf { weight(it) }
            var x = horizontal
            val usable = width - horizontal * 2 - (keys.size - 1) * (4*d)
            keys.forEach { raw ->
                val w = usable * weight(raw) / totalWeight
                val rect = RectF(x, y, x+w, y+rowHeight)
                val special = raw in listOf("⇧","⌫","?123","↵","🌐") || raw == "space"
                keyPaint.color = if (special) Color.argb(180,197,205,218) else Color.argb(238,255,255,255)
                keyPaint.setShadowLayer(1.5f*d, 0f, 1*d, Color.argb(28,40,48,65))
                canvas.drawRoundRect(rect, 8*d, 8*d, keyPaint)
                keyPaint.clearShadowLayer()
                val label = when (raw) { "space" -> "Türkçe"; else -> if (shift && raw.length == 1 && raw[0].isLetter()) raw.uppercase() else raw }
                textPaint.textSize = when { raw == "space" -> 12*d; raw.length > 1 -> 13*d; else -> 20*d }
                textPaint.typeface = if (special) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.create("sans-serif", Typeface.NORMAL)
                val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent())/2
                canvas.drawText(label, rect.centerX(), baseline, textPaint)
                keyRects.add(rect to when (raw) { "Türkçe" -> "space"; else -> raw })
                x += w + 4*d
            }
        }
    }

    private fun weight(key: String): Float = when (key) {
        "space" -> 4.2f
        "⇧", "⌫", "?123", "↵" -> 1.45f
        "🌐" -> 1.15f
        else -> 1f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            keyRects.firstOrNull { it.first.contains(event.x, event.y) }?.let {
                onKey(if (shift && it.second.length == 1 && it.second[0].isLetter()) it.second.uppercase() else it.second)
                if (shift && it.second.length == 1 && it.second[0].isLetter()) { shift = false; invalidate() }
            }
            performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
