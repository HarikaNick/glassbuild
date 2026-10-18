
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
                if (!selected.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }

            "enter" -> {
                val action = currentInputEditorInfo?.imeOptions
                    ?.and(EditorInfo.IME_MASK_ACTION)
                if (action != null &&
                    action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED &&
                    action != EditorInfo.IME_ACTION_NO_ENTER_ACTION
                ) {
                    ic.performEditorAction(action)
                } else {
                    ic.commitText("\n", 1)
                }
            }

            "space" -> ic.commitText(" ", 1)
            "shift" -> keyboard?.toggleShift()
            "symbols" -> keyboard?.toggleSymbols()
            "letters" -> keyboard?.toggleSymbols()
            "globe" -> {
                try {
                    switchToNextInputMethod(false)
                } catch (_: Exception) {}
            }
            "emoji" -> keyboard?.toggleEmoji()
            else -> ic.commitText(key, 1)
        }

        keyboard?.refreshSuggestions()
    }

    private fun vibrateTap() {
        if (!getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE)
                .getBoolean("vibration", true)
        ) return

        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createOneShot(8, 30))
                } else {
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

    private val prefs =
        context.getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
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
        "tamam","teşekkür","teşekkürler","merhaba","nasılsın",
        "iyiyim","güzel","evet","hayır","olur","olmaz","aynen",
        "bence","çünkü","şimdi","sonra","bugün","yarın","akşam",
        "sabah","arkadaş","kanka","canım","lütfen","yardım",
        "bir","ben","sen","biz","siz","çok","daha","var","yok",
        "nasıl","neden","nerede","hangi","zaman","harika","tamamdır",
        "olacak","geliyor","gidiyorum","biliyorum","anladım",
        "haklısın","selam","naber","iyi","günaydın","sorun",
        "istiyorum","yapabilir","gerekir","güzelmiş","kesinlikle"
    )

    private val emojiList = listOf("😀","😂","🥹","😍","🥰","😎","😭","❤️","👍","🙏","🔥","✨")

    private var shift = false
    private var symbols = false
    private var emojiMode = false
    private var dark = true
    private var opacity = 68
    private var roundness = 10

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
            (context as? InputMethodService)
                ?.currentInputConnection
                ?.getTextBeforeCursor(80, 0)
                ?.toString().orEmpty()
        } catch (_: Exception) {
            ""
        }

        val prefix = Regex("[\\p{L}\\p{M}]+$")
            .find(before)?.value.orEmpty()

        suggestions = if (prefix.isBlank()) {
            listOf("tamam","nasılsın","teşekkürler")
        } else {
            val matches = dictionary.filter {
                it.startsWith(prefix, ignoreCase = true) &&
                    !it.equals(prefix, ignoreCase = true)
            }.distinct().take(2)
            (listOf(prefix) + matches).take(3)
        }

        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val h = (if (emojiMode) 290 else 270) * d
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            h.toInt()
        )
    }

    private fun readAppearance() {
        val mode = prefs.getString("theme", "system") ?: "system"
        val night =
            (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> night
        }

        opacity = prefs.getInt("opacity", 68).coerceIn(35, 100)
        roundness = prefs.getInt("roundness", 10).coerceIn(5, 18)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        readAppearance()

        val d = resources.displayMetrics.density
        val bg = if (dark) Color.rgb(39, 40, 45)
                 else Color.rgb(211, 216, 225)
        val fg = if (dark) Color.WHITE
                 else Color.rgb(35, 37, 43)

        canvas.drawColor(bg)

        fill.color = Color.argb(
            if (dark) 230 else 220,
            Color.red(bg), Color.green(bg), Color.blue(bg)
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        keyRects.clear()
        suggestionRects.clear()
        emojiRects.clear()

        val margin = 5f * d
        val suggestionTop = 4f * d
        val suggestionHeight = 30f * d

        if (!emojiMode) {
            val cellWidth = (width - margin * 2) / 3f

            suggestions.take(3).forEachIndexed { i, word ->
                val rect = RectF(
                    margin + i * cellWidth,
                    suggestionTop,
                    margin + (i + 1) * cellWidth,
                    suggestionTop + suggestionHeight
                )
                textPaint.color = fg
                textPaint.textSize = 14f * d
                drawCentered(canvas, word, rect)
                suggestionRects.add(rect to "suggest:$word")
            }
        }

        val rowTop = if (emojiMode) 8f * d else 39f * d

        if (emojiMode) {
            val columns = 6
            val cellW = (width - margin * 2) / columns
            val cellH = 42f * d

            emojiList.forEachIndexed { i, emoji ->
                val rect = RectF(
                    margin + (i % columns) * cellW,
                    rowTop + (i / columns) * cellH,
                    margin + (i % columns + 1) * cellW,
                    rowTop + (i / columns + 1) * cellH
                )
                textPaint.color = fg
                textPaint.textSize = 25f * d
                drawCentered(canvas, emoji, rect)
                emojiRects.add(rect to "emoji:$emoji")
            }

            val back = RectF(
                margin, height - 42f * d,
                width - margin, height - 5f * d
            )
            drawKey(canvas, back, true, d)
            textPaint.color = fg
            textPaint.textSize = 14f * d
            drawCentered(canvas, "ABC", back)
            keyRects.add(back to "emoji")
            return
        }

        val rows = if (symbols) symbolRows else letters
        val rowHeight = 42f * d
        val rowGap = 7f * d
        val keyGap = 4f * d

        rows.forEachIndexed { rowIndex, row ->
            val y = rowTop + rowIndex * (rowHeight + rowGap)
            val weights = row.map { keyWeight(it) }
            val total = weights.sum()
            val available = width - margin * 2 - (row.size - 1) * keyGap
            var x = margin

            row.forEachIndexed { index, raw ->
                val w = available * weights[index] / total
                val rect = RectF(x, y, x + w, y + rowHeight)
                val special = raw in listOf(
                    "shift","delete","symbols","letters",
                    "globe","enter","space"
                )

                drawKey(canvas, rect, special, d)

                val label = when (raw) {
                    "shift" -> if (shift) "⇧" else "↑"
                    "delete" -> "⌫"
                    "symbols" -> "?123"
                    "letters" -> "ABC"
                    "globe" -> "🌐"
                    "space" -> "Türkçe"
                    "enter" -> "↵"
                    else -> if (shift && raw.length == 1 && raw[0].isLetter())
                        raw.uppercase() else raw
                }

                textPaint.color = fg
                textPaint.textSize = when (raw) {
                    "space" -> 13f * d
                    "symbols","letters","globe","enter","shift","delete" -> 15f * d
                    else -> 21f * d
                }
                textPaint.typeface = if (special)
                    Typeface.create("sans-serif-medium", Typeface.NORMAL)
                else Typeface.create("sans-serif", Typeface.NORMAL)

                drawCentered(canvas, label, rect)
                keyRects.add(rect to raw)
                x += w + keyGap
            }
        }
    }

    private fun keyWeight(key: String): Float = when (key) {
        "space" -> 4.5f
        "shift","delete","symbols","letters","enter" -> 1.5f
        "globe" -> 1.15f
        else -> 1f
    }

    private fun drawCentered(canvas: Canvas, text: String, rect: RectF) {
        val baseline = rect.centerY() -
            (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(text, rect.centerX(), baseline, textPaint)
    }

    private fun drawKey(
        canvas: Canvas,
        rect: RectF,
        special: Boolean,
        d: Float
    ) {
        val radius = roundness * d
        val alpha = (opacity * 2.25f).toInt().coerceIn(80, 225)

        val top = if (dark) {
            if (special) Color.argb(alpha, 90, 94, 105)
            else Color.argb(alpha, 76, 79, 90)
        } else {
            if (special) Color.argb(alpha, 171, 178, 191)
            else Color.argb(alpha, 250, 251, 255)
        }

        val bottom = if (dark) {
            if (special) Color.argb(alpha, 66, 69, 79)
            else Color.argb(alpha, 48, 50, 59)
        } else {
            if (special) Color.argb(alpha, 145, 152, 165)
            else Color.argb(alpha, 225, 229, 237)
        }

        keyPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            top, bottom, Shader.TileMode.CLAMP
        )
        keyPaint.setShadowLayer(
            1.5f * d, 0f, 1.5f * d,
            Color.argb(40, 0, 0, 0)
        )
        canvas.drawRoundRect(rect, radius, radius, keyPaint)
        keyPaint.clearShadowLayer()
        keyPaint.shader = null

        borderPaint.strokeWidth = max(.7f * d, 1f)
        borderPaint.color = Color.argb(
            if (dark) 90 else 130, 255, 255, 255
        )
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        fill.color = Color.argb(
            if (dark) 22 else 70, 255, 255, 255
        )
        canvas.drawRoundRect(
            RectF(
                rect.left + 2f * d,
                rect.top + 1f * d,
                rect.right - 2f * d,
                rect.top + 2.5f * d
            ),
            radius, radius, fill
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val suggestion = suggestionRects.firstOrNull {
                    it.first.contains(event.x, event.y)
                }
                val emoji = emojiRects.firstOrNull {
                    it.first.contains(event.x, event.y)
                }
                val key = keyRects.firstOrNull {
                    it.first.contains(event.x, event.y)
                }

                when {
                    emoji != null -> onKey(emoji.second)
                    suggestion != null -> onKey(suggestion.second)
                    key != null -> {
                        val raw = key.second
                        val output = if (
                            shift && raw.length == 1 && raw[0].isLetter()
                        ) raw.uppercase() else raw

                        onKey(output)

                        if (shift && raw.length == 1 && raw[0].isLetter()) {
                            shift = false
                            invalidate()
                        }
                    }
                }

                performClick()
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
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
