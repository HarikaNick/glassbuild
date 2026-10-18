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
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.os.SystemClock
import kotlin.math.max

class GlassKeyboardService : InputMethodService() {
    private var keyboard: IOSKeyboardView? = null
    private var lastSpaceAt = 0L

    override fun onCreateInputView(): View {
        // Let the keyboard view draw its own rounded glass silhouette.
        window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        keyboard = IOSKeyboardView(this) { handleKey(it) }
        return keyboard!!
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        keyboard?.configureForInput(info)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboard?.configureForInput(info)
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
            "space" -> {
                val now = SystemClock.uptimeMillis()
                val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                if (now - lastSpaceAt in 1..650 && before.endsWith(" ") &&
                    before.dropLast(1).lastOrNull()?.isLetterOrDigit() == true) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastSpaceAt = 0L
                } else {
                    ic.commitText(" ", 1)
                    lastSpaceAt = now
                }
            }
            "shift" -> keyboard?.toggleShift()
            "symbols", "letters" -> keyboard?.toggleSymbols()
            "globe" -> try { switchToNextInputMethod(false) } catch (_: Exception) {}
            "emoji" -> keyboard?.toggleEmoji()
            "voice" -> { /* Voice recognition requires a separate permission and result flow. */ }
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
    private val emojiList = listOf("😀","😃","😄","😁","😆","😅","😂","🤣","🥹","😊","😇","🙂","🙃","😉","😍","🥰","😘","😋","😎","🤩","🥳","😭","😢","😡","🤔","🤗","🤫","🤭","😴","🥱","🤯","😱","🤮","🤒","🤕","🤧","👍","👎","👏","🙌","🙏","💪","🫶","❤️","🧡","💛","💚","💙","💜","🖤","🤍","💔","🔥","✨","⭐","🌙","☀️","🌈","🎉","💯","🍀","🌹","🌸","🍕","🍔","☕","⚽","🎮","🚗","✈️","🐱","🐶")

    private var shift = false
    private var capsLock = false
    private var passwordInput = false
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

