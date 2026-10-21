package com.douglas.temas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File

/** Capa de personalización de íconos: estilo (original / vidrio / color) e imagen propia por app. */
object IconStyle {
    const val ORIGINAL = 0
    const val GLASS = 1
    const val COLOR = 2
    private val names = arrayOf("Original", "Vidrio", "Color")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun mode(ctx: Context): Int = prefs(ctx).getInt("icon_style", GLASS).coerceIn(0, 2)
    fun modeName(ctx: Context): String = names[mode(ctx)]
    fun cycle(ctx: Context) { prefs(ctx).edit().putInt("icon_style", (mode(ctx) + 1) % 3).apply() }

    fun customFile(ctx: Context, pkg: String): File {
        val dir = File(ctx.filesDir, "icons")
        dir.mkdirs()
        return File(dir, pkg.replace('/', '_') + ".png")
    }

    fun hasCustom(ctx: Context, pkg: String): Boolean = customFile(ctx, pkg).exists()

    /** Imagen propia si existe; si no, el ícono que se pase o el de la app. */
    fun drawable(ctx: Context, pkg: String, fallback: Drawable? = null): Drawable? {
        val f = customFile(ctx, pkg)
        if (f.exists()) {
            val bmp = BitmapFactory.decodeFile(f.path)
            if (bmp != null) return BitmapDrawable(ctx.resources, bmp)
        }
        if (fallback != null) return fallback
        return try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    )

    /** Color promedio del ícono (para el estilo "Color"). */
    private fun avg(d: Drawable): Int {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val old = d.copyBounds()
        d.setBounds(0, 0, 16, 16)
        d.draw(Canvas(bmp))
        d.setBounds(old.left, old.top, old.right, old.bottom)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until 16) for (x in 0 until 16) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) > 128) { r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++ }
        }
        bmp.recycle()
        if (n == 0L) return 0xFF37474F.toInt()
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** Vista del ícono ya con el estilo aplicado. El tamaño final lo pone quien la agrega (LayoutParams). */
    fun view(ctx: Context, pkg: String, sizeDp: Int, fallback: Drawable? = null): View {
        val d = drawable(ctx, pkg, fallback)
        val s = ctx.dp(sizeDp).toFloat()
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (d != null) setImageDrawable(d)
        }
        if (hasCustom(ctx, pkg)) {
            img.clipToOutline = true
            img.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, s * 0.24f) }
            }
            return img
        }
        val m = mode(ctx)
        if (m == ORIGINAL || d == null) return img

        val bg: GradientDrawable = if (m == GLASS) {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x38FFFFFF, 0x14FFFFFF))
        } else {
            val c = avg(d)
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(mix(c, Color.BLACK, 0.35f), mix(c, Color.BLACK, 0.7f)))
        }
        bg.cornerRadius = s * 0.26f
        bg.setStroke(ctx.dp(1).coerceAtLeast(1), 0x26FFFFFF)
        val pad = (s * 0.15f).toInt()
        return FrameLayout(ctx).apply {
            background = bg
            setPadding(pad, pad, pad, pad)
            addView(img, FrameLayout.LayoutParams(-1, -1))
        }
    }
}
