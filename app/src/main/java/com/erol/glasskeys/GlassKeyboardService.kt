package com.erol.glasskeys

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlin.math.max

class GlassKeyboardService : InputMethodService() {
    private var keyboard: IOSKeyboardView? = null

    override fun onCreateInputView(): View {
        keyboard = IOSKeyboardView(this) { handleKey(it) }
        return keyboard!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboard?.refreshSuggestions()
    }

    private fun handleKey(key: String) {
        val ic = currentInputConnection ?: return

        if (key.startsWith("suggest:")) {
            val word = key.removePrefix("suggest:")
            val before = ic.getTextBeforeCursor(100, 0)?.toString().orEmpty()
            val current = Regex("[\\p{L}\\p{M}]+$").find(before)?.value.orEmpty()
            if (current.isNotEmpty()) ic.deleteSurroundingText(current.length, 0)
            ic.commitText("$word ", 1)
            keyboard?.refreshSuggestions()
            return
        }
        if (key.startsWith("emoji:")) {
            ic.commitText(key.removePrefix("emoji:"), 1)
            keyboard?.refreshSuggestions()
            return
        }

        vibrateTap()
        when (key) {
            "delete" -> {
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) ic.commitText("", 1)
                else ic.deleteSurroundingText(1, 0)
            }
            "enter" -> {
                val info = currentInputEditorInfo
                val options = info?.imeOptions ?: 0
                val action = options and EditorInfo.IME_MASK_ACTION
                val hasNoEnterAction = (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                val isActionDefined = action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED
                if (isActionDefined && !hasNoEnterAction) {
                    ic.performEditorAction(action)
                } else {
                    ic.commitText("\n", 1)
                }
            }
            "space" -> ic.commitText(" ", 1)
            "shift" -> keyboard?.toggleShift()
            "symbols", "letters" -> keyboard?.toggleSymbols()
            "globe" -> try { switchToNextInputMethod(false) } catch (_: Exception) {}
            "emoji" -> keyboard?.toggleEmoji()
            else -> ic.commitText(key, 1)
        }
        keyboard?.refreshSuggestions()
    }

    private fun vibrateTap() {
        if (!getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE)
                .getBoolean("vibration", true)) return
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(8, 28))
                else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(8)
                }
            }
        } catch (_: Exception) {}
    }
}

