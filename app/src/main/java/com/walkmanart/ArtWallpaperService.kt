package com.walkmanart

import android.animation.ArgbEvaluator
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.text.TextPaint
import android.text.TextUtils
import android.view.SurfaceHolder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val SHEEN_COLORS = intArrayOf(
    0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF
)
private val SHEEN_POS = floatArrayOf(0f, 0.08f, 0.16f, 0.5f, 0.58f, 0.66f)

/**
 * Fondo animado que muestra la portada de la canción que suena,
 * con varios estilos que se eligen desde la app.
 */
class ArtWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ArtEngine()

    inner class ArtEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val component = ComponentName(this@ArtWallpaperService, MediaListenerService::class.java)
        private val prefs = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        private val density = this@ArtWallpaperService.resources.displayMetrics.density
        private fun dp(v: Float) = v * density

        // estado
        private var current: Bitmap? = null
        private var previous: Bitmap? = null
        private var blurCur: Bitmap? = null
        private var blurPrev: Bitmap? = null
        private var palCur: IntArray = Pal.DEFAULT
        private var palPrev: IntArray = Pal.DEFAULT
        private var fade = 1f
        private var style = Styles.BLUR_CARD
        private var dimPct = 35
        private var titleText = ""
        private var artistText = ""

        private var visible = false
        private var started = false
        private var width = 0
        private var height = 0
        private var discCx = 0f
        private var discCy = 0f
        private var discR = 0f
        private val t0 = SystemClock.uptimeMillis()
        private var lastFrame = t0

        // pinceles
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val bg = Paint()
        private val dimPaint = Paint()
        private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
            textAlign = Paint.Align.CENTER
        }
        private val argb = ArgbEvaluator()

        // burbujas
        private val rnd = java.util.Random(42)
        private val bx = FloatArray(16) { rnd.nextFloat() }
        private val by = FloatArray(16) { rnd.nextFloat() }
        private val br = FloatArray(16) { 14f + rnd.nextFloat() * 56f }
        private val bs = FloatArray(16) { 0.012f + rnd.nextFloat() * 0.03f }
        private val ba = IntArray(16) { 40 + rnd.nextInt(70) }

        // sesiones de medios
        private var msm: MediaSessionManager? = null
        private val callbacks = LinkedHashMap<MediaController, MediaController.Callback>()
        private val sessionsListener =
            MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list ?: emptyList()) }
        private val busListener: () -> Unit = { handler.post { refresh() } }
        private val prefListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                handler.post {
                    loadPrefs()
                    schedule()
                }
            }

        private val tick = object : Runnable {
            override fun run() {
                if (!visible) return
                val now = SystemClock.uptimeMillis()
                val dt = (now - lastFrame) / 1000f
                lastFrame = now
                if (fade < 1f) {
                    fade += dt / 0.6f
                    if (fade >= 1f) {
                        fade = 1f
                        previous = null
                        blurPrev = null
                        palPrev = palCur
                    }
                }
                draw()
                val delay = when (style) {
                    Styles.COVER, Styles.LIGHT -> -1L
                    Styles.CLOCK, Styles.DAYTIME -> 1000L
                    else -> 33L
                }
                if (fade < 1f) handler.postDelayed(this, 33)
                else if (delay > 0) handler.postDelayed(this, delay)
            }
        }

        private fun schedule() {
            handler.removeCallbacks(tick)
            if (visible) handler.post(tick)
        }

        private fun loadPrefs() {
            style = prefs.getInt(Prefs.KEY_STYLE, Styles.COVER)
            dimPct = prefs.getInt(Prefs.KEY_DIM, 35)
            dimPaint.color = Color.argb((dimPct * 255 / 100).coerceIn(0, 255), 0, 0, 0)
        }

        // ---------- ciclo de vida ----------

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                start()
                lastFrame = SystemClock.uptimeMillis()
                schedule()
            } else {
                handler.removeCallbacks(tick)
                stop()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            bg.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#1b1b2f"), Color.parseColor("#0b0b12"),
                Shader.TileMode.CLAMP
            )
            discCx = w / 2f
            discCy = h * 0.36f
            discR = w * 0.40f
            sheenPaint.shader = SweepGradient(discCx, discCy, SHEEN_COLORS, SHEEN_POS)
            loadPrefs()
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(tick)
            stop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            stop()
            super.onDestroy()
        }

        // ---------- sesiones de medios ----------

        private fun start() {
            if (started) return
            started = true
            loadPrefs()
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            ArtBus.listeners.add(busListener)
            try {
                val m = getSystemService(MediaSessionManager::class.java)
                msm = m
                m.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
                attach(m.getActiveSessions(component))
            } catch (e: SecurityException) {
                refresh()
            } catch (e: Exception) {
                refresh()
            }
        }

        private fun stop() {
            if (!started) return
            started = false
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            ArtBus.listeners.remove(busListener)
            try {
                msm?.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (_: Exception) {
            }
            clearCallbacks()
        }

        private fun clearCallbacks() {
            for ((c, cb) in callbacks) {
                try {
                    c.unregisterCallback(cb)
                } catch (_: Exception) {
                }
            }
            callbacks.clear()
        }

        private fun attach(list: List<MediaController>) {
            clearCallbacks()
            for (c in list) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        refresh()
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        refresh()
                    }
                }
                try {
                    c.registerCallback(cb, handler)
                    callbacks[c] = cb
                } catch (_: Exception) {
                }
            }
            refresh()
        }

        private fun refresh() {
            val ctrls = callbacks.keys
            fun hasTitle(x: MediaController) = !(x.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrEmpty())
            val c = ctrls.firstOrNull { hasTitle(it) && it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: ctrls.firstOrNull { hasTitle(it) }
                ?: ctrls.firstOrNull()
            val md = c?.metadata
            if (md != null) {
                titleText = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                artistText = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            }
            var bmp: Bitmap? = md?.let { extractArt(it) }
            if (bmp == null) bmp = ArtBus.notifArt
            setArt(bmp)
            if (visible) schedule()
        }

        private fun extractArt(m: MediaMetadata): Bitmap? {
            m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
            m.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
            m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }
            val keys = listOf(
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
            )
            for (k in keys) {
                val u = m.getString(k) ?: continue
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    contentResolver.openInputStream(Uri.parse(u))?.use { s ->
                        BitmapFactory.decodeStream(s, null, opts)?.let { return it }
                    }
                } catch (_: Exception) {
                }
            }
            return null
        }

        private fun makeBlur(b: Bitmap): Bitmap? = try {
            Bitmap.createScaledBitmap(b, 24, 24, true)
        } catch (e: Exception) {
            null
        }

        private fun setArt(bmp: Bitmap?) {
            if (bmp == null) return
            val cur = current
            if (cur === bmp) return
            if (cur != null && cur.width == bmp.width && cur.height == bmp.height) {
                try {
                    if (cur.sameAs(bmp)) return
                } catch (_: Exception) {
                }
            }
            palPrev = pal()
            previous = cur
            blurPrev = blurCur
            current = bmp
            blurCur = makeBlur(bmp)
            palCur = Pal.from(bmp)
            fade = 0f
        }

        private fun pal(): IntArray =
            if (fade >= 1f) palCur else IntArray(3) { argb.evaluate(fade, palPrev[it], palCur[it]) as Int }

        // ---------- dibujo ----------

        private fun draw() {
            if (!visible || width == 0 || height == 0) return
            val holder = surfaceHolder
            val canvas = try {
                holder.lockCanvas()
            } catch (e: Exception) {
                null
            } ?: return
            try {
                val t = (SystemClock.uptimeMillis() - t0) / 1000f
                render(canvas, t)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun layer(prev: Bitmap?, cur: Bitmap?, block: (Bitmap, Int) -> Unit) {
            prev?.let { block(it, 255) }
            cur?.let { block(it, (255 * fade).toInt().coerceIn(0, 255)) }
        }

        private fun render(c: Canvas, t: Float) {
            val w = width.toFloat()
            val h = height.toFloat()
            c.drawRect(0f, 0f, w, h, bg)
            when (style) {
                Styles.COVER -> {
                    layer(previous, current) { b, a -> drawCover(c, b, a) }
                    c.drawRect(0f, 0f, w, h, dimPaint)
                }
                Styles.BLUR_CARD -> {
                    layer(blurPrev, blurCur) { b, a -> drawCover(c, b, a) }
                    c.drawRect(0f, 0f, w, h, dimPaint)
                    drawBlurCard(c, t)
                }
                Styles.AURORA -> {
                    drawAurora(c, t)
                    c.drawRect(0f, 0f, w, h, dimPaint)
                }
                Styles.VINYL -> {
                    layer(blurPrev, blurCur) { b, a -> drawCover(c, b, a) }
                    c.drawRect(0f, 0f, w, h, dimPaint)
                    drawVinyl(c, t)
                }
                Styles.BOKEH -> {
                    layer(blurPrev, blurCur) { b, a -> drawCover(c, b, a) }
                    c.drawRect(0f, 0f, w, h, dimPaint)
                    drawBokeh(c, t)
                }
                Styles.CLOCK -> {
                    layer(blurPrev, blurCur) { b, a -> drawCover(c, b, a) }
                    fillPaint.color = Color.argb(120, 0, 0, 0)
                    c.drawRect(0f, 0f, w, h, fillPaint)
                    c.drawRect(0f, 0f, w, h, dimPaint)
                    drawClock(c)
                }
                Styles.DAYTIME -> {
                    drawDaytime(c)
                    c.drawRect(0f, 0f, w, h, dimPaint)
                    drawSmallCard(c, false)
                }
                Styles.LIGHT -> {
                    layer(blurPrev, blurCur) { b, a -> drawCover(c, b, a) }
                    fillPaint.color = Color.argb(215, 250, 250, 252)
                    c.drawRect(0f, 0f, w, h, fillPaint)
                    drawSmallCard(c, true)
                }
            }
        }

        private fun drawCover(c: Canvas, b: Bitmap, alpha: Int) {
            val s = max(width.toFloat() / b.width, height.toFloat() / b.height)
            val dx = (width - b.width * s) / 2f
            val dy = (height - b.height * s) / 2f
            val m = Matrix()
            m.setScale(s, s)
            m.postTranslate(dx, dy)
            paint.alpha = alpha
            c.drawBitmap(b, m, paint)
        }

        private fun drawRoundedCover(c: Canvas, b: Bitmap, alpha: Int, r: RectF, radius: Float) {
            val shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val m = Matrix()
            val s = max(r.width() / b.width, r.height() / b.height)
            m.setScale(s, s)
            m.postTranslate(r.left + (r.width() - b.width * s) / 2f, r.top + (r.height() - b.height * s) / 2f)
            shader.setLocalMatrix(m)
            cardPaint.shader = shader
            cardPaint.alpha = alpha
            c.drawRoundRect(r, radius, radius, cardPaint)
            cardPaint.shader = null
        }

        private fun drawCircleCover(c: Canvas, b: Bitmap, alpha: Int, cx: Float, cy: Float, rad: Float) {
            val shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val m = Matrix()
            val s = max(2f * rad / b.width, 2f * rad / b.height)
            m.setScale(s, s)
            m.postTranslate(cx - b.width * s / 2f, cy - b.height * s / 2f)
            shader.setLocalMatrix(m)
            cardPaint.shader = shader
            cardPaint.alpha = alpha
            c.drawCircle(cx, cy, rad, cardPaint)
            cardPaint.shader = null
        }

        private fun drawTexts(c: Canvas, yTop: Float, dark: Boolean = false) {
            if (titleText.isEmpty()) return
            titlePaint.color = if (dark) Color.parseColor("#1A1A26") else Color.WHITE
            artistPaint.color = if (dark) Color.argb(190, 26, 26, 38) else Color.argb(190, 255, 255, 255)
            val maxW = width * 0.84f
            titlePaint.textSize = dp(22f)
            artistPaint.textSize = dp(15f)
            val tt = TextUtils.ellipsize(titleText, titlePaint, maxW, TextUtils.TruncateAt.END).toString()
            c.drawText(tt, width / 2f, yTop, titlePaint)
            if (artistText.isNotEmpty()) {
                val at = TextUtils.ellipsize(artistText, artistPaint, maxW, TextUtils.TruncateAt.END).toString()
                c.drawText(at, width / 2f, yTop + dp(26f), artistPaint)
            }
        }

        private fun drawBlurCard(c: Canvas, t: Float) {
            val size = width * 0.74f
            val left = (width - size) / 2f
            val top = height * 0.16f + (sin(t * 1.2f) * dp(5f))
            val r = RectF(left, top, left + size, top + size)
            val radius = dp(26f)
            if (current != null) {
                shadowPaint.color = Color.BLACK
                shadowPaint.setShadowLayer(dp(26f), 0f, dp(12f), Color.argb(150, 0, 0, 0))
                c.drawRoundRect(r, radius, radius, shadowPaint)
            } else {
                fillPaint.color = Color.argb(60, 255, 255, 255)
                c.drawRoundRect(r, radius, radius, fillPaint)
            }
            layer(previous, current) { b, a -> drawRoundedCover(c, b, a, r, radius) }
            drawTexts(c, r.bottom + dp(46f))
        }

        private fun drawAurora(c: Canvas, t: Float) {
            val w = width.toFloat()
            val h = height.toFloat()
            val p = pal()
            for (i in 0..2) {
                val ph = i * 2.1f
                val cx = w * (0.5f + 0.38f * sin(t * (0.25f + 0.07f * i) + ph))
                val cy = h * (0.5f + 0.34f * cos(t * (0.21f + 0.05f * i) + ph * 1.3f))
                val rad = max(w, h) * (0.55f + 0.1f * sin(t * 0.4f + ph))
                val col = p[i]
                blobPaint.shader = RadialGradient(
                    cx, cy, rad,
                    Color.argb(200, Color.red(col), Color.green(col), Color.blue(col)),
                    Color.argb(0, Color.red(col), Color.green(col), Color.blue(col)),
                    Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w, h, blobPaint)
            }
            blobPaint.shader = null
        }

        private fun drawVinyl(c: Canvas, t: Float) {
            val p = pal()
            // ondas
            for (k in 0..2) {
                val ph = (t * 0.35f + k / 3f) % 1f
                val rad = discR * (1.0f + ph * 0.55f)
                val col = p[0]
                ringPaint.strokeWidth = dp(2f)
                ringPaint.color = Color.argb(
                    (90 * (1f - ph)).toInt().coerceIn(0, 255),
                    Color.red(col), Color.green(col), Color.blue(col)
                )
                c.drawCircle(discCx, discCy, rad, ringPaint)
            }
            // disco
            c.save()
            c.rotate(t * 45f, discCx, discCy)
            fillPaint.color = Color.parseColor("#101012")
            fillPaint.setShadowLayer(dp(22f), 0f, dp(10f), Color.argb(160, 0, 0, 0))
            c.drawCircle(discCx, discCy, discR, fillPaint)
            fillPaint.clearShadowLayer()
            ringPaint.strokeWidth = dp(1f)
            ringPaint.color = Color.argb(40, 255, 255, 255)
            for (i in 1..6) {
                c.drawCircle(discCx, discCy, discR * (0.5f + 0.08f * i), ringPaint)
            }
            val labelR = discR * 0.42f
            if (current == null && previous == null) {
                fillPaint.color = p[0]
                c.drawCircle(discCx, discCy, labelR, fillPaint)
            } else {
                layer(previous, current) { b, a -> drawCircleCover(c, b, a, discCx, discCy, labelR) }
            }
            fillPaint.color = Color.parseColor("#0b0b12")
            c.drawCircle(discCx, discCy, discR * 0.035f, fillPaint)
            c.restore()
            // brillo fijo
            c.drawCircle(discCx, discCy, discR, sheenPaint)
            drawTexts(c, discCy + discR * 1.55f)
        }

        private val clockPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
        }

        private fun drawClock(c: Canvas) {
            val cal = java.util.Calendar.getInstance()
            val hh = String.format("%02d", cal.get(java.util.Calendar.HOUR_OF_DAY))
            val mm = String.format("%02d", cal.get(java.util.Calendar.MINUTE))
            clockPaint.textSize = width * 0.40f
            val x = width / 2f
            val y = height * 0.34f
            c.drawText(hh, x, y, clockPaint)
            c.drawText(mm, x, y + clockPaint.textSize * 0.95f, clockPaint)
            artistPaint.textSize = dp(15f)
            titlePaint.textSize = dp(20f)
            drawTexts(c, height * 0.80f)
        }

        private val dayHours = floatArrayOf(0f, 6f, 12f, 18f, 22f, 24f)
        private val dayTop = intArrayOf(
            0xFF0B1026.toInt(), 0xFFFF8A65.toInt(), 0xFF4FC3F7.toInt(),
            0xFFFF7043.toInt(), 0xFF0B1026.toInt(), 0xFF0B1026.toInt()
        )
        private val dayBot = intArrayOf(
            0xFF1B2A57.toInt(), 0xFF5C6BC0.toInt(), 0xFF1976D2.toInt(),
            0xFF6A1B9A.toInt(), 0xFF1B2A57.toInt(), 0xFF1B2A57.toInt()
        )

        private fun drawDaytime(c: Canvas) {
            val cal = java.util.Calendar.getInstance()
            val hr = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60f
            var i = 0
            while (i < dayHours.size - 2 && hr >= dayHours[i + 1]) i++
            val f = ((hr - dayHours[i]) / (dayHours[i + 1] - dayHours[i])).coerceIn(0f, 1f)
            val top = argb.evaluate(f, dayTop[i], dayTop[i + 1]) as Int
            val bot = argb.evaluate(f, dayBot[i], dayBot[i + 1]) as Int
            fillPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bot, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
            fillPaint.shader = null
            // resplandor con el color de la portada
            val p = pal()
            val col = p[0]
            blobPaint.shader = RadialGradient(
                width * 0.5f, height * 0.9f, max(width, height) * 0.8f,
                Color.argb(110, Color.red(col), Color.green(col), Color.blue(col)),
                Color.argb(0, Color.red(col), Color.green(col), Color.blue(col)),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blobPaint)
            blobPaint.shader = null
        }

        private fun drawSmallCard(c: Canvas, dark: Boolean) {
            val size = width * 0.62f
            val left = (width - size) / 2f
            val top = height * 0.20f
            val r = RectF(left, top, left + size, top + size)
            val radius = dp(24f)
            if (current != null) {
                shadowPaint.color = Color.BLACK
                shadowPaint.setShadowLayer(dp(22f), 0f, dp(10f), Color.argb(if (dark) 70 else 140, 0, 0, 0))
                c.drawRoundRect(r, radius, radius, shadowPaint)
            } else {
                fillPaint.color = if (dark) Color.argb(40, 0, 0, 0) else Color.argb(60, 255, 255, 255)
                c.drawRoundRect(r, radius, radius, fillPaint)
            }
            layer(previous, current) { b, a -> drawRoundedCover(c, b, a, r, radius) }
            drawTexts(c, r.bottom + dp(46f), dark)
        }

        private fun drawBokeh(c: Canvas, t: Float) {
            val p = pal()
            val w = width.toFloat()
            val h = height.toFloat()
            for (i in bx.indices) {
                val frac = (by[i] + t * bs[i]) % 1f
                val py = h * (1.1f - frac * 1.2f)
                val px = w * bx[i] + dp(14f) * sin(t * 0.5f + i)
                val rad = dp(br[i])
                val col = p[i % 3]
                fillPaint.color = Color.argb(ba[i], Color.red(col), Color.green(col), Color.blue(col))
                c.drawCircle(px, py, rad, fillPaint)
                ringPaint.strokeWidth = dp(1.5f)
                ringPaint.color = Color.argb(
                    (ba[i] + 40).coerceAtMost(255), Color.red(col), Color.green(col), Color.blue(col)
                )
                c.drawCircle(px, py, rad, ringPaint)
            }
        }
    }
}
