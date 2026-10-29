package com.walkmanart

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/** Reloj enorme y condensado que ocupa todo el ancho. */
class BigClock(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 255, 255, 255)
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { invalidate(); h.postDelayed(this, 1000) }
    }
    private fun text(): String {
        val c = java.util.Calendar.getInstance()
        return String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
    }
    private fun fit(w: Int) {
        p.textSize = 100f
        val tw = p.measureText("00:00")
        p.textSize = 100f * w / tw
    }
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        fit(w)
        val fm = p.fontMetrics
        setMeasuredDimension(w, (p.textSize * 0.82f).toInt())
    }
    override fun onDraw(c: Canvas) {
        fit(width)
        val digitH = p.textSize * 0.72f
        c.drawText(text(), width / 2f, digitH + p.textSize * 0.05f, p)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); h.post(tick) }
    override fun onDetachedFromWindow() { h.removeCallbacks(tick); super.onDetachedFromWindow() }
}

/** Iconos de reproducción dibujados a mano. */
class MediaIcon(ctx: Context, var kind: Int) : View(ctx) {
    companion object { const val PREV = 0; const val PLAY = 1; const val PAUSE = 2; const val NEXT = 3 }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val s = minOf(w, h) * 0.46f
        val cx = w / 2f; val cy = h / 2f
        fun tri(x: Float, dir: Int, sz: Float) {
            val path = Path()
            path.moveTo(x - dir * sz * 0.6f, cy - sz)
            path.lineTo(x + dir * sz * 0.8f, cy)
            path.lineTo(x - dir * sz * 0.6f, cy + sz)
            path.close()
            c.drawPath(path, p)
        }
        when (kind) {
            PLAY -> tri(cx, 1, s)
            PAUSE -> {
                c.drawRoundRect(cx - s * 0.75f, cy - s, cx - s * 0.2f, cy + s, 4f, 4f, p)
                c.drawRoundRect(cx + s * 0.2f, cy - s, cx + s * 0.75f, cy + s, 4f, 4f, p)
            }
            NEXT -> {
                tri(cx - s * 0.2f, 1, s * 0.85f)
                tri(cx + s * 0.6f, 1, s * 0.85f)
            }
            PREV -> {
                tri(cx + s * 0.2f, -1, s * 0.85f)
                tri(cx - s * 0.6f, -1, s * 0.85f)
            }
        }
    }
}

/** Anillo de batería. */
class BatteryRing(ctx: Context) : View(ctx) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    var level = 0
    var charging = false
    fun refresh() {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val sc = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        level = l * 100 / sc
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
        charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        invalidate()
    }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val sw = w * 0.09f
        ring.strokeWidth = sw
        val r = RectF(sw, sw, w - sw, w - sw)
        ring.color = Color.argb(60, 255, 255, 255)
        c.drawArc(r, 0f, 360f, false, ring)
        ring.color = if (level <= 15 && !charging) Color.parseColor("#FF6B6B") else Color.parseColor("#7CFFB2")
        c.drawArc(r, -90f, 360f * level / 100f, false, ring)
        txt.textSize = w * 0.30f
        c.drawText("$level", w / 2f, w / 2f + txt.textSize * 0.35f, txt)
        if (charging) {
            txt.textSize = w * 0.18f
            c.drawText("⚡", w / 2f, w * 0.30f, txt)
        }
    }
}

/** Barra de progreso fina. */
class ProgressLine(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var frac = 0f
    override fun onDraw(c: Canvas) {
        val h = height / 2f
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(0f, h - 3f, width.toFloat(), h + 3f, 6f, 6f, p)
        p.color = Color.WHITE
        c.drawRoundRect(0f, h - 3f, width * frac.coerceIn(0f, 1f), h + 3f, 6f, 6f, p)
        c.drawCircle(width * frac.coerceIn(0f, 1f), h, 8f, p)
    }
}

/**
 * Pantalla de inicio minimalista con estilo de cristal: reloj enorme, tarjeta de música,
 * y accesos a Spotify, Reproductor, Ajustes y todas las apps.
 */
class HomeActivity : Activity() {

