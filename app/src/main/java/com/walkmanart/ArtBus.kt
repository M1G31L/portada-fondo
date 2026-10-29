package com.walkmanart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.util.concurrent.CopyOnWriteArrayList

/** Puente simple entre el servicio de notificaciones y el fondo animado. */
object ArtBus {
    @Volatile
    var notifArt: Bitmap? = null

    val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun changed() {
        for (l in listeners) l()
    }
}

fun drawableToBitmap(d: Drawable): Bitmap {
    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 512
    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 512
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    d.setBounds(0, 0, w, h)
    d.draw(c)
    return bmp
}

@Suppress("unused")
fun Context.dummy() = Unit
