package com.erol.glasskeys

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/** Native Android IME. UI is rebuilt from preferences and current editor capabilities. */
class GlassKeyboardService : InputMethodService() {
    private lateinit var panel: LinearLayout
    private var shift = false
    private var capsLock = false
    private var symbols = false
    private var emojiMode = false
    private var passwordInput = false
    private var learningAllowed = true
    private var numericInput = false
    private var suggestionsRow: LinearLayout? = null
    private var lastSuggestions = emptyList<String>()
    private val predictor by lazy { TurkishPredictionEngine(this) }

    private val letterRows = listOf(
        listOf("q","w","e","r","t","y","u","ı","o","p","ğ","ü"),
        listOf("a","s","d","f","g","h","j","k","l","ş","i"),
        listOf("z","x","c","v","b","n","m","ö","ç")
    )
    private val symbolRows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","₺","&","*","-","+","(",")","/"),
        listOf("=","%","€","£",":",";","!","?")
    )
    private val numberRows = listOf(
        listOf("1","2","3"),
        listOf("4","5","6"),
        listOf("7","8","9"),
        listOf("-","0",".")
    )
    private val dictionary = listOf(
        "acaba","açık","adım","akşam","ama","ancak","artık","aslında","aynen",
        "bana","bazen","belki","ben","beni","benim","beraber","bir","biraz","biri","birkaç","biz","bugün","büyük",
        "cevap","çok","çünkü","daha","değil","demek","diye","doğru","dün",
        "elbette","en","evet","ev","fazla","fakat","gibi","güzel","hangi","hala","hemen","her","herkes","hiç",
        "için","iki","ilk","ile","inşallah","iş","iyi","kadar","kendi","kesin","ki","kim","kolay","konu",
        "lazım","madem","maalesef","merhaba","mesela","mi","mı","miyim","miyim","mutlaka",
        "nasıl","neden","ne","neden","nerede","neyse","olabilir","olacak","oldu","oldukça","olmak","olsun","onu","orada","önce",
        "şey","şimdi","şöyle","tabii","tamam","tarafından","tekrar"," teşekkür","tek","telefon","tüm",
        "ve","veya","ya","yani","yapabilir","yapacağım","yapmak","yardım","yeni","yerine","yok","zaman"
    ).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private val emojis = listOf(
        "😀","😃","😄","😁","😆","😅","😂","🤣","🥹","😊","😇","🙂","😉","😍","🥰","😘",
        "😋","😎","🤩","🥳","😭","😢","😡","🤔","🤗","🤫","😴","🤯","😱","👍","👏","🙏",
        "💪","🫶","❤️","🧡","💛","💚","💙","💜","🖤","🤍","💔","🔥","✨","⭐","🌙","☀️",
        "🎉","💯","🍀","🌹","🍕","☕","⚽","🎮","🚗","✈️","🐱","🐶"
    )

    override fun onCreateInputView(): View {
        configureGlassWindow()
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = panelBackground()
        }
        rebuildKeyboard()
        return panel
    }

    override fun onWindowShown() {
        super.onWindowShown()
        configureGlassWindow()
        rebuildKeyboard()
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        configureForInput(info)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        configureForInput(info)
        refreshSuggestions()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshSuggestions()
    }

    private fun configureGlassWindow() {
        val w = window?.window ?: return
        // Do not blur the entire app behind the keyboard. Glass is rendered by
        // the keyboard's own translucent surfaces, not a window-wide blur flag.
        w.setBackgroundDrawableResource(android.R.color.transparent)
        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                w.attributes = w.attributes.apply {
                    blurBehindRadius = 0
                    dimAmount = 0f
                }
            } catch (_: Exception) { }
        }
    }

    private fun configureForInput(info: EditorInfo?) {
        val type = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val isPassword = (cls == InputType.TYPE_CLASS_TEXT && variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val isPrivateText = cls == InputType.TYPE_CLASS_TEXT && variation in listOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )
        val appDisablesSuggestions = (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        passwordInput = isPassword || appDisablesSuggestions
        learningAllowed = !isPassword && !isPrivateText && !appDisablesSuggestions
        numericInput = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
        if (!restartingShift(info) && !capsLock) {
            shift = cls == InputType.TYPE_CLASS_TEXT &&
                (type and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)) != 0
        }
        if (passwordInput) emojiMode = false
        if (::panel.isInitialized) rebuildKeyboard()
    }

    private fun restartingShift(info: EditorInfo?) = info == null

    private fun rebuildKeyboard() {
        if (!::panel.isInitialized) return
        panel.removeAllViews()
        panel.background = panelBackground()
        suggestionsRow = null

        if (!emojiMode && !passwordInput && !numericInput) {
            suggestionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            panel.addView(suggestionsRow, LinearLayout.LayoutParams(-1, dp(34)))
            refreshSuggestions()
        }

        if (emojiMode) {
            emojis.chunked(8).forEach { group ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                group.forEach { emoji ->
                    row.addView(keyView(emoji, false, 24f) { commit(emoji) },
                        LinearLayout.LayoutParams(0, dp(39), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
                }
                panel.addView(row, LinearLayout.LayoutParams(-1, dp(41)))
            }
            panel.addView(utilityRow(listOf("ABC" to { emojiMode = false; rebuildKeyboard() }, "⌫" to { deleteOne() })))
            return
        }

        if (numericInput) {
            numberRows.forEach { row ->
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                row.forEach { key ->
                    line.addView(keyView(key, false, 22f) { commit(key) },
                        LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                }
                panel.addView(line, LinearLayout.LayoutParams(-1, dp(49)))
            }
            panel.addView(utilityRow(listOf("−" to { deleteOne() }, "⌫" to { deleteWord() }, actionLabel() to { performEnter() })))
            return
        }

        if (getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("number_row", false) && !symbols) {
            addKeyRow(listOf("1","2","3","4","5","6","7","8","9","0"), 34, 17f)
        }

        val rows = if (symbols) symbolRows else letterRows
        rows.forEachIndexed { index, keys ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                if (index == 1) setPadding(dp(7), 0, dp(7), 0)
            }
            if (index == 2) {
                line.addView(keyView("⇧", true, 17f) { toggleShift() },
                    LinearLayout.LayoutParams(0, dp(43), 1.35f).apply { setMargins(dp(1), dp(2), dp(2), dp(2)) })
            }
            keys.forEach { key ->
                val shown = if (shift && !symbols) key.uppercase(Locale("tr", "TR")) else key
                line.addView(keyView(shown, false, 19f) {
                    commit(keyForCommit(key))
                    if (shift && !capsLock && key.any { it.isLetter() }) {
                        shift = false
                        rebuildKeyboard()
                    }
                }, LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                    setMargins(dp(1), dp(2), dp(1), dp(2))
                })
            }
            if (index == 2) {
                val del = keyView("⌫", true, 17f) { deleteOne() }
                del.setOnLongClickListener { deleteWord(); true }
                line.addView(del, LinearLayout.LayoutParams(0, dp(43), 1.35f).apply { setMargins(dp(2), dp(2), dp(1), dp(2)) })
            }
            panel.addView(line, LinearLayout.LayoutParams(-1, dp(47)))
        }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun addBottom(label: String, weight: Float, special: Boolean = false, action: () -> Unit) {
            bottom.addView(keyView(label, special, if (label == "🌐") 17f else 14f, action = action),
                LinearLayout.LayoutParams(0, dp(42), weight).apply { setMargins(dp(1), dp(2), dp(1), dp(2)) })
        }
        addBottom(if (symbols) "ABC" else "?123", 1.35f, true) { symbols = !symbols; rebuildKeyboard() }
        addBottom("🌐", 0.9f, true) {
            try { switchToNextInputMethod(false) } catch (_: Exception) { emojiMode = true; rebuildKeyboard() }
        }
        addBottom(",", 0.7f) { commit(",") }
        addBottom("😊", 0.8f, true) { emojiMode = true; rebuildKeyboard() }
        addBottom("Türkçe", 3.6f) { commit(" ") }
        addBottom(".", 0.7f) { commit(".") }
        addBottom(actionLabel(), 1.4f, true) { performEnter() }
        panel.addView(bottom, LinearLayout.LayoutParams(-1, dp(46)))
    }

    private fun addKeyRow(keys: List<String>, height: Int, size: Float) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        keys.forEach { key ->
            row.addView(keyView(key, false, size) { commit(key) },
                LinearLayout.LayoutParams(0, dp(height), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
        }
        panel.addView(row, LinearLayout.LayoutParams(-1, dp(height + 2)))
    }

    private fun utilityRow(items: List<Pair<String, () -> Unit>>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        items.forEach { (label, action) ->
            addView(keyView(label, true, 16f, action = action),
                LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
    }

    private fun keyView(
        label: String,
        special: Boolean,
        size: Float,
        accent: Boolean = false,
        action: () -> Unit
    ) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = size
        typeface = Typeface.create("sans-serif", if (special) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(foreground())
        background = keyBackground(special, accent)
        isClickable = true
        isFocusable = true
        contentDescription = when (label) {
            "⌫" -> "Sil"
            "⇧" -> "Büyük harf"
            "?123" -> "Semboller"
            "Türkçe" -> "Boşluk"
            else -> label
        }
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.92f).setDuration(65).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
        setOnClickListener {
            tapFeedback()
            action()
        }
        val alternatives = alternativesFor(label)
        if (alternatives.size > 1) {
            setOnLongClickListener {
                showAlternatives(this, alternatives)
                true
            }
        }
    }

    private fun alternativesFor(label: String): List<String> {
        val lower = label.lowercase(Locale("tr", "TR"))
        val variants = when (lower) {
            "a" -> listOf("a", "â", "á", "à")
            "c" -> listOf("c", "ç", "ć")
            "g" -> listOf("g", "ğ")
            "i" -> listOf("i", "ı", "î")
            "o" -> listOf("o", "ö", "ô")
            "s" -> listOf("s", "ş")
            "u" -> listOf("u", "ü", "û")
            else -> return emptyList()
        }
        return if (label.firstOrNull()?.isUpperCase() == true)
            variants.map { it.uppercase(Locale("tr", "TR")) } else variants
    }

    private fun showAlternatives(anchor: View, variants: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = panelBackground()
        }
        val popup = PopupWindow(row, -2, dp(52), true).apply {
            isOutsideTouchable = true
            elevation = dp(12).toFloat()
            setBackgroundDrawable(panelBackground())
        }
        variants.distinct().forEach { character ->
            row.addView(keyView(character, false, 20f) {
                commit(character)
                popup.dismiss()
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        popup.showAsDropDown(anchor, 0, -anchor.height - dp(58))
    }

    private fun refreshSuggestions() {
        val row = suggestionsRow ?: return
        if (!::panel.isInitialized || passwordInput || emojiMode) return
        val before = currentInputConnection?.getTextBeforeCursor(120, 0)?.toString().orEmpty()
        val prefix = Regex("[\\p{L}\\p{M}]+$").find(before)?.value.orEmpty()
        val beforePrefix = if (prefix.isNotEmpty()) before.dropLast(prefix.length) else before
        val previousWord = Regex("[\\p{L}\\p{M}]+$").find(beforePrefix.trimEnd())?.value.orEmpty()
        val candidates = predictor.suggestions(prefix, previousWord, 3)
        val display = if (prefix.isBlank()) candidates.ifEmpty { listOf(" ", " ", " ") } else listOf(prefix) + candidates.filterNot { it.equals(prefix, true) }
        val finalWords = display.take(3)
        if (finalWords == lastSuggestions && row.childCount == finalWords.size) return
        lastSuggestions = finalWords
        row.removeAllViews()
        finalWords.forEachIndexed { i, word ->
            val cell = TextView(this).apply {
                text = word
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(foreground())
                isClickable = word.isNotBlank()
                setOnClickListener { if (word.isNotBlank()) replaceCurrentWord(word) }
            }
            row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
            if (i < finalWords.lastIndex) row.addView(View(this).apply {
                setBackgroundColor(if (isDark()) 0x33FFFFFF else 0x22000000)
            }, LinearLayout.LayoutParams(dp(1), dp(17)))
        }
    }

    private fun replaceCurrentWord(word: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(120, 0)?.toString().orEmpty()
        val current = Regex("[\\p{L}\\p{M}]+$").find(before)?.value.orEmpty()
        val previousWord = Regex("[\\p{L}\\p{M}'’-]+$").find(
            before.dropLast(current.length).trimEnd()
        )?.value.orEmpty()
        if (learningAllowed && current.isNotBlank()) predictor.learnTypedText(word, previousWord)
        if (current.isNotEmpty()) ic.deleteSurroundingText(current.length, 0)
        ic.commitText(word + " ", 1)
        lastSuggestions = emptyList()
        refreshSuggestions()
    }

    private fun keyForCommit(key: String): String =
        if (shift && !symbols) key.uppercase(Locale("tr", "TR")) else key

    private fun commit(text: String) {
        val ic = currentInputConnection
        if (text == " " && learningAllowed) {
            val before = ic?.getTextBeforeCursor(120, 0)?.toString().orEmpty()
            val currentWord = Regex("[\\p{L}\\p{M}'’-]+$").find(before)?.value.orEmpty()
            val previousWord = Regex("[\\p{L}\\p{M}'’-]+$").find(
                before.dropLast(currentWord.length).trimEnd()
            )?.value.orEmpty()
            if (currentWord.isNotBlank()) predictor.learnTypedText(currentWord, previousWord)
        }
        ic?.commitText(text, 1)
        if (text == " " && !passwordInput && !capsLock && !symbols) {
            val before = ic?.getTextBeforeCursor(8, 0)?.toString().orEmpty()
            if (Regex("[.!?][\\s]*$").containsMatchIn(before)) {
                shift = true
                rebuildKeyboard()
            }
        }
        lastSuggestions = emptyList()
        refreshSuggestions()
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
        lastSuggestions = emptyList()
        refreshSuggestions()
    }

    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        val count = Regex("\\s*\\S+$").find(before)?.value?.length ?: 0
        if (count > 0) ic.deleteSurroundingText(count, 0) else deleteOne()
        lastSuggestions = emptyList()
        refreshSuggestions()
    }

    private fun toggleShift() {
        if (shift && capsLock) {
            shift = false
            capsLock = false
        } else if (shift) {
            capsLock = true
        } else {
            shift = true
        }
        rebuildKeyboard()
    }

    private fun performEnter() {
        val ic: InputConnection = currentInputConnection ?: return
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED &&
            (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun actionLabel(): String = when (currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
        EditorInfo.IME_ACTION_GO -> "Git"
        EditorInfo.IME_ACTION_SEARCH -> "Ara"
        EditorInfo.IME_ACTION_SEND -> "Gönder"
        EditorInfo.IME_ACTION_NEXT -> "İleri"
        EditorInfo.IME_ACTION_DONE -> "Bitti"
        else -> "↵"
    }

    private fun tapFeedback() {
        if (!getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("vibration", true)) return
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(8, 24))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(8)
                }
            }
        } catch (_: Exception) { }
    }

    private fun panelBackground(): GradientDrawable {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val opacity = prefs.getInt("opacity", 92)
        val alpha = (opacity * 2.1f).roundToInt().coerceIn(75, 220)
        val color = if (isDark()) Color.argb(alpha, 29, 32, 40) else Color.argb(alpha, 228, 233, 242)
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(25).toFloat()
            setStroke(dp(1), if (isDark()) 0x55FFFFFF else 0x77FFFFFF)
        }
    }

    private fun keyBackground(special: Boolean, accent: Boolean): GradientDrawable {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val alpha = (prefs.getInt("opacity", 92) * 2.35f).roundToInt().coerceIn(90, 235)
        val radius = prefs.getInt("roundness", 9).coerceIn(5, 18)
        val color = when {
            accent -> if (isDark()) Color.argb(alpha, 89, 127, 184) else Color.argb(alpha, 173, 200, 238)
            special -> if (isDark()) Color.argb(alpha, 67, 71, 82) else Color.argb(alpha, 198, 207, 221)
            isDark() -> Color.argb(alpha, 54, 58, 68)
            else -> Color.argb(alpha, 250, 251, 254)
        }
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), if (isDark()) 0x2AFFFFFF else 0x55FFFFFF)
        }
    }

    private fun foreground() = if (isDark()) Color.WHITE else Color.rgb(31, 36, 45)

    private fun isDark(): Boolean {
        val mode = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("theme", "system")
        return when (mode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val PREFS = "glass_keys_prefs"
    }
}