    private val gray = Color.argb(200, 235, 235, 245)
    private lateinit var cover: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var playIcon: MediaIcon
    private lateinit var progress: ProgressLine
    private lateinit var tPos: TextView
    private lateinit var tDur: TextView
    private lateinit var ring: BatteryRing
    private lateinit var overlay: ScrollView
    private val ui = Handler(Looper.getMainLooper())
    private val hubListener: () -> Unit = { runOnUiThread { bindNow() } }
    private val ticker = object : Runnable {
        override fun run() {
            tickProgress()
            ring.refresh()
            ui.postDelayed(this, 500)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glass(r: Int, fill: Int = Color.argb(70, 255, 255, 255)) = GradientDrawable().apply {
        cornerRadius = dp(r).toFloat()
        setColor(fill)
        setStroke(dp(1), Color.argb(60, 255, 255, 255))
    }

    private fun txt(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).toInt()
        return String.format("%d:%02d", s / 60, s % 60)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(40), dp(18), dp(22))
            setBackgroundColor(Color.argb(95, 0, 0, 0))
        }

        // fecha arriba
        root.addView(TextClock(this).apply {
            format12Hour = "EEE d MMM"
            format24Hour = "EEE d MMM"
            isAllCaps = true
            textSize = 14f
            letterSpacing = 0.12f
            setTextColor(gray)
        })
        root.addView(BigClock(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
        })

