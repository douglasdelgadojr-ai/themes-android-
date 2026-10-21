package com.douglas.temas

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation.*
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import android.widget.LinearLayout.LayoutParams as LP

/** Tema 3: fondo tipo aurora + columna de tarjetas de vidrio a la izquierda + barra inferior. */
class PanelTheme : LauncherTheme {
    override val name = "Panel"

    override fun build(ctx: Context, host: ThemeHost): View {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        // Fondo "aurora" dibujado (sin imagen): azul noche + resplandor verde/turquesa
        root.background = LayerDrawable(arrayOf(
            GradientDrawable(TOP_BOTTOM, intArrayOf(0xFF040A1C.toInt(), 0xFF0B3C52.toInt())),
            GradientDrawable(TL_BR, intArrayOf(0x0000E5A0, 0x6600E5A0, 0x00000000)),
            GradientDrawable(BL_TR, intArrayOf(0x00000000, 0x55A06BFF, 0x00000000))
        ))

        val body = FrameLayout(ctx)
        root.addView(body, LP(-1, 0, 1f))

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        body.addView(col, FrameLayout.LayoutParams(ctx.dp(300), -1, Gravity.START).apply { setMargins(ctx.dp(10), ctx.dp(6), 0, ctx.dp(6)) })

        fun glass(title: String, glyph: String, line1: String, line2: String, onClick: () -> Unit): TextView {
            val tvLine = label(ctx, line1, 15f, true).apply { maxLines = 1 }
            val tvSub = label(ctx, line2, 12f).apply { setTextColor(0xFFB7D3F2.toInt()); maxLines = 1 }
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
                background = GradientDrawable(TOP_BOTTOM, intArrayOf(0xDD2A66B0.toInt(), 0xDD17427F.toInt())).apply {
                    cornerRadius = ctx.dp(8).toFloat(); setStroke(ctx.dp(1), 0x55FFFFFF)
                }
                setOnClickListener { onClick() }
                addView(label(ctx, title, 11f).apply { setTextColor(0xFFD5E6FA.toInt()) })
                val r = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
                r.addView(label(ctx, glyph, 26f), LP(ctx.dp(44), -2))
                val t = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                t.addView(tvLine); t.addView(tvSub)
                r.addView(t, LP(0, -2, 1f))
                addView(r, LP(-1, 0, 1f))
            }
            col.addView(card, LP(-1, 0, 1f).apply { setMargins(0, ctx.dp(3), 0, ctx.dp(3)) })
            return tvLine.also { tvLine.tag = tvSub }
        }

        val music = glass("Música", "♫", "Reproductor", "Toca: play / pausa") { host.media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        glass("Navegación", "➤", "Waze", "Toca para abrir") { host.launch(NAV_PKG) }
        glass("Bluetooth", "BT", "Ajustes Bluetooth", "Emparejar / conectar") { host.openSettings("android.settings.BLUETOOTH_SETTINGS") }
        glass("Aplicaciones", "▦", "Todas las apps", "Mantén ▦ abajo: cambiar tema") { host.openDrawer() }

        // Reloj grande a la derecha, sobre el fondo
        val clock = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        fun tc(f: String, sp: Float) = TextClock(ctx).apply { format12Hour = f; format24Hour = f; textSize = sp; setTextColor(Color.WHITE); gravity = Gravity.END }
        clock.addView(tc("HH:mm", 54f)); clock.addView(tc("EEEE d MMMM", 18f))
        body.addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, ctx.dp(28), ctx.dp(18)) })

        // Música real (si hay permiso de notificaciones)
        val tick = object : Runnable {
            override fun run() {
                if (!root.isAttachedToWindow) return
                val np = host.nowPlaying()
                val sub = music.tag as TextView
                if (np == null) { music.text = "Dar acceso a la música"; sub.text = "Toca y activa «Temas»" }
                else if (np.title.isNotEmpty()) { music.text = np.title; sub.text = np.artist.ifEmpty { "Toca: play / pausa" } }
                root.postDelayed(this, 1000)
            }
        }
        root.post(tick)

        root.addView(dockRow(ctx, host), LP(-1, ctx.dp(66)))
        return root
    }
}
