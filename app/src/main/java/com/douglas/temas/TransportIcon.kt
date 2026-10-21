package com.douglas.temas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Iconos dibujados con Canvas: no dependen de que la fuente de la radio tenga los simbolos. */
class TransportIcon(ctx: Context, var kind: Kind, private val color: Int) : View(ctx) {
    enum class Kind { PLAY, PAUSE, PREV, NEXT, NAV }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun set(k: Kind) { if (k != kind) { kind = k; invalidate() } }

    private fun poly(c: Canvas, vararg xy: Float) {
        val path = Path()
        path.moveTo(xy[0], xy[1])
        var i = 2
        while (i < xy.size) { path.lineTo(xy[i], xy[i + 1]); i += 2 }
        path.close()
        c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        p.color = color
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) * 0.30f
        when (kind) {
            Kind.PLAY -> poly(c, cx - s * 0.7f, cy - s, cx - s * 0.7f, cy + s, cx + s, cy)
            Kind.PAUSE -> {
                c.drawRect(cx - s * 0.8f, cy - s, cx - s * 0.2f, cy + s, p)
                c.drawRect(cx + s * 0.2f, cy - s, cx + s * 0.8f, cy + s, p)
            }
            Kind.PREV -> {
                c.drawRect(cx - s, cy - s, cx - s * 0.6f, cy + s, p)
                poly(c, cx + s, cy - s, cx + s, cy + s, cx - s * 0.5f, cy)
            }
            Kind.NEXT -> {
                c.drawRect(cx + s * 0.6f, cy - s, cx + s, cy + s, p)
                poly(c, cx - s, cy - s, cx - s, cy + s, cx + s * 0.5f, cy)
            }
            Kind.NAV -> poly(c, cx, cy - s * 1.2f, cx + s * 0.9f, cy + s, cx, cy + s * 0.4f, cx - s * 0.9f, cy + s)
        }
    }
}