        // fichas de cristal
        val tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ring = BatteryRing(this)
        val ringBox = FrameLayout(this).apply { background = glass(22); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        ringBox.addView(ring, FrameLayout.LayoutParams(dp(56), dp(56)))
        tiles.addView(ringBox, LinearLayout.LayoutParams(dp(82), dp(82)))
        val dateBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(22)
            setPadding(dp(16), 0, dp(16), 0)
        }
        dateBox.addView(TextClock(this).apply {
            format12Hour = "EEEE"; format24Hour = "EEEE"
            textSize = 15f; isAllCaps = true; setTextColor(gray); letterSpacing = 0.1f
        })
        dateBox.addView(TextClock(this).apply {
            format12Hour = "d 'de' MMMM"; format24Hour = "d 'de' MMMM"
            textSize = 20f; setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        tiles.addView(dateBox, LinearLayout.LayoutParams(0, dp(82), 1f).apply { marginStart = dp(10) })
        root.addView(tiles, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // tarjeta de música
        val now = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(26, Color.argb(85, 255, 255, 255))
            setPadding(dp(14), dp(14), dp(14), dp(10))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, dp(14).toFloat())
                }
            }
        }
        top.addView(cover, LinearLayout.LayoutParams(dp(68), dp(68)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = txt("Nada sonando", 17f, Color.WHITE, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        artist = txt("", 13f, gray).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        col.addView(title); col.addView(artist)
        top.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
        now.addView(top)

        progress = ProgressLine(this)
        now.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)).apply { topMargin = dp(10) })
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tPos = txt("0:00", 11f, gray)
        tDur = txt("0:00", 11f, gray).apply { gravity = Gravity.END }
        times.addView(tPos, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(tDur, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        now.addView(times)

        val ctl = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val prev = MediaIcon(this, MediaIcon.PREV).apply { setOnClickListener { MediaHub.prev() } }
        playIcon = MediaIcon(this, MediaIcon.PLAY).apply { setOnClickListener { MediaHub.playPause() } }
        val next = MediaIcon(this, MediaIcon.NEXT).apply { setOnClickListener { MediaHub.next() } }
        ctl.addView(prev, LinearLayout.LayoutParams(dp(70), dp(50)))
        ctl.addView(playIcon, LinearLayout.LayoutParams(dp(90), dp(50)))
        ctl.addView(next, LinearLayout.LayoutParams(dp(70), dp(50)))
        now.addView(ctl)
        root.addView(now, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16)
        })

        // dock con iconos reales
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glass(30, Color.argb(60, 255, 255, 255))
            setPadding(dp(8), dp(12), dp(8), dp(10))
        }
        dock.addView(appTile("Spotify", "com.spotify.music") { launch("com.spotify.music") }, tileLp())
        dock.addView(appTile("Reproductor", "com.sony.walkman.highresmediaplayer") { launch("com.sony.walkman.highresmediaplayer") }, tileLp())
        dock.addView(appTile("Ajustes", "com.android.settings") { startActivity(Intent(Settings.ACTION_SETTINGS)) }, tileLp())
        dock.addView(appTile("Apps", null) { showOverlay(true) }, tileLp())
        root.addView(dock)

        // capa de todas las apps
        overlay = ScrollView(this).apply {
            setBackgroundColor(Color.argb(235, 12, 12, 20))
            visibility = View.GONE
        }
        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(frame)
        buildOverlay()
    }

    private fun tileLp() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun appTile(name: String, pkg: String?, click: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setOnClickListener { click() }
        }
        val img = ImageView(this)
        var ok = false
        if (pkg != null) {
            try { img.setImageDrawable(packageManager.getApplicationIcon(pkg)); ok = true } catch (_: Exception) {}
        }
        if (!ok) {
            img.background = glass(16, Color.argb(110, 255, 255, 255))
            img.setImageDrawable(object : android.graphics.drawable.Drawable() {
                private val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                override fun draw(c: Canvas) {
                    val b = bounds
                    val u = b.width() / 10f
                    for (i in 0..2) for (j in 0..2)
                        c.drawCircle(b.left + u * (2.5f + i * 2.5f), b.top + u * (2.5f + j * 2.5f), u * 0.7f, pt)
                }
                override fun setAlpha(a: Int) {}
                override fun setColorFilter(f: android.graphics.ColorFilter?) {}
                @Suppress("OVERRIDE_DEPRECATION")
                override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
            })
            img.setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        box.addView(img, LinearLayout.LayoutParams(dp(54), dp(54)))
        box.addView(txt(name, 11f, Color.WHITE).apply {
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        })
        return box
    }

    private fun buildOverlay() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(48), dp(14), dp(20))
        }
        col.addView(txt("Todas las apps", 22f, Color.WHITE, true).apply { setPadding(dp(6), 0, 0, dp(14)) })
        val grid = GridLayout(this).apply { columnCount = 4 }
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val w = (resources.displayMetrics.widthPixels - dp(28)) / 4
        for (ri in apps) {
            val pkg = ri.activityInfo.packageName
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { showOverlay(false); launch(pkg) }
            }
            cell.addView(ImageView(this).apply { setImageDrawable(ri.loadIcon(packageManager)) },
                LinearLayout.LayoutParams(dp(48), dp(48)))
            cell.addView(txt(ri.loadLabel(packageManager).toString(), 11f, Color.WHITE).apply {
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), dp(4), dp(2), 0)
            })
            grid.addView(cell, GridLayout.LayoutParams().apply { width = w })
        }
        col.addView(grid)
        overlay.removeAllViews()
        overlay.addView(col)
    }

    private fun showOverlay(show: Boolean) {
        overlay.visibility = if (show) View.VISIBLE else View.GONE
        if (show) overlay.scrollTo(0, 0)
    }

    private fun launch(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) Toast.makeText(this, "No se encontró la app", Toast.LENGTH_SHORT).show()
        else startActivity(i)
    }

    private fun bindNow() {
        val has = MediaHub.hasSession && MediaHub.title.isNotEmpty()
        title.text = if (has) MediaHub.title else "Nada sonando"
        artist.text = if (has) MediaHub.artist else "Da acceso a notificaciones en Portada Fondo"
        val a = MediaHub.art
        if (a != null) cover.setImageBitmap(a)
        else cover.setImageDrawable(GradientDrawable().apply { setColor(Color.argb(90, 255, 255, 255)) })
        playIcon.kind = if (MediaHub.playing) MediaIcon.PAUSE else MediaIcon.PLAY
        playIcon.invalidate()
        tickProgress()
    }

    private fun tickProgress() {
        val d = MediaHub.duration
        val p = MediaHub.positionNow()
        progress.frac = if (d > 0) p.toFloat() / d else 0f
        progress.invalidate()
        tPos.text = fmt(p)
        tDur.text = fmt(d)
    }

    override fun onStart() {
        super.onStart()
        MediaHub.ensure(this)
        MediaHub.listeners.add(hubListener)
        bindNow()
        ui.post(ticker)
    }

    override fun onStop() {
        ui.removeCallbacks(ticker)
        MediaHub.listeners.remove(hubListener)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        showOverlay(false)
    }

    override fun onBackPressed() {
        if (overlay.visibility == View.VISIBLE) showOverlay(false)
    }
}
