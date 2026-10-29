package com.walkmanart

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bgColor = Color.parseColor("#0E0E16")
    private val cardColor = Color.parseColor("#1A1A26")
    private val accent = Color.parseColor("#7C6CF0")
    private val gray = Color.parseColor("#9A9AB0")

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private val cards = ArrayList<LinearLayout>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int = 0, strokeW: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
            if (strokeW > 0) setStroke(strokeW, stroke)
        }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        root.addView(label("Portada Fondo", 28f, Color.WHITE, true))
        root.addView(label("Tu música, de fondo de pantalla.", 14f, gray).apply {
            setPadding(0, dp(4), 0, dp(18))
        })

        // estado
        statusText = label("", 14f, Color.WHITE).apply {
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(cardColor, dp(16))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(statusText)
        }, lp(bottom = 22))

        // estilos
        root.addView(label("ESTILO", 12f, gray, true).apply { setPadding(dp(4), 0, 0, dp(8)) })
        for (s in ALL_STYLES) {
            val card = styleCard(s)
            cards.add(card)
            root.addView(card, lp(bottom = 10))
        }
        refreshCards()

        // oscurecer
        val dimLabel = label("Oscurecer fondo: ${Prefs.dim(this)}%", 14f, Color.WHITE).apply {
            setPadding(dp(4), dp(14), 0, dp(4))
        }
        root.addView(dimLabel)
        root.addView(SeekBar(this).apply {
            max = 80
            progress = Prefs.dim(this@MainActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    dimLabel.text = "Oscurecer fondo: $p%"
                    prefs.edit().putInt(Prefs.KEY_DIM, p).apply()
                }

                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        // botones
        root.addView(button("Aplicar fondo animado", true) { applyWallpaper() }, lp(top = 18, height = 52))
        root.addView(button("Dar acceso a notificaciones", false) {
            tryStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, lp(top = 10, height = 52))

        root.addView(label("EXTRAS", 12f, gray, true).apply { setPadding(dp(4), dp(24), 0, dp(8)) })
        root.addView(button("Usar inicio minimalista", false) {
            tryStart(Intent(Settings.ACTION_HOME_SETTINGS))
        }, lp(height = 52))
        root.addView(label(
            "Elige \"Inicio minimalista\" como app de inicio. Para volver al de Sony, elige \"Launcher\" en esa misma pantalla.",
            12f, gray
        ).apply { setPadding(dp(4), dp(6), dp(4), dp(10)) })
        root.addView(label(
            "Widget: mantén pulsada la pantalla de inicio de Sony y agrega \"Ahora suena\" (si tu launcher lo permite).",
            12f, gray
        ).apply { setPadding(dp(4), 0, dp(4), 0) })

        root.addView(label(
            "Si la portada no aparece, da el acceso a notificaciones y vuelve a elegir el fondo.",
            12f, gray
        ).apply { setPadding(dp(4), dp(14), dp(4), 0) })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun lp(top: Int = 0, bottom: Int = 0, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (height > 0) dp(height) else height
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun thumb(id: Int): GradientDrawable {
        val d = GradientDrawable()
        d.orientation = GradientDrawable.Orientation.TL_BR
        when (id) {
            Styles.COVER -> d.colors = intArrayOf(Color.parseColor("#2D3436"), Color.parseColor("#636E72"))
            Styles.BLUR_CARD -> d.colors = intArrayOf(Color.parseColor("#6C5CE7"), Color.parseColor("#0984E3"))
            Styles.AURORA -> d.colors = intArrayOf(
                Color.parseColor("#00B894"), Color.parseColor("#6C5CE7"), Color.parseColor("#FD79A8")
            )
            Styles.VINYL -> d.colors = intArrayOf(Color.parseColor("#111111"), Color.parseColor("#555555"))
            Styles.CLOCK -> d.colors = intArrayOf(Color.parseColor("#232526"), Color.parseColor("#414345"))
            Styles.DAYTIME -> d.colors = intArrayOf(Color.parseColor("#FF8A65"), Color.parseColor("#5C6BC0"))
            Styles.LIGHT -> d.colors = intArrayOf(Color.parseColor("#F5F7FA"), Color.parseColor("#C3CFE2"))
            else -> d.colors = intArrayOf(Color.parseColor("#FD79A8"), Color.parseColor("#FDCB6E"))
        }
        if (id == Styles.VINYL) d.shape = GradientDrawable.OVAL else d.cornerRadius = dp(14).toFloat()
        return d
    }

    private fun styleCard(s: StyleInfo): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            tag = s.id
        }
        row.addView(View(this).apply { background = thumb(s.id) }, LinearLayout.LayoutParams(dp(56), dp(56)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(s.name, 16f, Color.WHITE, true))
        col.addView(label(s.desc, 13f, gray).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(14)
        })
        row.setOnClickListener {
            prefs.edit().putInt(Prefs.KEY_STYLE, s.id).apply()
            refreshCards()
        }
        return row
    }

    private fun refreshCards() {
        val cur = Prefs.style(this)
        for (c in cards) {
            val sel = c.tag == cur
            c.background = rounded(cardColor, dp(16), if (sel) accent else cardColor, dp(2))
        }
    }

    private fun button(text: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(if (filled) Color.WHITE else accent)
        background = if (filled) rounded(accent, dp(14)) else rounded(Color.TRANSPARENT, dp(14), accent, dp(2))
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun updateStatus() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        val access = enabled.contains(packageName)
        val info = WallpaperManager.getInstance(this).wallpaperInfo
        val active = info != null && info.packageName == packageName
        statusText.text = "Acceso a notificaciones: " + (if (access) "activo" else "falta darlo") +
            "\nFondo animado: " + (if (active) "en uso" else "sin aplicar")
    }

    private fun applyWallpaper() {
        val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, ArtWallpaperService::class.java)
        )
        tryStart(i)
    }

    private fun tryStart(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }
}
