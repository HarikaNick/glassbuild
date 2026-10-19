package com.erol.glasskeys

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Switch
import android.widget.ScrollView

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("glass_keys_prefs", Context.MODE_PRIVATE) }
    private val ink = Color.rgb(29, 35, 47)
    private val muted = Color.rgb(91, 99, 113)
    private lateinit var root: LinearLayout
    private lateinit var preview: TextView
    private lateinit var opacityLabel: TextView
    private lateinit var roundnessLabel: TextView
    private lateinit var heightLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(28))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(242, 246, 252), Color.rgb(213, 224, 239)))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        root.addView(TextView(this).apply {
            text = "Glass Keys"
            textSize = 30f
            setTextColor(ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "iOS 26 esintili · Türkçe Q"
            textSize = 15f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(18))
        })
        preview = TextView(this).apply {
            text = "q  w  e  r  t  y  u  ı  o  p  ğ  ü\n\na  s  d  f  g  h  j  k  l  ş  i\n\n⇧  z  x  c  v  b  n  m  ö  ç  ⌫\n\n?123      🌐       Türkçe       Bitti"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(16), dp(10), dp(16))
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
        section("GÖRÜNÜM")
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Sistem" to "system", "Koyu" to "dark", "Açık" to "light").forEach { (label, value) ->
            themeRow.addView(button(label) {
                prefs.edit().putString("theme", value).apply()
                updatePreview()
                updateThemeButtons(themeRow)
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (value != "system") marginStart = dp(8) })
        }
        root.addView(themeRow)
        opacityLabel = TextView(this).apply { setTextColor(ink); textSize = 14f }
        root.addView(labelRow("Tuş saydamlığı", opacityLabel).apply { setPadding(0, dp(18), 0, 0) })
        root.addView(SeekBar(this).apply {
            max = 65
            progress = (prefs.getInt("opacity", 92) - 35).coerceIn(0, 65)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    val v = p + 35
                    opacityLabel.text = "$v%"
                    if (user) { prefs.edit().putInt("opacity", v).apply(); updatePreview() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, dp(42)))
        heightLabel = TextView(this).apply { setTextColor(ink); textSize = 14f }
        root.addView(labelRow("Klavye yüksekliği", heightLabel).apply { setPadding(0, dp(18), 0, 0) })
        root.addView(SeekBar(this).apply {
            max = 16
            progress = (prefs.getInt("keyboard_height", 42) - 34).coerceIn(0, 16)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    val value = p + 34
                    heightLabel.text = when {
                        value < 39 -> "$value dp · Kompakt"
                        value > 45 -> "$value dp · Yüksek"
                        else -> "$value dp · Standart"
                    }
                    if (user) prefs.edit().putInt("keyboard_height", value).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(TextView(this).apply {
            text = "Değişiklik, klavyeyi kapatıp yeniden açınca uygulanır."
            textSize = 12f
            setTextColor(muted)
        })
        val spacingLabel = TextView(this).apply { setTextColor(ink); textSize = 14f }
        root.addView(labelRow("Tuşlar arası boşluk", spacingLabel).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(SeekBar(this).apply {
            max = 5
            progress = prefs.getInt("key_spacing", 2).coerceIn(0, 5)
            spacingLabel.text = "${progress} dp"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    spacingLabel.text = "$p dp"
                    if (user) prefs.edit().putInt("key_spacing", p).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, dp(42)))
        roundnessLabel = TextView(this).apply { setTextColor(ink); textSize = 14f }
        root.addView(labelRow("Tuş yuvarlaklığı", roundnessLabel).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(SeekBar(this).apply {
            max = 13
            progress = (prefs.getInt("roundness", 9) - 5).coerceIn(0, 13)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    val v = p + 5
                    roundnessLabel.text = "$v / 18"
                    if (user) { prefs.edit().putInt("roundness", v).apply(); updatePreview() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(-1, dp(42)))
        heightLabel.text = "${prefs.getInt("keyboard_height", 42)} dp · Standart"
        opacityLabel.text = "${prefs.getInt("opacity", 92)}%"
        roundnessLabel.text = "${prefs.getInt("roundness", 9)} / 18"
        root.addView(Switch(this).apply {
            text = "Hafif tuş titreşimi"
            setTextColor(ink)
            isChecked = prefs.getBoolean("vibration", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("vibration", checked).apply() }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(Switch(this).apply {
            text = "Harflerin üstünde sayı satırı"
            setTextColor(ink)
            isChecked = prefs.getBoolean("number_row", false)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("number_row", checked).apply() }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        section("TÜRKÇE TAHMİN VE BELLEK")
        root.addView(Switch(this).apply {
            text = "Yazdıklarımdan öğren"
            setTextColor(ink)
            isChecked = getSharedPreferences("glass_keys_language", Context.MODE_PRIVATE)
                .getBoolean("personal_learning", true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences("glass_keys_language", Context.MODE_PRIVATE)
                    .edit().putBoolean("personal_learning", checked).apply()
            }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(button("Öğrenilen kelime belleğini temizle") {
            val languagePrefs = getSharedPreferences("glass_keys_language", Context.MODE_PRIVATE)
            val enabled = languagePrefs.getBoolean("personal_learning", true)
            languagePrefs.edit().clear().putBoolean("personal_learning", enabled).apply()
            android.widget.Toast.makeText(this, "Kişisel kelime belleği temizlendi", android.widget.Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })
        root.addView(button("Kişisel kelimeleri görüntüle / tek tek sil") {
            showLearnedWordsDialog()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })
        section("KURULUM")
        root.addView(button("Klavyeyi etkinleştir") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
        root.addView(button("Glass Keys'i seç") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(TextView(this).apply {
            text = "Gizlilik: tuş vuruşları kaydedilmez. Ayarlar cihazında yerel olarak saklanır."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(18), 0, 0)
        })
        setContentView(scroll)
        updatePreview()
        updateThemeButtons(themeRow)
    }

    private fun showLearnedWordsDialog() {
        val languagePrefs = getSharedPreferences("glass_keys_language", Context.MODE_PRIVATE)
        val words = languagePrefs.getString("learned_words", "").orEmpty()
            .split('\u001F')
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        if (words.isEmpty()) {
            android.widget.Toast.makeText(this, "Kişisel sözlükte henüz kelime yok", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Kişisel kelimeler (" + words.size + ")")
            .setItems(words.toTypedArray()) { _, index ->
                val selected = words[index]
                AlertDialog.Builder(this)
                    .setTitle("Kelimeyi sil?")
                    .setMessage("\"$selected\" kişisel sözlükten ve öneri belleğinden silinsin mi?")
                    .setNegativeButton("Vazgeç", null)
                    .setPositiveButton("Sil") { _, _ ->
                        val current = languagePrefs.getString("learned_words", "").orEmpty()
                            .split('\u001F')
                            .filter { it.isNotBlank() && !it.equals(selected, ignoreCase = true) }
                        val editor = languagePrefs.edit()
                            .putString("learned_words", current.joinToString("\u001F"))
                        languagePrefs.all.keys.filter { key ->
                            key.startsWith("pair:") &&
                                (key.substringAfter('>').equals(selected, ignoreCase = true) ||
                                 key.substringAfter(':').substringBefore('>').equals(selected, ignoreCase = true))
                        }.forEach { editor.remove(it) }
                        editor.apply()
                        android.widget.Toast.makeText(this, "Kelime silindi", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    private fun section(label: String) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(muted)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(10))
        })
    }

    private fun labelRow(label: String, value: TextView) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@MainActivity).apply { text = label; textSize = 14f; setTextColor(ink) },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(value)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(ink)
        background = card(Color.argb(220, 255, 255, 255), 22)
        setOnClickListener { action() }
    }

    private fun updateThemeButtons(row: LinearLayout) {
        val selected = prefs.getString("theme", "system") ?: "system"
        listOf("system", "dark", "light").forEachIndexed { i, v ->
            row.getChildAt(i)?.background = card(
                if (v == selected) Color.rgb(178, 198, 224) else Color.argb(210, 255, 255, 255), 20)
        }
    }

    private fun updatePreview() {
        val mode = prefs.getString("theme", "system") ?: "system"
        val dark = mode == "dark" || (mode == "system" &&
            (resources.configuration.uiMode and 0x30) == 0x20)
        val opacity = prefs.getInt("opacity", 92)
        preview.setTextColor(if (dark) Color.WHITE else ink)
        preview.background = card(
            if (dark) Color.argb((opacity * 2.35f).toInt().coerceIn(80, 235), 43, 46, 54)
            else Color.argb((opacity * 2.35f).toInt().coerceIn(80, 235), 247, 249, 253),
            prefs.getInt("roundness", 9) + 8)
    }

    private fun card(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(90, 255, 255, 255))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
