package com.walkmanart

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        col.addView(TextView(this).apply {
            text = "Portada Fondo\nPon la portada de la canción que suena como fondo de pantalla."
            textSize = 16f
        })

        col.addView(Button(this).apply {
            text = "1) Dar acceso a notificaciones"
            setOnClickListener {
                tryStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })

        col.addView(Button(this).apply {
            text = "2) Elegir este fondo animado"
            setOnClickListener {
                val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, ArtWallpaperService::class.java)
                )
                tryStart(i)
            }
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun tryStart(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }
}
