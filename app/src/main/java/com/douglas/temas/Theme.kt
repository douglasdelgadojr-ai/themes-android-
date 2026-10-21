package com.douglas.temas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/** App de navegacion exclusiva. Para cambiarla, solo se edita esta linea. */
const val NAV_PKG = "com.waze"

/** Cada tema es una clase que construye su propia pantalla de inicio. */
interface LauncherTheme {
    val name: String
    fun build(ctx: Context, host: ThemeHost): View
}

/** Lo que un tema puede pedirle al launcher. */
interface ThemeHost {
    fun launch(pkg: String?)
    fun openDrawer()
    fun pickDockSlot(slot: Int)
    fun dockPackage(slot: Int): String?   // null = nunca asignado, "" = quitado a propósito
    fun clearSlot(slot: Int)
    fun media(keyCode: Int)
    fun nextTheme()
    fun nowPlaying(): NowPlaying?   // null = falta permiso de acceso a notificaciones
    fun requestMediaAccess()
    fun openSettings(action: String)
}

data class NowPlaying(val title: String, val artist: String, val playing: Boolean, val art: Bitmap?, val pkg: String = "", val source: String = "")

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun grad(ctx: Context, o: GradientDrawable.Orientation, a: Long, b: Long, radiusDp: Int = 0) =
    GradientDrawable(o, intArrayOf(a.toInt(), b.toInt())).apply { cornerRadius = ctx.dp(radiusDp).toFloat() }

fun label(ctx: Context, text: String, sp: Float, bold: Boolean = false) = TextView(ctx).apply {
    this.text = text; textSize = sp; setTextColor(Color.WHITE)
    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
}


/** Barra inferior reutilizable: boton de apps (mantener = cambiar tema), dock de 5 y reloj. */
fun dockRow(ctx: Context, host: ThemeHost): LinearLayout {
    val bar = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xAA000000.toInt()) }
    bar.addView(label(ctx, "▦", 30f).apply {
        gravity = Gravity.CENTER
        setOnClickListener { host.openDrawer() }
        setOnLongClickListener { host.nextTheme(); true }
    }, LinearLayout.LayoutParams(ctx.dp(70), -1))
    val dock = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
    for (i in 0 until 5) {
        val pkg = host.dockPackage(i)
        val slot = FrameLayout(ctx).apply {
            val icon: View = if (pkg.isNullOrEmpty()) label(ctx, "+", 30f).apply { gravity = Gravity.CENTER } else IconStyle.view(ctx, pkg, 50)
            addView(icon, FrameLayout.LayoutParams(ctx.dp(50), ctx.dp(50), Gravity.CENTER))
            setOnClickListener { if (!pkg.isNullOrEmpty()) host.launch(pkg) else host.pickDockSlot(i) }
            setOnLongClickListener { host.pickDockSlot(i); true }
        }
        dock.addView(slot, LinearLayout.LayoutParams(0, -1, 1f))
    }
    bar.addView(dock, LinearLayout.LayoutParams(0, -1, 1f))
    bar.addView(TextClock(ctx).apply {
        format12Hour = "HH:mm"; format24Hour = "HH:mm"; textSize = 22f; setTextColor(-1); gravity = Gravity.CENTER
    }, LinearLayout.LayoutParams(ctx.dp(90), -1))
    return bar
}
