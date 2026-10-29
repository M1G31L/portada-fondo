package com.walkmanart

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * Pantalla de inicio minimalista: reloj, lo que suena y tres accesos.
 * Para volver al launcher de Sony basta con cambiar la app de inicio en Ajustes.
 */
class HomeActivity : Activity() {

    private val gray = Color.parseColor("#B8B8CC")
    private lateinit var cover: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var play: TextView
    private val hubListener: () -> Unit = { runOnUiThread { bindNow() } }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat()
        setColor(color)
    }

    private fun txt(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(28))
            setBackgroundColor(Color.argb(110, 0, 0, 0))
        }

        // reloj
        root.addView(TextClock(this).apply {
            format12Hour = null
            format24Hour = "HH:mm"
            textSize = 72f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        })
        root.addView(TextClock(this).apply {
            format12Hour = "EEEE, d 'de' MMMM"
            format24Hour = "EEEE, d 'de' MMMM"
            textSize = 16f
            setTextColor(gray)
        })

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // ahora suena
        val now = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.argb(190, 22, 22, 34), 20)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        cover = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        now.addView(cover, LinearLayout.LayoutParams(dp(64), dp(64)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = txt("Nada sonando", 16f, Color.WHITE, true).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        artist = txt("", 13f, gray).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        col.addView(title)
        col.addView(artist)
        now.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        })
        now.addView(ctrl("⏮") { MediaHub.prev() })
        play = ctrl("▶") { MediaHub.playPause() }
        now.addView(play)
        now.addView(ctrl("⏭") { MediaHub.next() })
        root.addView(now, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        // accesos
        root.addView(entry("Spotify", "#1DB954") { launch("com.spotify.music") })
        root.addView(entry("Reproductor Walkman", "#7C6CF0") { launch("com.sony.walkman.highresmediaplayer") })
        root.addView(entry("Ajustes", "#5B6B8C") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        })
        root.addView(txt("Portada Fondo  ·  Cambiar de inicio", 12f, gray).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            setOnClickListener {
                startActivity(Intent(this@HomeActivity, MainActivity::class.java))
            }
            setOnLongClickListener {
                try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {}
                true
            }
        })

        setContentView(root)
    }

    private fun ctrl(sym: String, f: () -> Unit) = txt(sym, 24f, Color.WHITE).apply {
        gravity = Gravity.CENTER
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(48))
    }

    private fun entry(name: String, color: String, f: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(Color.argb(190, 22, 22, 34), 16)
        setPadding(dp(16), 0, dp(16), 0)
        addView(View(this@HomeActivity).apply { background = rounded(Color.parseColor(color), 8) },
            LinearLayout.LayoutParams(dp(12), dp(12)))
        addView(txt(name, 18f, Color.WHITE).apply { setPadding(dp(14), 0, 0, 0) })
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)).apply {
            bottomMargin = dp(10)
        }
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) {
            Toast.makeText(this, "No se encontró la app", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(i)
        }
    }

    private fun bindNow() {
        val has = MediaHub.hasSession && MediaHub.title.isNotEmpty()
        title.text = if (has) MediaHub.title else "Nada sonando"
        artist.text = if (has) MediaHub.artist else "Da acceso a notificaciones en Portada Fondo"
        val a = MediaHub.art
        if (a != null) cover.setImageBitmap(a) else cover.setImageDrawable(rounded(Color.parseColor("#2A2A3C"), 12))
        play.text = if (MediaHub.playing) "⏸" else "▶"
    }

    override fun onStart() {
        super.onStart()
        MediaHub.ensure(this)
        MediaHub.listeners.add(hubListener)
        bindNow()
    }

    override fun onStop() {
        MediaHub.listeners.remove(hubListener)
        super.onStop()
    }

    override fun onBackPressed() { /* es la pantalla de inicio */ }
}
