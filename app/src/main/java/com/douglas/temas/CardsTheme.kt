package com.douglas.temas

import android.content.Context
import android.graphics.drawable.GradientDrawable.Orientation.*
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import android.widget.LinearLayout.LayoutParams as LP

/** Tema 1: tarjetas verticales (reloj, musica, navegacion, apps) + dock. */
class CardsTheme : LauncherTheme {
    override val name = "Tarjetas"

    override fun build(ctx: Context, host: ThemeHost): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
            background = grad(ctx, TL_BR, 0xFF0B1030, 0xFF2A1458)
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(row, LP(-1, 0, 1f))

        fun card(a: Long, b: Long, onClick: (() -> Unit)? = null) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ctx.dp(10), ctx.dp(10), ctx.dp(10), ctx.dp(10))
            background = grad(ctx, TOP_BOTTOM, a, b, 22)
            if (onClick != null) setOnClickListener { onClick() }
            row.addView(this, LP(0, -1, 1f).apply { setMargins(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6)) })
        }

        // Reloj
        card(0xFF5B5BD6, 0xFF3A3A9E).apply {
            fun clock(fmt: String, sp: Float) = TextClock(ctx).apply {
                format12Hour = fmt; format24Hour = fmt; textSize = sp; setTextColor(-1)
            }
            addView(clock("HH:mm", 44f)); addView(clock("EEEE", 18f)); addView(clock("yyyy-MM-dd", 16f))
        }

        // Musica
        card(0xFFD63ED6, 0xFF7A1FA0).apply {
            addView(label(ctx, "Música", 16f))
            addView(label(ctx, "♫", 64f), LP(-2, -2).apply { setMargins(0, ctx.dp(6), 0, ctx.dp(6)) })
            val ctl = LinearLayout(ctx)
            fun btn(t: String, key: Int) = label(ctx, t, 30f).apply {
                setPadding(ctx.dp(14), ctx.dp(6), ctx.dp(14), ctx.dp(6))
                setOnClickListener { host.media(key) }
                ctl.addView(this)
            }
            btn("⏮", KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            btn("⏯", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            btn("⏭", KeyEvent.KEYCODE_MEDIA_NEXT)
            addView(ctl)
        }

        // Navegacion
        card(0xFF1FA2F0, 0xFF0B4FB5) { host.launch(NAV_PKG) }.apply {
            addView(label(ctx, "Navegación", 16f))
            addView(label(ctx, "➤", 72f))
        }

        // Apps
        card(0xFF6A7BE0, 0xFF3A44A8) { host.openDrawer() }.apply {
            addView(label(ctx, "Apps", 16f))
            addView(label(ctx, "▦", 72f))
            addView(label(ctx, "Tema: $name  (mantén presionado)", 11f))
            setOnLongClickListener { host.nextTheme(); true }
        }

        // Dock (5 accesos; tap = abrir, mantener = asignar app)
        val dock = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            background = grad(ctx, LEFT_RIGHT, 0x33FFFFFF, 0x22FFFFFF, 20)
        }
        for (i in 0 until 5) {
            val pkg = host.dockPackage(i)
            val slot = FrameLayout(ctx).apply {
                val icon: View = try {
                    ImageView(ctx).apply { setImageDrawable(ctx.packageManager.getApplicationIcon(pkg!!)) }
                } catch (e: Exception) {
                    label(ctx, "+", 30f).apply { gravity = Gravity.CENTER }
                }
                addView(icon, FrameLayout.LayoutParams(ctx.dp(52), ctx.dp(52), Gravity.CENTER))
                setOnClickListener { if (pkg != null) host.launch(pkg) else host.pickDockSlot(i) }
                setOnLongClickListener { host.pickDockSlot(i); true }
            }
            dock.addView(slot, LP(0, -1, 1f))
        }
        root.addView(dock, LP(-1, ctx.dp(76)).apply { setMargins(ctx.dp(40), ctx.dp(4), ctx.dp(40), 0) })
        return root
    }
}
