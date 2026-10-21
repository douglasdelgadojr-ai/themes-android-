package com.douglas.temas

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation.*
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import android.widget.LinearLayout.LayoutParams as LP

/** Tema 2 (estilo Android Auto): mapa/nav a la izquierda, música a la derecha, barra inferior con dock y reloj. */
class DashboardTheme : LauncherTheme {
    override val name = "Dashboard"

    private fun iconButton(ctx: Context, icon: TransportIcon, bg: Int, onClick: () -> Unit) =
        FrameLayout(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
            addView(icon, FrameLayout.LayoutParams(-1, -1))
            setOnClickListener { onClick() }
        }

    override fun build(ctx: Context, host: ThemeHost): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0B0D10.toInt())
            setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(10), ctx.dp(6))
        }
        val top = LinearLayout(ctx)
        root.addView(top, LP(-1, 0, 1f))

        // ---- Navegación (izquierda) ----
        val nav = FrameLayout(ctx).apply {
            background = grad(ctx, TOP_BOTTOM, 0xFF24323C, 0xFF141B21, 28)
            setOnClickListener { host.launch(NAV_PKG) }
        }
        nav.addView(label(ctx, "Ir con Waze", 15f).apply {
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(16), ctx.dp(10))
            background = grad(ctx, LEFT_RIGHT, 0xFF0B0D10, 0xFF0B0D10, 22)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(ctx.dp(16), ctx.dp(16), 0, 0) })
        nav.addView(iconButton(ctx, TransportIcon(ctx, TransportIcon.Kind.NAV, Color.WHITE), 0xFF2F7BFF.toInt()) { host.launch(NAV_PKG) },
            FrameLayout.LayoutParams(ctx.dp(84), ctx.dp(84), Gravity.CENTER))
        nav.addView(label(ctx, "Toca para abrir Waze", 14f).apply { setTextColor(0xFFB0BEC5.toInt()) },
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = ctx.dp(14) })
        top.addView(nav, LP(0, -1, 1.15f).apply { setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(6), ctx.dp(4)) })

        // ---- Música (derecha) ----
        val music = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(18), ctx.dp(18), ctx.dp(18), ctx.dp(14))
            background = grad(ctx, TOP_BOTTOM, 0xFF2D3340, 0xFF171A21, 28)
        }
        val info = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val art = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF3A4150.toInt())
        }
        info.addView(art, LP(ctx.dp(96), ctx.dp(96)).apply { rightMargin = ctx.dp(14) })
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val title = label(ctx, "—", 22f, true).apply { maxLines = 2 }
        val artist = label(ctx, "", 16f).apply { setTextColor(0xFFB0BEC5.toInt()); maxLines = 1 }
        texts.addView(title); texts.addView(artist)
        info.addView(texts, LP(0, -2, 1f))
        music.addView(info, LP(-1, 0, 1f))

        val ctl = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        fun btnLP(size: Int) = LP(ctx.dp(size), ctx.dp(size)).apply { setMargins(ctx.dp(8), 0, ctx.dp(8), 0) }
        ctl.addView(iconButton(ctx, TransportIcon(ctx, TransportIcon.Kind.PREV, Color.WHITE), 0x22FFFFFF) { host.media(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, btnLP(64))
        val playIcon = TransportIcon(ctx, TransportIcon.Kind.PLAY, Color.BLACK)
        ctl.addView(iconButton(ctx, playIcon, Color.WHITE) { host.media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, btnLP(76))
        ctl.addView(iconButton(ctx, TransportIcon(ctx, TransportIcon.Kind.NEXT, Color.WHITE), 0x22FFFFFF) { host.media(KeyEvent.KEYCODE_MEDIA_NEXT) }, btnLP(64))
        music.addView(ctl, LP(-1, -2))
        top.addView(music, LP(0, -1, 1f).apply { setMargins(ctx.dp(6), ctx.dp(4), ctx.dp(4), ctx.dp(4)) })

        // Actualiza la música cada segundo mientras la pantalla exista
        val tick = object : Runnable {
            override fun run() {
                if (!root.isAttachedToWindow) return
                val np = host.nowPlaying()
                if (np == null) {
                    title.text = "Dar acceso a la música"; artist.text = "Toca aquí y activa «Temas»"
                    music.setOnClickListener { host.requestMediaAccess() }
                } else {
                    title.text = np.title.ifEmpty { "Sin reproducción" }; artist.text = np.artist
                    playIcon.set(if (np.playing) TransportIcon.Kind.PAUSE else TransportIcon.Kind.PLAY)
                    music.setOnClickListener(null); music.isClickable = false
                    if (np.art != null) art.setImageBitmap(np.art) else art.setImageDrawable(null)
                }
                root.postDelayed(this, 1000)
            }
        }
        root.post(tick)

        // ---- Barra inferior ----
        val bar = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(label(ctx, "▦", 30f).apply {
            gravity = Gravity.CENTER
            setOnClickListener { host.openDrawer() }
            setOnLongClickListener { host.nextTheme(); true }   // mantener = cambiar de tema
        }, LP(ctx.dp(70), -1))
        val dock = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        for (i in 0 until 5) {
            val pkg = host.dockPackage(i)
            val slot = FrameLayout(ctx).apply {
                val icon: View = try {
                    ImageView(ctx).apply { setImageDrawable(ctx.packageManager.getApplicationIcon(pkg!!)) }
                } catch (e: Exception) { label(ctx, "+", 30f).apply { gravity = Gravity.CENTER } }
                addView(icon, FrameLayout.LayoutParams(ctx.dp(50), ctx.dp(50), Gravity.CENTER))
                setOnClickListener { if (pkg != null) host.launch(pkg) else host.pickDockSlot(i) }
                setOnLongClickListener { host.pickDockSlot(i); true }
            }
            dock.addView(slot, LP(0, -1, 1f))
        }
        bar.addView(dock, LP(0, -1, 1f))
        bar.addView(TextClock(ctx).apply {
            format12Hour = "HH:mm"; format24Hour = "HH:mm"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, LP(ctx.dp(90), -1))
        root.addView(bar, LP(-1, ctx.dp(66)))
        return root
    }
}
