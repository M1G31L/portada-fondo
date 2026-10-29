package com.walkmanart

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * Estado compartido de "lo que suena ahora". Lo usan la pantalla de inicio
 * y el widget. Requiere el acceso a notificaciones.
 */
object MediaHub {
    private val handler = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var started = false
    private val callbacks = LinkedHashMap<MediaController, MediaController.Callback>()

    @Volatile var title = ""
    @Volatile var artist = ""
    @Volatile var art: Bitmap? = null
    @Volatile var playing = false
    @Volatile var hasSession = false
    @Volatile var duration = 0L
    private var basePos = 0L
    private var baseTime = 0L
    private var speed = 1f

    fun positionNow(): Long {
        if (!hasSession) return 0L
        val p = if (playing) basePos + ((android.os.SystemClock.elapsedRealtime() - baseTime) * speed).toLong() else basePos
        return if (duration > 0) p.coerceIn(0L, duration) else p.coerceAtLeast(0L)
    }
    private var controller: MediaController? = null

    val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun ensure(ctx: Context) {
        val c = ctx.applicationContext
        appCtx = c
        if (started) return
        try {
            val comp = ComponentName(c, MediaListenerService::class.java)
            val m = c.getSystemService(MediaSessionManager::class.java)
            m.addOnActiveSessionsChangedListener({ list -> attach(list ?: emptyList()) }, comp, handler)
            attach(m.getActiveSessions(comp))
            started = true
        } catch (_: Exception) {
            // sin acceso a notificaciones todavía
        }
    }

    private fun attach(list: List<MediaController>) {
        for ((c, cb) in callbacks) {
            try { c.unregisterCallback(cb) } catch (_: Exception) {}
        }
        callbacks.clear()
        for (c in list) {
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) { refresh() }
                override fun onPlaybackStateChanged(state: PlaybackState?) { refresh() }
            }
            try {
                c.registerCallback(cb, handler)
                callbacks[c] = cb
            } catch (_: Exception) {}
        }
        refresh()
    }

    private fun refresh() {
        val ctrls = callbacks.keys
        fun hasTitle(x: MediaController) = !(x.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrEmpty())
        val c = ctrls.firstOrNull { hasTitle(it) && it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: ctrls.firstOrNull { hasTitle(it) }
            ?: ctrls.firstOrNull()
        controller = c
        hasSession = c != null
        playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
        c?.playbackState?.let {
            basePos = it.position
            baseTime = it.lastPositionUpdateTime
            speed = it.playbackSpeed
        }
        val md = c?.metadata
        duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (md != null) {
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            art = extractArt(md) ?: ArtBus.notifArt
        }
        for (l in listeners) l()
        appCtx?.let { NowPlayingWidget.updateAll(it) }
    }

    private fun extractArt(m: MediaMetadata): Bitmap? {
        m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        m.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }
        val ctx = appCtx ?: return null
        for (k in listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
        )) {
            val u = m.getString(k) ?: continue
            try {
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                ctx.contentResolver.openInputStream(Uri.parse(u))?.use { s ->
                    BitmapFactory.decodeStream(s, null, o)?.let { return it }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /** Envía una tecla multimedia al reproductor activo (funciona con cualquier app). */
    private fun key(code: Int) {
        val ctx = appCtx ?: return
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
        } catch (_: Exception) {
        }
    }

    fun playPause() { key(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
    fun next() { key(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) }
    fun prev() { key(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
}
