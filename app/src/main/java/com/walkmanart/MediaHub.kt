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
    private var controller: MediaController? = null

    val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun ensure(ctx: Context) {
        if (started) return
        val c = ctx.applicationContext
        appCtx = c
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
        val c = ctrls.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: ctrls.firstOrNull()
        controller = c
        hasSession = c != null
        playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
        val md = c?.metadata
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

    fun playPause() {
        val c = controller ?: return
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }
}
