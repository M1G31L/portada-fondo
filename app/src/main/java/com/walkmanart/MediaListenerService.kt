package com.walkmanart

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Su principal función es existir y tener permiso de "acceso a notificaciones",
 * lo que autoriza a leer las sesiones de medios activas. Además, como respaldo,
 * toma la portada de la notificación del reproductor.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    @Suppress("DEPRECATION")
    private fun handle(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val isMedia = n.category == Notification.CATEGORY_TRANSPORT ||
            extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        if (!isMedia) return

        var bmp: Bitmap? = extras.getParcelable(Notification.EXTRA_PICTURE)
        if (bmp == null) {
            val icon = n.getLargeIcon()
            bmp = icon?.loadDrawable(this)?.let { drawableToBitmap(it) }
        }
        if (bmp != null) {
            ArtBus.notifArt = bmp
            ArtBus.changed()
        }
    }
}
