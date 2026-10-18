
package com.erol.glasskeys

import android.inputmethodservice.InputMethodService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlin.math.max

class GlassKeyboardService : InputMethodService() {
    private var keyboard: GlassKeyboardView? = null

    override fun onCreateInputView(): View {
        keyboard = GlassKeyboardView(this) { handleKey(it) }
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

        vibrateTap()
        when (key) {
            "⌫" -> ic.deleteSurroundingText(1, 0)
            "↵" -> {
                val action = currentInputEditorInfo?.imeOptions
                    ?.and(EditorInfo.IME_MASK_ACTION)
                if (action != null &&
                    action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
                    ic.performEditorAction(action)
                } else {
                    ic.commitText("\n", 1)
                }
            }
            "space" -> ic.commitText(" ", 1)
            "⇧" -> keyboard?.toggleShift()
            "?123" -> keyboard?.toggleSymbols()
            "🌐" -> {
                try {
                    switchToNextInputMethod(false)
                } catch (_: Exception) {}
            }
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
                    vibrator.vibrate(VibrationEffect.createOneShot(9, 35))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(9)
                }
            }
        } catch (_: Exception) {}
    }
}

private class GlassKeyboardView(
    context: Context,
    private val onKey: (String) -> Unit
) : View(context) {

    private val prefs =
        context.getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val rows = listOf(
        listOf("q","w","e","r","t","y","u","ı","o","p","ğ","ü"),
        listOf("a","s","d","f","g","h","j","k","l","ş","i"),
        listOf("⇧","z","x","c","v","b","n","m","ö","ç","⌫"),
        listOf("?123",",","🌐","space",".","↵")
    )

    private val dictionary = listOf(
        "tamam","teşekkür","teşekkürler","merhaba","nasılsın",
        "iyiyim","güzel","evet","hayır","olur","olmaz","aynen",
        "bence","çünkü","şimdi","sonra","bugün","yarın","akşam",
        "sabah","arkadaş","kanka","knk","canım","lütfen","yardım",
        "bir","ben","sen","biz","siz","çok","daha","var","yok",
        "nasıl","neden","nerede","hangi","zaman","harika","tamamdır",
        "olacak","geliyor","gidiyorum","biliyorum","anladım",
        "haklısın","selam","naber","iyi","günaydın","sorun"
    )

    private val shortcuts = mapOf(
        "slm" to "selam",
        "mrb" to "merhaba",
        "tmm" to "tamam",
        "nbr" to "naber"
    )

    private val defaultSuggestions = listOf("tamam","nasılsın","teşekkürler")
    private val emojis = listOf("😄","❤️","🔥","🙏","😹","😭","👍")

    private var shift = false
    private var symbols = false
    private var dark = true
    private var opacity = 68
    private var roundness = 12

    private val keyRects = mutableListOf<Pair<RectF,String>>()
    private val suggestionRects = mutableListOf<Pair<RectF,String>>()
    private val emojiRects = mutableListOf<Pair<RectF,String>>()
    private var suggestions = defaultSuggestions

    fun toggleShift() {
        shift = !shift
        invalidate()
    }

    fun toggleSymbols() {
        symbols = !symbols
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

        val replacement = shortcuts[prefix.lowercase()]

        suggestions = when {
            replacement != null ->
                listOf(replacement, prefix, "teşekkürler").distinct().take(3)

            prefix.isBlank() -> defaultSuggestions

            else -> {
                val matches = dictionary.filter {
                    it.startsWith(prefix, ignoreCase = true) &&
                    !it.equals(prefix, ignoreCase = true)
                }.distinct().take(2)

                (listOf(prefix) + matches).take(3)
            }
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (resources.displayMetrics.density * 282).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    private fun loadAppearance() {
        val mode = prefs.getString("theme", "system") ?: "system"
        val night = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        dark = when (mode) {
            "dark" -> true
            "light" -> false
            else -> night
        }
        opacity = prefs.getInt("opacity", 68).coerceIn(35, 100)
        roundness = prefs.getInt("roundness", 12).coerceIn(6, 18)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        loadAppearance()

        val d = resources.displayMetrics.density
        val background = if (dark) Color.rgb(24,27,34)
                         else Color.rgb(222,228,237)
        val textColor = if (dark) Color.rgb(250,251,255)
                        else Color.rgb(29,36,49)
        val alpha = (opacity * 2.2f).toInt().coerceIn(70,220)

        canvas.drawColor(background)
        paint.color = Color.argb(205, Color.red(background),
            Color.green(background), Color.blue(background))
        canvas.drawRoundRect(
            0f,0f,width.toFloat(),height.toFloat(),
            24*d,24*d,paint
        )

        keyRects.clear()
        suggestionRects.clear()
        emojiRects.clear()

        val margin = 5*d
        val suggestionHeight = 30*d
        val suggestionGap = 4*d
        val suggestionWidth =
            (width - margin*2 - suggestionGap*2) / 3f

        suggestions.take(3).forEachIndexed { index, word ->
            val left = margin + index*(suggestionWidth+suggestionGap)
            val rect = RectF(
                left,6*d,left+suggestionWidth,6*d+suggestionHeight
            )
            drawGlassKey(canvas,rect,false,alpha,d)
            textPaint.color = textColor
            textPaint.textSize = 13*d
            drawCenteredText(canvas,word,rect)
            suggestionRects.add(rect to "suggest:$word")
        }

        val emojiWidth = (width-margin*2)/emojis.size
        emojis.forEachIndexed { index, emoji ->
            val rect = RectF(
                margin+index*emojiWidth,42*d,
                margin+(index+1)*emojiWidth,66*d
            )
            textPaint.textSize = 18*d
            drawCenteredText(canvas,emoji,rect)
            emojiRects.add(rect to emoji)
        }

        val horizontal = 4*d
        val top = 75*d
        val rowHeight = 42*d
        val rowGap = 7*d

        rows.forEachIndexed { rowIndex, row ->
            val keys = when {
                symbols && rowIndex == 0 ->
                    listOf("1","2","3","4","5","6","7","8","9","0","-","+")
                symbols && rowIndex == 1 ->
                    listOf("@","#","$","%","&","*","(",")","/",":",";")
                symbols && rowIndex == 2 ->
                    listOf("=","_","\\","|","~","<",">","€","£","⌫")
                else -> row
            }

            val y = top + rowIndex*(rowHeight+rowGap)
            val totalWeight = keys.sumOf { weight(it).toDouble() }.toFloat()
            var x = horizontal
            val usable = width-horizontal*2 -
                (keys.size-1)*(3.5f*d)

            keys.forEach { raw ->
                val keyWidth = usable*weight(raw)/totalWeight
                val rect = RectF(x,y,x+keyWidth,y+rowHeight)
                val special = raw in listOf("⇧","⌫","?123","↵","🌐") ||
                    raw == "space"

                drawGlassKey(canvas,rect,special,alpha,d)

                val label = when {
                    raw == "space" -> "Türkçe"
                    shift && raw.length == 1 && raw[0].isLetter() ->
                        raw.uppercase()
                    else -> raw
                }

                textPaint.color = textColor
                textPaint.textSize = when {
                    raw == "space" -> 12*d
                    raw.length > 1 -> 12*d
                    else -> 19*d
                }
                textPaint.typeface = if (special)
                    Typeface.create("sans-serif-medium",Typeface.NORMAL)
                else Typeface.create("sans-serif",Typeface.NORMAL)

                drawCenteredText(canvas,label,rect)
                keyRects.add(rect to raw)
                x += keyWidth+3.5f*d
            }
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF) {
        val baseline = rect.centerY() -
            (textPaint.ascent()+textPaint.descent())/2
        canvas.drawText(text,rect.centerX(),baseline,textPaint)
    }

    private fun drawGlassKey(
        canvas: Canvas,
        rect: RectF,
        special: Boolean,
        alpha: Int,
        d: Float
    ) {
        val radius = roundness*d

        val topColor = if (dark) {
            if (special) Color.argb((alpha*.92f).toInt(),145,157,178)
            else Color.argb((alpha*.75f).toInt(),110,122,142)
        } else {
            if (special) Color.argb((alpha*.92f).toInt(),191,201,216)
            else Color.argb((alpha*.76f).toInt(),248,250,255)
        }

        val bottomColor = if (dark) {
            if (special) Color.argb((alpha*.72f).toInt(),82,91,107)
            else Color.argb((alpha*.67f).toInt(),61,69,83)
        } else {
            if (special) Color.argb((alpha*.78f).toInt(),157,169,188)
            else Color.argb((alpha*.70f).toInt(),215,222,233)
        }

        keyPaint.shader = LinearGradient(
            rect.left,rect.top,rect.left,rect.bottom,
            topColor,bottomColor,Shader.TileMode.CLAMP
        )
        keyPaint.setShadowLayer(2*d,0f,1.5f*d,Color.argb(45,0,0,0))
        canvas.drawRoundRect(rect,radius,radius,keyPaint)
        keyPaint.clearShadowLayer()
        keyPaint.shader = null

        edgePaint.strokeWidth = max(.7f*d,1f)
        edgePaint.color = Color.argb(
            if (dark) 110 else 150,255,255,255
        )
        canvas.drawRoundRect(rect,radius,radius,edgePaint)

        paint.color = Color.argb(
            if (dark) 42 else 80,255,255,255
        )
        val glint = RectF(
            rect.left+2*d,rect.top+1.5f*d,
            rect.right-2*d,rect.top+2.5f*d
        )
        canvas.drawRoundRect(glint,radius,radius,paint)
    }

    private fun weight(key: String): Float = when (key) {
        "space" -> 4f
        "⇧","⌫","?123","↵" -> 1.45f
        "🌐" -> 1.1f
        else -> 1f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val suggestion = suggestionRects.firstOrNull {
                it.first.contains(event.x,event.y)
            }
            val emoji = emojiRects.firstOrNull {
                it.first.contains(event.x,event.y)
            }
            val key = keyRects.firstOrNull {
                it.first.contains(event.x,event.y)
            }

            when {
                suggestion != null -> onKey(suggestion.second)
                emoji != null -> onKey(emoji.second)
                key != null -> {
                    val raw = key.second
                    onKey(
                        if (shift && raw.length == 1 && raw[0].isLetter())
                            raw.uppercase()
                        else raw
                    )
                    if (shift && raw.length == 1 && raw[0].isLetter()) {
                        shift = false
                        invalidate()
                    }
                }
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
