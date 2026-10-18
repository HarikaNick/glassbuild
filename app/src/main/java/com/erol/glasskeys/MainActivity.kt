package com.erol.glasskeys

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(245,247,251), Color.rgb(224,231,241))).apply { cornerRadius = 0f }
        }
        val title = TextView(this).apply {
            text = "Glass Keys"
            textSize = 30f
            setTextColor(Color.rgb(29,35,47))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val desc = TextView(this).apply {
            text = "iOS 26 esintili cam görünüm · Türkçe Q\n\nKlavye önce etkinleştirilmeli, ardından varsayılan giriş yöntemi olarak seçilmeli."
            textSize = 16f
            setTextColor(Color.rgb(75,83,99))
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 28)
        }
        val enable = makeButton("1 · Klavyeyi etkinleştir") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        val choose = makeButton("2 · Glass Keys'i seç") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        val note = TextView(this).apply {
            text = "Gizlilik: Bu başlangıç sürümü internet izni istemez ve tuş vuruşlarını kaydetmez."
            textSize = 13f
            setTextColor(Color.rgb(91,99,113))
            gravity = Gravity.CENTER
            setPadding(0, 26, 0, 0)
        }
        root.addView(title)
        root.addView(desc)
        root.addView(enable, LinearLayout.LayoutParams(-1, 54).apply { bottomMargin = 12 })
        root.addView(choose, LinearLayout.LayoutParams(-1, 54))
        root.addView(note)
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.rgb(31,39,54))
        background = GradientDrawable().apply {
            setColor(Color.argb(215,255,255,255))
            cornerRadius = 24f
            setStroke(1, Color.argb(100,255,255,255))
        }
        setOnClickListener { action() }
    }
}
