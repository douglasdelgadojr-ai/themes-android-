package com.douglas.temas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object IconStyle {
    const val ORIGINAL = 0
    const val GLASS = 1
    const val COLOR = 2
    const val LIGHT = 3
    const val DARK = 4
    const val TINT = 5
    private val names = arrayOf("Original", "Cristal", "Color", "Claro", "Oscuro", "Teñido")
    val styleNames: Array<String> get() = names

    val tintNames = arrayOf("Azul", "Rosa", "Verde", "Naranja", "Morado", "Rojo", "Turquesa")
    private val tintColors = longArrayOf(0xFF4F8CFF, 0xFFFF5C8A, 0xFF34C759, 0xFFFF9F0A, 0xFFAF52DE, 0xFFFF453A, 0xFF30D5C8)
        .map { it.toInt() }.toIntArray()

    private const val SIZE = 192
    private val cache = object : LruCache<String, Bitmap>(12 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun mode(ctx: Context): Int = prefs(ctx).getInt("icon_style", GLASS).coerceIn(0, names.size - 1)
    fun modeName(ctx: Context): String = names[mode(ctx)]
    fun cycle(ctx: Context) { prefs(ctx).edit().putInt("icon_style", (mode(ctx) + 1) % names.size).apply() }

    fun appMode(ctx: Context, pkg: String): Int {
        val v = prefs(ctx).getInt("icon_style_$pkg", -1)
        return if (v in 0 until names.size) v else -1
    }
    fun setAppMode(ctx: Context, pkg: String, m: Int) {
        val e = prefs(ctx).edit()
        if (m < 0) e.remove("icon_style_$pkg") else e.putInt("icon_style_$pkg", m)
        e.apply()
    }
    private fun modeFor(ctx: Context, pkg: String): Int = appMode(ctx, pkg).let { if (it >= 0) it else mode(ctx) }

    fun tintIndex(ctx: Context): Int = prefs(ctx).getInt("icon_tint", 0).coerceIn(0, tintColors.size - 1)
    fun setTint(ctx: Context, i: Int) { prefs(ctx).edit().putInt("icon_tint", i).apply() }
    fun tintName(ctx: Context): String = tintNames[tintIndex(ctx)]

    fun customFile(ctx: Context, pkg: String): File {
        val dir = File(ctx.filesDir, "icons")
        dir.mkdirs()
        return File(dir, pkg.replace('/', '_') + ".png")
    }
    fun hasCustom(ctx: Context, pkg: String): Boolean = customFile(ctx, pkg).exists()

    private fun hiRes(ctx: Context, pkg: String, fallback: Drawable?): Drawable? {
        try {
            val pm = ctx.packageManager
            val comp = pm.getLaunchIntentForPackage(pkg)?.component
            val res = if (comp != null) pm.getActivityInfo(comp, 0).let { if (it.icon != 0) it.icon else it.applicationInfo.icon }
                      else pm.getApplicationInfo(pkg, 0).icon
            if (res != 0) {
                val d = pm.getResourcesForApplication(pkg).getDrawableForDensity(res, DisplayMetrics.DENSITY_XXHIGH, null)
                if (d != null) return d
            }
        } catch (e: Exception) { }
        if (fallback != null) return fallback
        return try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    )

    private fun squircle(size: Float, inset: Float = 0f): Path {
        val r = size / 2f - inset
        val c = size / 2f
        val path = Path()
        val steps = 120
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val ct = cos(t); val st = sin(t)
            val x = c + r * sign(ct).toFloat() * abs(ct).pow(0.4).toFloat()
            val y = c + r * sign(st).toFloat() * abs(st).pow(0.4).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun drawTo(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun avg(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 16, 16, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (y in 0 until 16) for (x in 0 until 16) {
            val p = s.getPixel(x, y)
            if (Color.alpha(p) > 128) { r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++ }
        }
        if (n == 0L) return 0xFF37474F.toInt()
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    private fun finish(c: Canvas, f: Float) {
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        c.drawPath(squircle(f), mask)
        c.restore()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x26FFFFFF }
        c.drawPath(squircle(f, 1.5f), stroke)
    }

    private fun renderCustom(file: File): Bitmap? {
        val src = BitmapFactory.decodeFile(file.path) ?: return null
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val f = SIZE.toFloat()
        c.saveLayer(0f, 0f, f, f, null)
        c.drawBitmap(src, null, android.graphics.RectF(0f, 0f, f, f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        finish(c, f)
        return out
    }

    private fun render(d: Drawable, m: Int, tint: Int): Bitmap {
        val f = SIZE.toFloat()
        if (m == ORIGINAL) return drawTo(d, SIZE)

        val pad = (f * 0.14f).toInt()
        val glyph = drawTo(d, SIZE - 2 * pad)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.saveLayer(0f, 0f, f, f, null)

        val (c1, c2) = when (m) {
            GLASS -> 0x38FFFFFF to 0x14FFFFFF
            COLOR -> avg(glyph).let { mix(it, Color.BLACK, 0.35f) to mix(it, Color.BLACK, 0.7f) }
            LIGHT -> 0xFFFFFFFF.toInt() to 0xFFD6D9E0.toInt()
            DARK -> 0xFF2B2E36.toInt() to 0xFF0E1015.toInt()
            else -> mix(tint, Color.BLACK, 0.72f) to mix(tint, Color.BLACK, 0.9f)
        }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, f, f, c1, c2, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, f, f, bg)

        val gp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (m == DARK) {
            gp.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                .72f, 0f, 0f, 0f, 0f,  0f, .72f, 0f, 0f, 0f,  0f, 0f, .72f, 0f, 0f,  0f, 0f, 0f, 1f, 0f)))
        } else if (m == TINT) {
            val k = 1.25f
            val tr = Color.red(tint) / 255f; val tg = Color.green(tint) / 255f; val tb = Color.blue(tint) / 255f
            gp.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                tr * .299f * k, tr * .587f * k, tr * .114f * k, 0f, 0f,
                tg * .299f * k, tg * .587f * k, tg * .114f * k, 0f, 0f,
                tb * .299f * k, tb * .587f * k, tb * .114f * k, 0f, 0f,
                0f, 0f, 0f, 1f, 0f)))
        }
        c.drawBitmap(glyph, pad.toFloat(), pad.toFloat(), gp)
        glyph.recycle()
        finish(c, f)
        return out
    }

    fun view(ctx: Context, pkg: String, sizeDp: Int, fallback: Drawable? = null): View {
        val custom = customFile(ctx, pkg)
        val m = modeFor(ctx, pkg)
        val tint = tintColors[tintIndex(ctx)]
        val key = if (custom.exists()) "c|$pkg|${custom.lastModified()}" else "$pkg|$m|${if (m == TINT) tint else 0}"
        var bmp: Bitmap? = cache.get(key)
        if (bmp == null) {
            bmp = try {
                if (custom.exists()) renderCustom(custom)
                else hiRes(ctx, pkg, fallback)?.let { render(it, m, tint) }
            } catch (e: Exception) { null }
            if (bmp != null) cache.put(key, bmp)
        }
        return ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (bmp != null) setImageBitmap(bmp)
        }
    }
}
