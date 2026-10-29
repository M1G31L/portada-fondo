package com.walkmanart

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Puente simple entre el servicio de notificaciones y el fondo animado. */
object ArtBus {
    @Volatile
    var notifArt: Bitmap? = null

    val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun changed() {
        for (l in listeners) l()
    }
}

/** Estilos de fondo disponibles. */
object Styles {
    const val COVER = 0
    const val BLUR_CARD = 1
    const val AURORA = 2
    const val VINYL = 3
    const val BOKEH = 4
    const val CLOCK = 5
    const val DAYTIME = 6
    const val LIGHT = 7
}

class StyleInfo(val id: Int, val name: String, val desc: String)

val ALL_STYLES = listOf(
    StyleInfo(Styles.COVER, "Portada completa", "La portada llena toda la pantalla con un fundido suave."),
    StyleInfo(Styles.BLUR_CARD, "Tarjeta flotante", "Portada en una tarjeta sobre fondo desenfocado, con título y artista."),
    StyleInfo(Styles.AURORA, "Aurora", "Luces de colores en movimiento, tomadas de la portada."),
    StyleInfo(Styles.VINYL, "Vinilo", "Un disco que gira con la portada al centro y ondas alrededor."),
    StyleInfo(Styles.BOKEH, "Bokeh", "Burbujas de luz que suben despacio sobre la portada desenfocada."),
    StyleInfo(Styles.CLOCK, "Reloj grande", "Hora enorme y minimalista sobre la portada desenfocada."),
    StyleInfo(Styles.DAYTIME, "Hora del día", "El degradado cambia con la hora: amanecer, día, tarde y noche."),
    StyleInfo(Styles.LIGHT, "Modo claro", "Fondo luminoso y suave con la portada en una tarjeta.")
)

object Prefs {
    const val FILE = "portada_prefs"
    const val KEY_STYLE = "style"
    const val KEY_DIM = "dim"

    fun style(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_STYLE, Styles.BLUR_CARD)

    fun dim(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_DIM, 35)
}

/** Colores vivos sacados de la portada. */
object Pal {
    val DEFAULT = intArrayOf(
        Color.parseColor("#6C5CE7"),
        Color.parseColor("#00B8D4"),
        Color.parseColor("#FF6B9D")
    )

    private val argb = ArgbEvaluator()

    fun lerp(a: IntArray, b: IntArray, f: Float): IntArray =
        IntArray(3) { argb.evaluate(f, a[it], b[it]) as Int }

    private fun hueDist(a: Float, b: Float): Float {
        val d = abs(a - b)
        return min(d, 360f - d)
    }

    private fun vivid(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = max(hsv[1], 0.45f)
        hsv[2] = max(hsv[2], 0.65f)
        return Color.HSVToColor(hsv)
    }

    fun from(b: Bitmap?): IntArray {
        if (b == null) return DEFAULT
        return try {
            val s = Bitmap.createScaledBitmap(b, 8, 8, true)
            val px = IntArray(64)
            s.getPixels(px, 0, 8, 0, 0, 8, 8)
            val hsv = FloatArray(3)
            val scored = px.map { col ->
                Color.colorToHSV(col, hsv)
                Triple(col, hsv[0], hsv[1] * hsv[2])
            }.sortedByDescending { it.third }
            val h1 = scored[0].second
            val second = scored.firstOrNull { hueDist(it.second, h1) > 40f } ?: scored[1]
            val third = scored.firstOrNull {
                hueDist(it.second, h1) > 40f && hueDist(it.second, second.second) > 40f
            } ?: scored[2]
            intArrayOf(vivid(scored[0].first), vivid(second.first), vivid(third.first))
        } catch (e: Exception) {
            DEFAULT
        }
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
