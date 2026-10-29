package com.walkmanart

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        MediaHub.ensure(ctx)
        updateAll(ctx)
    }

    override fun onEnabled(ctx: Context) {
        MediaHub.ensure(ctx)
    }

    companion object {
        const val ACT_PLAY = "com.walkmanart.PLAY"
        const val ACT_NEXT = "com.walkmanart.NEXT"
        const val ACT_PREV = "com.walkmanart.PREV"

        private fun pi(ctx: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, code,
                Intent(ctx, WidgetControlReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, NowPlayingWidget::class.java))
                if (ids.isEmpty()) return
                val rv = RemoteViews(ctx.packageName, R.layout.widget_now_playing)
                val has = MediaHub.hasSession && MediaHub.title.isNotEmpty()
                rv.setTextViewText(R.id.w_title, if (has) MediaHub.title else "Nada sonando")
                rv.setTextViewText(R.id.w_artist, if (has) MediaHub.artist else "Abre Spotify o el reproductor")
                val art = MediaHub.art
                if (art != null) {
                    val s = Bitmap.createScaledBitmap(art, 160, 160, true)
                    rv.setImageViewBitmap(R.id.w_cover, s)
                } else {
                    rv.setImageViewResource(R.id.w_cover, android.R.drawable.ic_media_play)
                }
                rv.setImageViewResource(
                    R.id.w_play,
                    if (MediaHub.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
                rv.setOnClickPendingIntent(R.id.w_play, pi(ctx, ACT_PLAY, 1))
                rv.setOnClickPendingIntent(R.id.w_next, pi(ctx, ACT_NEXT, 2))
                rv.setOnClickPendingIntent(R.id.w_prev, pi(ctx, ACT_PREV, 3))
                mgr.updateAppWidget(ids, rv)
            } catch (_: Exception) {
            }
        }
    }
}

class WidgetControlReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        MediaHub.ensure(ctx)
        when (i.action) {
            NowPlayingWidget.ACT_PLAY -> MediaHub.playPause()
            NowPlayingWidget.ACT_NEXT -> MediaHub.next()
            NowPlayingWidget.ACT_PREV -> MediaHub.prev()
        }
    }
}