private class IOSKeyboardView(
    context: Context,
    private val onKey: (String) -> Unit
) : View(context) {
    private val prefs = context.getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val letters = listOf(
        listOf("q","w","e","r","t","y","u","ı","o","p","ğ","ü"),
        listOf("a","s","d","f","g","h","j","k","l","ş","i"),
        listOf("shift","z","x","c","v","b","n","m","ö","ç","delete"),
        listOf("symbols",",","globe","space",".","enter")
    )
    private val symbolRows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","₺","&","*","-","+","(",")","/"),
        listOf("=","%","€","£",":",";","!","?","delete"),
        listOf("letters",",","globe","space",".","enter")
    )
    private val dictionary = listOf(
        "tamam","teşekkür","teşekkürler","merhaba","nasılsın","iyiyim","güzel","evet",
        "hayır","olur","olmaz","aynen","bence","çünkü","şimdi","sonra","bugün","yarın",
        "akşam","sabah","arkadaş","kanka","canım","lütfen","yardım","bir","ben","sen",
        "biz","siz","çok","daha","var","yok","nasıl","neden","nerede","hangi","zaman",
        "harika","tamamdır","olacak","geliyor","gidiyorum","biliyorum","anladım","haklısın",
        "selam","naber","iyi","günaydın","sorun","istiyorum","yapabilir","gerekir",
        "güzelmiş","kesinlikle"
    )
    private val emojiList = listOf("😀","😂","🥹","😍","🥰","😎","😭","❤️","👍","🙏","🔥","✨")

    private var shift = false
    private var symbols = false
    private var emojiMode = false
    private var dark = true
    private var opacity = 92
    private var roundness = 9
    private var pressedKey: String? = null
    private var suggestions = listOf("tamam","nasılsın","teşekkürler")

    private val keyRects = mutableListOf<Pair<RectF,String>>()
    private val suggestionRects = mutableListOf<Pair<RectF,String>>()
    private val emojiRects = mutableListOf<Pair<RectF,String>>()

    fun toggleShift() {
        shift = !shift
        invalidate()
    }
    fun toggleSymbols() {
        symbols = !symbols
        emojiMode = false
        invalidate()
    }
    fun toggleEmoji() {
        emojiMode = !emojiMode
        invalidate()
    }

    fun refreshSuggestions() {
        val before = try {
            (context as? InputMethodService)?.currentInputConnection
                ?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        } catch (_: Exception) { "" }
        val prefix = Regex("[\\p{L}\\p{M}]+$").find(before)?.value.orEmpty()
        suggestions = if (prefix.isBlank()) {
            listOf("tamam","nasılsın","teşekkürler")
        } else {
            val matches = dictionary.asSequence()
                .filter { it.startsWith(prefix, ignoreCase = true) }
                .filterNot { it.equals(prefix, ignoreCase = true) }
                .distinct()
                .take(2)
                .toList()
            (listOf(prefix) + matches).take(3)
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val desired = (if (emojiMode) 292 else 270) * d
        val h = resolveSize(desired.toInt(), heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    private fun readAppearance() {
        val mode = prefs.getString("theme", "system") ?: "system"
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> night
        }
        opacity = prefs.getInt("opacity", 92).coerceIn(35, 100)
        roundness = prefs.getInt("roundness", 9).coerceIn(5, 18)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        readAppearance()
        val d = resources.displayMetrics.density
        val bgTop = if (dark) Color.rgb(48, 50, 57) else Color.rgb(229, 232, 238)
        val bgBottom = if (dark) Color.rgb(27, 28, 33) else Color.rgb(194, 200, 210)
        val foreground = if (dark) Color.rgb(250, 250, 252) else Color.rgb(30, 32, 38)

        backgroundPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
            bgTop, bgBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        backgroundPaint.shader = null

        // A restrained upper sheen and edge vignette give the panel depth without a fake neon glow.
        highlightPaint.shader = LinearGradient(0f, 0f, 0f, 62f * d,
            Color.argb(if (dark) 24 else 45, 255, 255, 255),
            Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), 62f * d, highlightPaint)
        highlightPaint.shader = null

        keyRects.clear()
        suggestionRects.clear()
        emojiRects.clear()

        val margin = 4f * d
        val suggestionTop = 1f * d
        val suggestionHeight = 33f * d
        if (!emojiMode) {
            val cellWidth = (width - margin * 2) / 3f
            suggestions.take(3).forEachIndexed { i, word ->
                val rect = RectF(margin + i * cellWidth, suggestionTop,
                    margin + (i + 1) * cellWidth, suggestionTop + suggestionHeight)
                textPaint.color = foreground
                textPaint.textSize = 14f * d
                textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                drawCentered(canvas, word, rect)
                suggestionRects.add(rect to "suggest:$word")
                if (i > 0) {
                    edgePaint.strokeWidth = 0.7f * d
                    edgePaint.color = Color.argb(if (dark) 35 else 45, foreground.let { Color.red(it) },
                        Color.green(foreground), Color.blue(foreground))
                    val x = rect.left
                    canvas.drawLine(x, rect.top + 7f * d, x, rect.bottom - 7f * d, edgePaint)
                }
            }
        }

        val rowTop = if (emojiMode) 8f * d else 37f * d
        if (emojiMode) {
            val columns = 6
            val cellW = (width - margin * 2) / columns
            val cellH = 42f * d
            emojiList.forEachIndexed { i, emoji ->
                val rect = RectF(margin + (i % columns) * cellW,
                    rowTop + (i / columns) * cellH,
                    margin + (i % columns + 1) * cellW,
                    rowTop + (i / columns + 1) * cellH)
                textPaint.color = foreground
                textPaint.textSize = 25f * d
                drawCentered(canvas, emoji, rect)
                emojiRects.add(rect to "emoji:$emoji")
            }
            val back = RectF(margin, height - 42f * d, width - margin, height - 5f * d)
            drawKey(canvas, back, true, d, pressedKey == "emoji")
            textPaint.color = foreground
            textPaint.textSize = 14f * d
            drawCentered(canvas, "ABC", back)
            keyRects.add(back to "emoji")
            return
        }

        val rows = if (symbols) symbolRows else letters
        val rowHeight = 42f * d
        val rowGap = 5f * d
        val keyGap = 3f * d
        rows.forEachIndexed { rowIndex, row ->
            val y = rowTop + rowIndex * (rowHeight + rowGap)
            val weights = row.map { keyWeight(it) }
            val total = weights.sum()
            val available = width - margin * 2 - (row.size - 1) * keyGap
            var x = margin
            row.forEachIndexed { index, raw ->
                val w = available * weights[index] / total
                val rect = RectF(x, y, x + w, y + rowHeight)
                val special = raw in setOf("shift","delete","symbols","letters","globe","enter","space")
                drawKey(canvas, rect, special, d, raw == pressedKey)
                val label = when (raw) {
                    "shift" -> if (shift) "⇧" else "↑"
                    "delete" -> "⌫"
                    "symbols" -> "?123"
                    "letters" -> "ABC"
                    "globe" -> "🌐"
                    "space" -> "Türkçe"
                    "enter" -> "↵"
                    else -> if (shift && raw.length == 1 && raw[0].isLetter()) raw.uppercase() else raw
                }
                textPaint.color = foreground
                textPaint.textSize = when (raw) {
                    "space" -> 13f * d
                    "symbols","letters","globe","enter","shift","delete" -> 15f * d
                    else -> 21f * d
                }
                textPaint.typeface = if (special) Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    else Typeface.create("sans-serif", Typeface.NORMAL)
                drawCentered(canvas, label, rect)
                keyRects.add(rect to raw)
                x += w + keyGap
            }
        }
    }

    private fun keyWeight(key: String): Float = when (key) {
        "space" -> 4.65f
        "shift","delete" -> 1.55f
        "symbols","letters" -> 1.65f
        "enter" -> 1.75f
        "globe" -> 1.05f
        else -> 1f
    }

    private fun drawCentered(canvas: Canvas, text: String, rect: RectF) {
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(text, rect.centerX(), baseline, textPaint)
    }

    private fun drawKey(canvas: Canvas, rect: RectF, special: Boolean, d: Float, pressed: Boolean = false) {
        val radius = roundness * d
        val alpha = (opacity * 2.25f).toInt().coerceIn(80, 225)
        val keyRect = if (pressed) RectF(rect.left, rect.top + 1.5f * d, rect.right, rect.bottom + 1.5f * d) else rect

        val top = if (dark) {
            if (special) Color.rgb(88, 90, 98) else Color.rgb(75, 77, 84)
        } else {
            if (special) Color.rgb(165, 170, 180) else Color.rgb(255, 255, 255)
        }
        val bottom = if (dark) {
            if (special) Color.rgb(58, 60, 67) else Color.rgb(47, 49, 56)
        } else {
            if (special) Color.rgb(142, 148, 159) else Color.rgb(232, 235, 241)
        }

        val keyTop = if (pressed) {
            if (dark) Color.rgb(55, 57, 64) else Color.rgb(210, 215, 224)
        } else Color.rgb(Color.red(top), Color.green(top), Color.blue(top))
        val keyBottom = if (pressed) {
            if (dark) Color.rgb(41, 43, 49) else Color.rgb(192, 198, 208)
        } else Color.rgb(Color.red(bottom), Color.green(bottom), Color.blue(bottom))

        keyPaint.shader = LinearGradient(keyRect.left, keyRect.top, keyRect.left, keyRect.bottom,
            Color.argb(alpha, Color.red(keyTop), Color.green(keyTop), Color.blue(keyTop)),
            Color.argb(alpha, Color.red(keyBottom), Color.green(keyBottom), Color.blue(keyBottom)),
            Shader.TileMode.CLAMP)
        keyPaint.setShadowLayer((if (pressed) 0.4f else 2.0f) * d, 0f,
            (if (pressed) 0.4f else 1.5f) * d, Color.argb(if (pressed) 16 else 42, 0, 0, 0))
        canvas.drawRoundRect(keyRect, radius, radius, keyPaint)
        keyPaint.clearShadowLayer()
        keyPaint.shader = null

        edgePaint.strokeWidth = max(0.65f * d, 0.8f)
        edgePaint.color = Color.argb(if (dark) 58 else 90, 255, 255, 255)
        canvas.drawRoundRect(keyRect, radius, radius, edgePaint)

        // Thin inner top reflection: subtle enough to read as material, not an outline.
        highlightPaint.shader = LinearGradient(keyRect.left, keyRect.top, keyRect.left, keyRect.top + 9f * d,
            Color.argb(if (dark) 32 else 75, 255, 255, 255),
            Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(keyRect.left + 2f * d, keyRect.top + 1f * d,
            keyRect.right - 2f * d, keyRect.top + 9f * d), radius, radius, highlightPaint)
        highlightPaint.shader = null

        // Soft lower edge adds separation against the keyboard base.
        highlightPaint.color = Color.argb(if (dark) 24 else 34, 0, 0, 0)
        canvas.drawLine(keyRect.left + radius, keyRect.bottom - 1f * d,
            keyRect.right - radius, keyRect.bottom - 1f * d, highlightPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedKey = keyRects.firstOrNull { it.first.contains(event.x, event.y) }?.second
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val suggestion = suggestionRects.firstOrNull { it.first.contains(event.x, event.y) }
                val emoji = emojiRects.firstOrNull { it.first.contains(event.x, event.y) }
                val key = keyRects.firstOrNull { it.first.contains(event.x, event.y) }
                when {
                    emoji != null -> onKey(emoji.second)
                    suggestion != null -> onKey(suggestion.second)
                    key != null -> {
                        val raw = key.second
                        // A short tap switches input methods; a long press opens emoji.
                        if (raw == "globe" && event.eventTime - event.downTime >= 450L) {
                            onKey("emoji")
                        } else {
                            val output = if (shift && raw.length == 1 && raw[0].isLetter()) raw.uppercase() else raw
                            onKey(output)
                            if (shift && raw.length == 1 && raw[0].isLetter()) shift = false
                        }
                    }
                }
                pressedKey = null
                performClick()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}