    fun configureForInput(info: EditorInfo?) {
        val inputType = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        passwordInput = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val caps = (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            ((inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0 ||
             (inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0 ||
             (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0)
        if (caps && !passwordInput) shift = true
        if (passwordInput) suggestions = emptyList()
        invalidate()
    }

    fun toggleShift() {
        if (shift && capsLock) {
            capsLock = false
            shift = false
        } else if (shift) {
            capsLock = true
            shift = true
        } else {
            shift = true
        }
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
        if (passwordInput) {
            suggestions = emptyList()
            invalidate()
            return
        }
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
        // Keep the keyboard close to the compact iOS footprint and avoid dead space below the last row.
        val desired = (if (emojiMode) 310 else 270) * d
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
        val foreground = if (dark) Color.rgb(250, 250, 252) else Color.rgb(30, 32, 38)

        // Full keyboard silhouette: smoked translucent glass with rounded lower corners.
        // Keep the canvas clear outside this shape so the IME window can reveal what is behind it.
        val panel = RectF(1f * d, 1f * d, width - 1f * d, height - 1f * d)
        val panelRadius = 34f * d
        val panelAlpha = (if (dark) 118f else 132f) * (opacity / 92f)
        backgroundPaint.shader = LinearGradient(0f, panel.top, 0f, panel.bottom,
            Color.argb(panelAlpha.toInt().coerceIn(65, 175), if (dark) 49 else 231,
                if (dark) 49 else 234, if (dark) 56 else 241),
            Color.argb((panelAlpha * 0.82f).toInt().coerceIn(55, 160), if (dark) 24 else 196,
                if (dark) 25 else 202, if (dark) 31 else 213), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(panel, panelRadius, panelRadius, backgroundPaint)
        backgroundPaint.shader = null

        // Glass rim: a low-contrast outer edge and a broad soft reflection across the top.
        edgePaint.strokeWidth = 1f * d
        edgePaint.color = Color.argb(if (dark) 92 else 125, 245, 247, 255)
        canvas.drawRoundRect(RectF(panel.left + 0.5f * d, panel.top + 0.5f * d,
            panel.right - 0.5f * d, panel.bottom - 0.5f * d), panelRadius, panelRadius, edgePaint)
        highlightPaint.shader = LinearGradient(0f, panel.top, 0f, panel.top + 72f * d,
            Color.argb(if (dark) 38 else 68, 255, 255, 255),
            Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(panel, panelRadius, panelRadius, highlightPaint)
        highlightPaint.shader = null

        keyRects.clear()
        suggestionRects.clear()
        emojiRects.clear()

        val margin = 4f * d
        val suggestionTop = 1f * d
        val suggestionHeight = 33f * d
        if (!emojiMode && !passwordInput) {
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

        val rowTop = if (emojiMode) 8f * d else if (passwordInput) 5f * d else 37f * d
        if (emojiMode) {
            val columns = 8
            val cellW = (width - margin * 2) / columns
            val cellH = 31f * d
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

        val baseRows = if (symbols) symbolRows else letters
        val numberRow = listOf("1","2","3","4","5","6","7","8","9","0")
        val rows = if (!symbols && prefs.getBoolean("number_row", false)) listOf(numberRow) + baseRows else baseRows
        // Reserve a separate utility strip below the key rows, as in the reference keyboard.
        val utilityStrip = 34f * d
        val rowHeight = ((height - rowTop - utilityStrip - 10f * d - (rows.size - 1) * 4f * d) / rows.size)
            .coerceIn(30f * d, 44f * d)
        val rowGap = 4f * d
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
                drawKey(canvas, rect, special, d, raw == pressedKey, raw)
                val label = when (raw) {
                    "shift" -> if (shift) "⇧" else "↑"
                    "delete" -> "⌫"
                    "symbols" -> "?123"
                    "letters" -> "ABC"
                    "globe" -> "🌐"
                    "space" -> "Türkçe"
                    "enter" -> enterLabel()
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

        // Bottom utility strip: standalone emoji and microphone controls outside the key row.
        val utilityY = height - 30f * d
        val emojiButton = RectF(10f * d, utilityY - 15f * d, 43f * d, utilityY + 13f * d)
        textPaint.color = foreground
        textPaint.textSize = 24f * d
        drawCentered(canvas, "☻", emojiButton)
        keyRects.add(emojiButton to "emoji")
        val micButton = RectF(width - 43f * d, utilityY - 15f * d, width - 10f * d, utilityY + 13f * d)
        drawMicrophone(canvas, micButton.centerX(), micButton.centerY(), d, foreground)
        keyRects.add(micButton to "voice")

        // iOS-style key preview bubble: draw it above the pressed character.
        val pressed = pressedKey
        if (pressed != null && pressed.length == 1 && (pressed[0].isLetterOrDigit() || pressed in listOf(",", ".", "?", "!", "'", "-"))) {
            val target = keyRects.firstOrNull { it.second == pressed }?.first
            if (target != null) {
                val popupWidth = max(target.width() + 12f * d, 42f * d)
                val popupHeight = 48f * d
                val popupBottom = target.top - 3f * d
                val popupTop = popupBottom - popupHeight
                val popup = RectF(target.centerX() - popupWidth / 2f, popupTop,
                    target.centerX() + popupWidth / 2f, popupBottom)
                val clamped = popup.apply {
                    if (left < 2f * d) offset(2f * d - left, 0f)
                    if (right > width - 2f * d) offset(width - 2f * d - right, 0f)
                }
                keyPaint.shader = LinearGradient(clamped.left, clamped.top, clamped.left, clamped.bottom,
                    if (dark) Color.rgb(104, 108, 118) else Color.rgb(255, 255, 255),
                    if (dark) Color.rgb(64, 68, 77) else Color.rgb(220, 226, 235),
                    Shader.TileMode.CLAMP)
                keyPaint.setShadowLayer(4f * d, 0f, 2f * d, Color.argb(65, 0, 0, 0))
                canvas.drawRoundRect(clamped, 12f * d, 12f * d, keyPaint)
                keyPaint.clearShadowLayer()
                keyPaint.shader = null
                edgePaint.strokeWidth = 1f * d
                edgePaint.color = Color.argb(if (dark) 125 else 150, 255, 255, 255)
                canvas.drawRoundRect(clamped, 12f * d, 12f * d, edgePaint)
                textPaint.color = foreground
                textPaint.textSize = 29f * d
                textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                val popupLabel = if (shift) pressed.uppercase() else pressed
                drawCentered(canvas, popupLabel, clamped)
            }
        }
    }


    private fun alternateCharacters(letter: String): List<String> = when (letter) {
        "a" -> listOf("á", "à", "â", "ä", "æ", "å")
        "c" -> listOf("ç", "ć", "č")
        "e" -> listOf("é", "è", "ê", "ë")
        "g" -> listOf("ğ")
        "i" -> listOf("î", "ï", "í", "ì")
        "ı" -> listOf("i", "î", "ï")
        "n" -> listOf("ñ", "ń")
        "o" -> listOf("ö", "ó", "ô", "ò", "õ")
        "s" -> listOf("ş", "ś", "š")
        "u" -> listOf("ü", "ú", "û", "ù")
        "y" -> listOf("ý", "ÿ")
        "z" -> listOf("ž", "ź")
        else -> emptyList()
    }

    private fun enterLabel(): String {
        val service = context as? InputMethodService
        val options = service?.currentInputEditorInfo?.imeOptions ?: 0
        return when (options and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Git"
            EditorInfo.IME_ACTION_SEARCH -> "Ara"
            EditorInfo.IME_ACTION_SEND -> "Gönder"
            EditorInfo.IME_ACTION_NEXT -> "İleri"
            EditorInfo.IME_ACTION_DONE -> "Bitti"
            else -> "↵"
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

    private fun drawMicrophone(canvas: Canvas, cx: Float, cy: Float, d: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        val capsule = RectF(cx - 4.2f * d, cy - 10f * d, cx + 4.2f * d, cy + 4f * d)
        canvas.drawRoundRect(capsule, 4.2f * d, 4.2f * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.8f * d
        p.strokeCap = Paint.Cap.ROUND
        val cradle = RectF(cx - 8f * d, cy - 5f * d, cx + 8f * d, cy + 9f * d)
        canvas.drawArc(cradle, 0f, 180f, false, p)
        canvas.drawLine(cx, cy + 9f * d, cx, cy + 13f * d, p)
        canvas.drawLine(cx - 5f * d, cy + 13f * d, cx + 5f * d, cy + 13f * d, p)
    }

    private fun drawCentered(canvas: Canvas, text: String, rect: RectF) {
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(text, rect.centerX(), baseline, textPaint)
    }

    private fun drawKey(
        canvas: Canvas, rect: RectF, special: Boolean, d: Float,
        pressed: Boolean = false, key: String = ""
    ) {
        val radius = roundness * d
        // Keycaps are noticeably more transparent than conventional keyboard buttons.
        val alpha = ((if (dark) 108f else 145f) * (opacity / 92f)).toInt().coerceIn(65, 185)
        val keyRect = if (pressed) RectF(rect.left, rect.top + 1.5f * d, rect.right, rect.bottom + 1.5f * d) else rect

        val accentKey = key == "globe" || key == "shift" && shift
        val actionKey = key == "enter"
        val top = when {
            dark && accentKey -> Color.rgb(45, 104, 171)
            dark && actionKey -> Color.rgb(91, 97, 109)
            dark && special -> Color.rgb(86, 88, 96)
            dark -> Color.rgb(76, 79, 87)
            accentKey -> Color.rgb(210, 228, 250)
            actionKey -> Color.rgb(218, 224, 233)
            special -> Color.rgb(204, 210, 220)
            else -> Color.rgb(250, 251, 253)
        }
        val bottom = when {
            dark && accentKey -> Color.rgb(26, 73, 128)
            dark && actionKey -> Color.rgb(63, 67, 77)
            dark && special -> Color.rgb(57, 59, 67)
            dark -> Color.rgb(48, 50, 58)
            accentKey -> Color.rgb(180, 207, 239)
            actionKey -> Color.rgb(187, 196, 210)
            special -> Color.rgb(181, 189, 202)
            else -> Color.rgb(225, 230, 238)
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
        edgePaint.color = if (dark) Color.argb(if (pressed) 95 else 76, 235, 240, 249)
            else Color.argb(if (pressed) 155 else 120, 255, 255, 255)
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
                        // Long-press letters to type an alternate character; globe opens emoji.
                        val held = event.eventTime - event.downTime >= 450L
                        if (raw == "globe" && held) {
                            onKey("emoji")
                        } else if (held && raw.length == 1 && raw[0].isLetter()) {
                            val alternatives = alternateCharacters(raw.lowercase())
                            if (alternatives.isNotEmpty()) {
                                val chosen = alternatives.first()
                                onKey(if (shift) chosen.uppercase() else chosen)
                            } else {
                                onKey(if (shift) raw.uppercase() else raw)
                            }
                        } else {
                            val output = if (shift && raw.length == 1 && raw[0].isLetter()) raw.uppercase() else raw
                            onKey(output)
                            if (shift && !capsLock && raw.length == 1 && raw[0].isLetter()) shift = false
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