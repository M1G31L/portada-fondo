package com.walkmanart

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.max

/**
 * Fondo animado: muestra la portada de la canción que suena.
 * Solo se dibuja al cambiar de portada (fundido breve) para gastar poca batería.
 */
class ArtWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ArtEngine()

    inner class ArtEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val component = ComponentName(this@ArtWallpaperService, MediaListenerService::class.java)

        private var current: Bitmap? = null
        private var previous: Bitmap? = null
        private var fade = 1f
        private var visible = false
        private var started = false
        private var width = 0
        private var height = 0
        private var noAccess = false

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val dim = Paint().apply { color = Color.argb(110, 0, 0, 0) }
        private val bg = Paint()

        private var msm: MediaSessionManager? = null
        private val callbacks = LinkedHashMap<MediaController, MediaController.Callback>()

        private val sessionsListener =
            MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list ?: emptyList()) }

        private val busListener: () -> Unit = { handler.post { refresh() } }

        private val frame = object : Runnable {
            override fun run() {
                fade += 0.06f
                if (fade >= 1f) {
                    fade = 1f
                    previous = null
                    draw()
                } else {
                    draw()
                    handler.postDelayed(this, 16)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                start()
                draw()
            } else {
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
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            handler.removeCallbacks(frame)
            stop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            stop()
            super.onDestroy()
        }

        // ---------- sesiones de medios ----------

        private fun start() {
            if (started) return
            started = true
            ArtBus.listeners.add(busListener)
            try {
                val m = getSystemService(MediaSessionManager::class.java)
                msm = m
                m.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
                noAccess = false
                attach(m.getActiveSessions(component))
            } catch (e: SecurityException) {
                // Falta el permiso de acceso a notificaciones.
                noAccess = true
                refresh()
            } catch (e: Exception) {
                refresh()
            }
        }

        private fun stop() {
            if (!started) return
            started = false
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
            val c = ctrls.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: ctrls.firstOrNull()
            var bmp: Bitmap? = c?.metadata?.let { extractArt(it) }
            if (bmp == null) bmp = ArtBus.notifArt
            setArt(bmp)
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
            previous = cur
            current = bmp
            fade = 0f
            handler.removeCallbacks(frame)
            if (visible) handler.post(frame)
        }

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
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
                previous?.let { drawCover(canvas, it, 255) }
                current?.let { drawCover(canvas, it, (255 * fade).toInt().coerceIn(0, 255)) }
                if (current != null) {
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
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
    }
}
