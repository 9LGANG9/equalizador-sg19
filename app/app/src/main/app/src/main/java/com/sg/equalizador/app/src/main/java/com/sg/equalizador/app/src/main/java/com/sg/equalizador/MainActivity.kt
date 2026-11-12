package com.sg.equalizador

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private var minLvl = -1500
    private var maxLvl = 1500
    private var freqs = listOf(60, 230, 910, 3600, 14000)
    private val bars = ArrayList<SeekBar>()
    private val labels = ArrayList<TextView>()
    private lateinit var status: TextView

    private val BG = Color.parseColor("#161615")
    private val FG = Color.parseColor("#EFEEE9")
    private val MUTED = Color.parseColor("#9A9890")
    private val ACCENT = Color.parseColor("#FF7A45")

    private val presets = linkedMapOf<String, (Float) -> Float>(
        "Plano" to { _: Float -> 0f },
        "Graves" to { p: Float -> maxOf(0f, 1f - p * 2.5f) },
        "Agudos" to { p: Float -> maxOf(0f, p * 2.5f - 1.5f) },
        "Rock" to { p: Float -> abs(p - 0.5f) * 1.6f - 0.2f },
        "Pop" to { p: Float -> 0.7f - abs(p - 0.5f) * 2.4f },
        "Voz" to { p: Float -> 0.8f - abs(p - 0.55f) * 3f }
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun readBands() {
        try {
            val e = Equalizer(0, 0)
            val n = e.numberOfBands.toInt()
            freqs = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            minLvl = e.bandLevelRange[0].toInt()
            maxLvl = e.bandLevelRange[1].toInt()
            e.release()
        } catch (_: Exception) {}
    }

    private fun labelText(i: Int, lvl: Int): String {
        val f = freqs[i]
        val fs = if (f >= 1000) String.format("%.1f kHz", f / 1000f) else "$f Hz"
        return String.format("%s    %+.1f dB", fs, lvl / 100f)
    }

    private fun setLevel(i: Int, lvl: Int, moveBar: Boolean) {
        prefs.edit().putInt("band_$i", lvl).apply()
        labels[i].text = labelText(i, lvl)
        if (moveBar) bars[i].progress = lvl - minLvl
    }

    private fun startSvc(action: String) {
        try {
            startForegroundService(Intent(this, EqService::class.java).setAction(action))
        } catch (_: Exception) {}
    }

    private fun push() {
        if (prefs.getBoolean("enabled", false)) startSvc(EqService.ACTION_UPDATE)
    }

    private fun refreshStatus() {
        Handler(Looper.getMainLooper()).postDelayed({
            status.text = if (prefs.getBoolean("enabled", false))
                prefs.getString("status", "Iniciando…") else "Desactivado"
        }, 800)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("eq", MODE_PRIVATE)
        readBands()
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val scroll = ScrollView(this).apply { setBackgroundColor(BG) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(24))
        }
        scroll.addView(col)
        col.addView(text("🎚️ Equalizador de SG", 24f, FG, true))

        val sw = Switch(this).apply {
            text = "Activar ecualizador"; textSize = 16f; setTextColor(FG)
            isChecked = prefs.getBoolean("enabled", false)
            setPadding(0, dp(16), 0, dp(8))
        }
        col.addView(sw)
        status = text(if (sw.isChecked) prefs.getString("status", "") ?: "" else "Desactivado", 13f, MUTED)
        col.addView(status)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hs = HorizontalScrollView(this).apply { addView(row); setPadding(0, dp(12), 0, dp(12)) }
        col.addView(hs)

        for (i in freqs.indices) {
            val l = text("", 14f, FG)
            l.setPadding(0, dp(10), 0, 0)
            val sb = SeekBar(this).apply {
                max = maxLvl - minLvl
                progress = prefs.getInt("band_$i", 0).coerceIn(minLvl, maxLvl) - minLvl
                progressTintList = ColorStateList.valueOf(ACCENT)
                thumbTintList = ColorStateList.valueOf(ACCENT)
            }
            bars.add(sb); labels.add(l)
            col.addView(l)
            col.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            l.text = labelText(i, sb.progress + minLvl)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { setLevel(i, p + minLvl, false); push() }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }

        for ((name, f) in presets) {
            row.addView(Button(this).apply {
                text = name
                setOnClickListener {
                    val n = freqs.size
                    for (i in 0 until n) {
                        val p = if (n == 1) 0f else i / (n - 1f)
                        setLevel(i, (f(p) * maxLvl * 0.8f).toInt().coerceIn(minLvl, maxLvl), true)
                    }
                    push()
                }
            })
        }

        col.addView(text("Hecho por SG", 12f, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        sw.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("enabled", on).apply()
            startSvc(if (on) EqService.ACTION_START else EqService.ACTION_STOP)
            refreshStatus()
        }
        setContentView(scroll)
    }
}
