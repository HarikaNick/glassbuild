package com.erol.glasskeys

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Glass Keys — clean native Android IME implementation.
 * This replaces the previous Canvas keyboard implementation instead of patching it.
 */
class GlassKeyboardService : InputMethodService() {
    private lateinit var keyboardRoot: LinearLayout
    private lateinit var panel: LinearLayout
    private var shift = false
    private var capsLock = false
    private var symbols = false
    private var emojiMode = false
    private var passwordInput = false
    private var suggestionsRow: LinearLayout? = null
    private var lastSpaceAt = 0L

    private val letterRows = listOf(
        listOf("q","w","e","r","t","y","u","ı","o","p","ğ","ü"),
        listOf("a","s","d","f","g","h","j","k","l","ş","i"),
        listOf("shift","z","x","c","v","b","n","m","ö","ç","delete")
    )
    private val symbolRows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","₺","&","*","-","+","(",")","/"),
        listOf("=","%","€","£",":",";","!","?","delete")
    )
    private val emojis = listOf(
        "😀","😃","😄","😁","😆","😅","😂","🤣","🥹","😊","😇","🙂","😉","😍","🥰","😘",
        "😋","😎","🤩","🥳","😭","😢","😡","🤔","🤗","🤫","😴","🤯","😱","👍","👏","🙏",
        "💪","🫶","❤️","🧡","💛","💚","💙","💜","🖤","🤍","💔","🔥","✨","⭐","🌙","☀️",
        "🎉","💯","🍀","🌹","🍕","☕","⚽","🎮","🚗","✈️","🐱","🐶"
    )

    override fun onCreateInputView(): View {
        configureGlassWindow()
        keyboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(7))
            setBackgroundColor(Color.TRANSPARENT)
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(4))
            background = glassPanel()
        }
        keyboardRoot.addView(panel, LinearLayout.LayoutParams(-1, -2))
        rebuildKeyboard()
        return keyboardRoot
    }

    override fun onWindowShown() {
        super.onWindowShown()
        configureGlassWindow()
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

    private fun configureGlassWindow() {
        val w = window?.window ?: return
        w.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                w.attributes = w.attributes.apply {
                    blurBehindRadius = dp(36)
                    dimAmount = 0.035f
                }
                w.setDimAmount(0.035f)
            } catch (_: Exception) { }
        }
    }

    private fun configureForInput(info: EditorInfo?) {
        val type = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        passwordInput = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val caps = cls == InputType.TYPE_CLASS_TEXT &&
            (type and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)) != 0
        if (caps && !passwordInput) shift = true
        if (::panel.isInitialized) rebuildKeyboard()
    }

    private fun rebuildKeyboard() {
        if (!::panel.isInitialized) return
        panel.removeAllViews()
        suggestionsRow = null
        if (!emojiMode && !passwordInput) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
            }
            suggestionsRow = row
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(34)))
            refreshSuggestions()
        }

        if (emojiMode) {
            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            emojis.chunked(8).forEach { group ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                group.forEach { emoji ->
                    row.addView(keyView(emoji, false, 27f) { commit(emoji) },
                        LinearLayout.LayoutParams(0, dp(38), 1f))
                }
                grid.addView(row, LinearLayout.LayoutParams(-1, dp(39)))
            }
            panel.addView(grid)
            panel.addView(utilityRow(listOf("ABC" to { emojiMode = false; rebuildKeyboard() }, "⌫" to { delete() })))
            return
        }

        val rows = if (symbols) symbolRows else letterRows
        rows.forEachIndexed { index, row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            if (index == 1) line.setPadding(dp(9), 0, dp(9), 0)
            if (index == 2) {
                line.addView(keyView(if (shift) "⇧" else "⇧", true, 17f) { toggleShift() },
                    LinearLayout.LayoutParams(0, dp(43), 1.35f))
            }
            row.filterNot { it == "shift" || it == "delete" }.forEach { key ->
                line.addView(keyView(if (shift && !symbols) key.uppercase() else key, false, 20f) {
                    if (key == "shift" || key == "delete") return@keyView
                    commit(key)
                    if (shift && !capsLock && key.length == 1 && key[0].isLetter()) {
                        shift = false
                        rebuildKeyboard()
                    }
                }, LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                    setMargins(dp(1.5f), dp(2), dp(1.5f), dp(2))
                })
            }
            if (index == 2) {
                line.addView(keyView("⌫", true, 17f) { delete() },
                    LinearLayout.LayoutParams(0, dp(43), 1.35f))
            }
            panel.addView(line, LinearLayout.LayoutParams(-1, dp(47)))
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottom.addView(keyView(if (symbols) "ABC" else "?123", true, 14f) {
            symbols = !symbols
            rebuildKeyboard()
        }, LinearLayout.LayoutParams(0, dp(42), 1.45f).apply { setMargins(dp(1), dp(2), dp(3), dp(2)) })
        bottom.addView(keyView("🌐", true, 17f) {
            try { switchToNextInputMethod(false) } catch (_: Exception) { emojiMode = true; rebuildKeyboard() }
        }, LinearLayout.LayoutParams(0, dp(42), 1.05f).apply { setMargins(dp(1), dp(2), dp(3), dp(2)) })
        bottom.addView(keyView(",", false, 19f) { commit(",") },
            LinearLayout.LayoutParams(0, dp(42), 0.8f).apply { setMargins(dp(1), dp(2), dp(3), dp(2)) })
        bottom.addView(keyView("Türkçe", false, 14f) { commit(" ") },
            LinearLayout.LayoutParams(0, dp(42), 4.3f).apply { setMargins(dp(1), dp(2), dp(3), dp(2)) })
        bottom.addView(keyView(".", false, 19f) { commit(".") },
            LinearLayout.LayoutParams(0, dp(42), 0.8f).apply { setMargins(dp(1), dp(2), dp(3), dp(2)) })
        bottom.addView(keyView(actionLabel(), true, 14f, true) { performEnter() },
            LinearLayout.LayoutParams(0, dp(42), 1.55f).apply { setMargins(dp(1), dp(2), dp(1), dp(2)) })
        panel.addView(bottom, LinearLayout.LayoutParams(-1, dp(46)))
        refreshSuggestions()
    }

    private fun utilityRow(items: List<Pair<String, () -> Unit>>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        items.forEach { (label, action) ->
            addView(keyView(label, true, 16f, label == "ABC", action),
                LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
    }

    private fun keyView(label: String, special: Boolean, size: Float, accent: Boolean = false, action: () -> Unit) =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = size
            typeface = Typeface.create("sans-serif", if (special) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(foreground())
            background = keyBackground(special, accent)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                tapFeedback()
                action()
            }
        }

    private fun refreshSuggestions() {
        val row = suggestionsRow ?: return
        row.removeAllViews()
        if (passwordInput || emojiMode) return
        val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        val prefix = Regex("[\\p{L}\\p{M}]+$").find(before)?.value.orEmpty()
        val words = if (prefix.isBlank()) listOf(" ", " ", " ") else listOf(prefix, "", "")
        words.take(3).forEachIndexed { i, word ->
            val cell = TextView(this).apply {
                text = word
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(foreground())
                setOnClickListener {
                    if (word.isNotBlank()) {
                        val current = Regex("[\\p{L}\\p{M}]+$").find(
                            currentInputConnection?.getTextBeforeCursor(100, 0)?.toString().orEmpty()
                        )?.value.orEmpty()
                        if (current.isNotEmpty()) currentInputConnection?.deleteSurroundingText(current.length, 0)
                        commit(word + " ")
                    }
                }
            }
            row.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
            if (i < 2) row.addView(View(this).apply {
                setBackgroundColor(if (isDark()) 0x44FFFFFF else 0x33000000)
            }, LinearLayout.LayoutParams(dp(1), dp(18)))
        }
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        refreshSuggestions()
    }

    private fun delete() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
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
        } else ic.commitText("\n", 1)
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
        if (!getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE).getBoolean("vibration", true)) return
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(8, 24))
                else {
                    @Suppress("DEPRECATION")
                    v.vibrate(8)
                }
            }
        } catch (_: Exception) { }
    }

    private fun glassPanel(): GradientDrawable {
        val a = getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE).getInt("opacity", 92)
        val alpha = (a * 2.1f).roundToInt().coerceIn(75, 220)
        val color = if (isDark()) Color.argb(alpha, 36, 39, 47) else Color.argb(alpha, 228, 233, 242)
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(27).toFloat()
            setStroke(dp(1), if (isDark()) 0x44FFFFFF else 0x66FFFFFF)
        }
    }

    private fun keyBackground(special: Boolean, accent: Boolean): GradientDrawable {
        val alpha = getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE).getInt("opacity", 92)
            .let { (it * 2.35f).roundToInt().coerceIn(90, 235) }
        val color = when {
            accent -> if (isDark()) Color.argb(alpha, 87, 125, 179) else Color.argb(alpha, 173, 200, 238)
            special -> if (isDark()) Color.argb(alpha, 75, 79, 89) else Color.argb(alpha, 195, 204, 218)
            isDark() -> Color.argb(alpha, 66, 69, 78)
            else -> Color.argb(alpha, 250, 251, 254)
        }
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), if (isDark()) 0x22FFFFFF else 0x44FFFFFF)
        }
    }

    private fun foreground() = if (isDark()) Color.WHITE else Color.rgb(31, 36, 45)
    private fun isDark(): Boolean {
        val mode = getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE).getString("theme", "system")
        return when (mode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
