package com.douglas.temas

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation.*
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.*
import android.widget.LinearLayout.LayoutParams as LP

/** Cambia el texto solo si es distinto (así no se reinicia el desplazamiento del título largo). */
private fun TextView.setIfChanged(s: CharSequence) { if (text.toString() != s.toString()) text = s }

/** Entrada suave: aparece subiendo unos píxeles. */
private fun animIn(v: View) {
    v.alpha = 0f; v.translationY = v.context.dp(8).toFloat()
    v.animate().alpha(1f).translationY(0f).setDuration(350).start()
}

private fun fmtTime(ms: Long): String { val s = ms / 1000; return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }

/** Color dominante de la carátula (favorece colores saturados ni muy claros ni muy oscuros). */
private fun dominantColor(src: Bitmap): Int {
    val s = Bitmap.createScaledBitmap(src, 24, 24, true)
    var r = 0.0; var g = 0.0; var bl = 0.0; var w = 0.0
    for (y in 0 until 24) for (x in 0 until 24) {
        val px = s.getPixel(x, y)
        val rr = Color.red(px); val gg = Color.green(px); val bb = Color.blue(px)
        val hi = maxOf(rr, gg, bb); val lo = minOf(rr, gg, bb)
        val sat = (hi - lo).toDouble() / (if (hi == 0) 1 else hi)
        val lum = (rr + gg + bb) / 765.0
        val k = sat * maxOf(0.05, 1 - Math.abs(lum - 0.5) * 1.4) + 0.02
        r += rr * k; g += gg * k; bl += bb * k; w += k
    }
    if (s !== src) s.recycle()
    return Color.rgb((r / w).toInt(), (g / w).toInt(), (bl / w).toInt())
}

/** Barra de progreso delgada con extremos redondeados. */
class SlimBar(ctx: Context) : View(ctx) {
    var frac: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var color: Int = 0xFFFF6A57.toInt()
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        val h = height.toFloat(); val rad = h / 2f
        p.color = 0x2EFFFFFF; r.set(0f, 0f, width.toFloat(), h); c.drawRoundRect(r, rad, rad, p)
        if (frac > 0f) { p.color = color; r.set(0f, 0f, width * frac, h); c.drawRoundRect(r, rad, rad, p) }
    }
}

/**
 * Tema BJ30 (oscuro): barra lateral fija, reproductor con planeta que cambia según la fuente
 * (radio / CarPlay / música), 4 tarjetas editables arriba a la derecha y la temperatura de Managua en la barra inferior.
 * Tarjetas (slots 20-23) y barra lateral (slots 10-13): mantén pulsado un ícono para ponerlo o quitarlo.
 */
class BjTheme : LauncherTheme {
    override val name = "BJ30"

    // Tamaños en dp. Si algo se ve chico o grande, cambia solo estos números.
    private val RAIL_ICON = 72
    private val CARD_ICON = 84
    private val CARD_H = 170

    private var apps: List<ResolveInfo> = emptyList()

    /** Apps que se ponen solas la primera vez (por nombre o paquete). Luego tú las cambias. */
    private val defaults: Map<Int, List<String>> = mapOf(
        10 to listOf("medios", "music", "música"),
        11 to listOf("teléfono", "telefono", "dialer"),
        12 to listOf(NAV_PKG),
        13 to listOf("ajustes", "settings", "configuración"),
        20 to listOf("carplay", "rlink"),
        21 to listOf("easyconn"),
        22 to listOf("bluetooth"),
        23 to listOf("foto", "galer", "gallery")
    )

    private fun glass(ctx: Context, r: Int) =
        GradientDrawable(TL_BR, intArrayOf(0x22FFFFFF, 0x0CFFFFFF)).apply {
            cornerRadius = ctx.dp(r).toFloat()
            setStroke(ctx.dp(1).coerceAtLeast(1), 0x1CFFFFFF)
        }

    private fun findApp(keys: List<String>): String? {
        for (k in keys) {
            for (ri in apps) {
                val pkg = ri.activityInfo.packageName
                if (pkg.contains(k, true)) return pkg
            }
        }
        return null
    }

    private fun findByLabel(ctx: Context, keys: List<String>): String? {
        val pm = ctx.packageManager
        for (k in keys) {
            for (ri in apps) {
                val lbl = ri.loadLabel(pm).toString()
                if (lbl.contains(k, true)) return ri.activityInfo.packageName
            }
        }
        return null
    }

    /** null = vacío. "" guardado = el usuario lo quitó a propósito. */
    private fun resolve(ctx: Context, host: ThemeHost, slot: Int, editable: Boolean): String? {
        val saved = if (editable) host.dockPackage(slot) else null
        if (saved != null) return saved.ifEmpty { null }
        val keys = defaults[slot] ?: return null
        return findApp(keys) ?: findByLabel(ctx, keys)
    }

    /** Clima de Managua desde Open-Meteo (sin clave). Si la radio no tiene internet, se queda en "Sin conexión". */
    private fun fetchWeather(root: View, temp: TextView) {
        Thread {
            var ok = false
            try {
                val url = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=12.136&longitude=-86.251&current=temperature_2m")
                val c = url.openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val txt = c.inputStream.bufferedReader().use { it.readText() }
                val cur = org.json.JSONObject(txt).getJSONObject("current")
                val t = Math.round(cur.getDouble("temperature_2m")).toInt()
                root.post { temp.text = "$t°C" }
                ok = true
            } catch (e: Exception) { }
            root.postDelayed({ if (root.isAttachedToWindow) fetchWeather(root, temp) }, if (ok) 15 * 60 * 1000L else 2 * 60 * 1000L)
        }.start()
    }

    private fun appLabel(ctx: Context, pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun slot(ctx: Context, host: ThemeHost, n: Int, iconDp: Int, labeled: Boolean, card: Boolean, editable: Boolean = true): View {
        val pkg = resolve(ctx, host, n, editable)
        val pm = ctx.packageManager
        var appName = ""
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            if (card) { background = glass(ctx, 22); setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6)) }
        }
        val icon: View = try {
            appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg!!, 0)).toString()
            IconStyle.view(ctx, pkg, iconDp)
        } catch (e: Exception) {
            appName = ""
            label(ctx, if (editable) "+" else "", 30f).apply {
                gravity = Gravity.CENTER; setTextColor(0x66FFFFFF)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x14FFFFFF) }
            }
        }
        box.addView(icon, LP(ctx.dp(iconDp), ctx.dp(iconDp)))
        if (labeled && appName.isNotEmpty()) {
            box.addView(label(ctx, appName, 14f).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                setTextColor(0xFFB0BEC5.toInt())
            }, LP(-1, -2).apply { topMargin = ctx.dp(6) })
        }
        box.setOnClickListener { if (pkg != null) host.launch(pkg) else if (editable) host.pickDockSlot(n) }
        if (editable) box.setOnLongClickListener { host.pickDockSlot(n); true }
        return box
    }

    private fun iconButton(ctx: Context, icon: TransportIcon, bg: Int, onClick: () -> Unit) =
        FrameLayout(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
            addView(icon, FrameLayout.LayoutParams(-1, -1))
            setOnClickListener { onClick() }
        }

    override fun build(ctx: Context, host: ThemeHost): View {
        apps = ctx.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != ctx.packageName }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = grad(ctx, TOP_BOTTOM, 0xFF12232B, 0xFF0A1014, 0)
            setPadding(ctx.dp(14), ctx.dp(14), ctx.dp(14), ctx.dp(12))
        }
        val top = LinearLayout(ctx)
        root.addView(top, LP(-1, 0, 1f))

        // ---- Barra lateral (4 accesos fijos) ----
        val rail = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        for (i in 0 until 4) rail.addView(slot(ctx, host, 10 + i, RAIL_ICON, false, false, true), LP(-1, 0, 1f))
        top.addView(rail, LP(ctx.dp(92), -1))

        // ---- Reproductor (cambia según lo que suene: radio, CarPlay, música) ----
        val musicBg = glass(ctx, 28)     // se tiñe con el color de la carátula
        val music = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(16))
            background = musicBg
        }
        val source = label(ctx, "REPRODUCTOR", 11f, true).apply {
            setTextColor(0xFF8097A0.toInt()); letterSpacing = 0.15f; gravity = Gravity.CENTER
        }
        val title = label(ctx, "—", 22f, true).apply {
            setSingleLine(true); ellipsize = TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1   // título largo: se desplaza
            isSelected = true; gravity = Gravity.CENTER
        }
        val artist = label(ctx, "", 15f).apply {
            setTextColor(0xFF8097A0.toInt()); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        }
        val planet = PlanetView(ctx)
        music.addView(source, LP(-1, -2))
        music.addView(title, LP(-1, -2))
        music.addView(artist, LP(-1, -2))
        music.addView(planet, LP(-1, 0, 1f).apply { setMargins(0, ctx.dp(6), 0, ctx.dp(6)) })

        // ---- Progreso: tiempo transcurrido, barra y duración (se oculta si la fuente no da duración) ----
        val elapsed = label(ctx, "0:00", 12f).apply { setTextColor(0xFFB0BEC5.toInt()) }
        val total = label(ctx, "0:00", 12f).apply { setTextColor(0xFFB0BEC5.toInt()) }
        val slim = SlimBar(ctx)
        val progressRow = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE
            addView(elapsed)
            addView(slim, LP(0, ctx.dp(4), 1f).apply { setMargins(ctx.dp(8), 0, ctx.dp(8), 0) })
            addView(total)
        }
        music.addView(progressRow, LP(-1, -2).apply { bottomMargin = ctx.dp(8) })

        val ctl = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        fun btnLP(size: Int) = LP(ctx.dp(size), ctx.dp(size)).apply { setMargins(ctx.dp(10), 0, ctx.dp(10), 0) }
        ctl.addView(iconButton(ctx, TransportIcon(ctx, TransportIcon.Kind.PREV, Color.WHITE), 0x22FFFFFF) { host.media(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, btnLP(58))
        val playIcon = TransportIcon(ctx, TransportIcon.Kind.PLAY, Color.BLACK)
        ctl.addView(iconButton(ctx, playIcon, Color.WHITE) { host.media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }, btnLP(70))
        ctl.addView(iconButton(ctx, TransportIcon(ctx, TransportIcon.Kind.NEXT, Color.WHITE), 0x22FFFFFF) { host.media(KeyEvent.KEYCODE_MEDIA_NEXT) }, btnLP(58))
        music.addView(ctl, LP(-1, -2))
        top.addView(music, LP(0, -1, 0.95f).apply { setMargins(ctx.dp(8), 0, ctx.dp(7), 0) })

        // ---- Columna derecha: 4 tarjetas editables ----
        val right = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val cards = LinearLayout(ctx)
        for (i in 0 until 4) cards.addView(slot(ctx, host, 20 + i, CARD_ICON, true, true),
            LP(0, -1, 1f).apply { setMargins(ctx.dp(5), 0, ctx.dp(5), 0) })
        right.addView(cards, LP(-1, ctx.dp(CARD_H)))

        top.addView(right, LP(0, -1, 1.55f))

        // ---- Barra inferior: apps (mantener = cambiar tema), temperatura y reloj ----
        val bar = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL; background = glass(ctx, 26)
            setPadding(ctx.dp(10), 0, ctx.dp(16), 0)
        }
        val appsBtn = FrameLayout(ctx).apply {
            addView(LineIcon(ctx, LineIcon.Kind.GRID, Color.WHITE), FrameLayout.LayoutParams(ctx.dp(40), ctx.dp(40), Gravity.CENTER))
            setOnClickListener { host.openDrawer() }
            setOnLongClickListener { host.nextTheme(); true }
        }
        bar.addView(appsBtn, LP(ctx.dp(84), -1))
        bar.addView(View(ctx), LP(0, -1, 1f))   // aquí irá la función nueva de la barra; por ahora empuja temperatura y reloj a la derecha
        val temp = label(ctx, "--°C", 21f, true).apply { gravity = Gravity.CENTER }
        bar.addView(temp, LP(ctx.dp(84), -1))
        fetchWeather(root, temp)
        bar.addView(TextClock(ctx).apply {
            format12Hour = "h:mm a\nEEE d MMM"; format24Hour = "h:mm a\nEEE d MMM"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, LP(ctx.dp(120), -1))
        root.addView(bar, LP(-1, ctx.dp(84)).apply { topMargin = ctx.dp(14) })

        // ---- Color del reproductor según la carátula (fondo de la tarjeta, borde y barra de progreso) ----
        val neutral = intArrayOf(0x22FFFFFF, 0x0CFFFFFF, 0x1CFFFFFF, 0xFFFF6A57.toInt())
        val tintCur = neutral.copyOf()
        var tintAnim: ValueAnimator? = null
        fun paintTint() {
            musicBg.setColors(intArrayOf(tintCur[0], tintCur[1]))
            musicBg.setStroke(ctx.dp(1).coerceAtLeast(1), tintCur[2])
            slim.color = tintCur[3]
        }
        fun applyTint(c: Int?) {
            val rgb = (c ?: 0) and 0xFFFFFF
            val to = if (c == null) neutral
                     else intArrayOf((0x99 shl 24) or rgb, (0x29 shl 24) or rgb, (0x80 shl 24) or rgb, (0xFF shl 24) or rgb)
            val from = tintCur.copyOf()
            tintAnim?.cancel()
            val ev = ArgbEvaluator()
            tintAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 600
                addUpdateListener { a ->
                    val t = a.animatedValue as Float
                    for (i in 0..3) tintCur[i] = ev.evaluate(t, from[i], to[i]) as Int
                    paintTint()
                }
                start()
            }
        }

        // Actualiza la música cada segundo mientras la pantalla exista
        var lastNp: NowPlaying? = null
        var lastKey = ""
        var hadArt = false
        val tick = object : Runnable {
            override fun run() {
                if (!root.isAttachedToWindow) return
                val np = host.nowPlaying()
                lastNp = np
                if (np == null) {
                    title.setIfChanged("Dar acceso a la música"); artist.setIfChanged("Toca aquí y activa «Temas»")
                    music.setOnClickListener { host.requestMediaAccess() }
                    planet.setPlaying(false)
                    if (hadArt) { hadArt = false; planet.setCover(null); applyTint(null) }
                } else {
                    val lbl = if (np.pkg.isEmpty()) "" else appLabel(ctx, np.pkg)
                    // canción nueva: el título y el artista entran con una animación
                    val key = np.pkg + "|" + np.title + "|" + np.artist
                    val changed = key != lastKey
                    if (changed) { lastKey = key; animIn(title); animIn(artist) }
                    if (np.source.isNotEmpty()) {
                        // fuente sin sesión de música (CarPlay, Android Auto...)
                        source.setIfChanged(np.source); title.textSize = 22f; title.setIfChanged(np.title); artist.setIfChanged(np.artist)
                    } else {
                        val radio = np.pkg.contains("radio", true) || np.pkg.contains("tuner", true) || lbl.contains("radio", true)
                        source.setIfChanged(if (np.pkg.isEmpty()) "REPRODUCTOR" else if (radio) "RADIO" else lbl.uppercase())
                        title.textSize = if (radio) 40f else 22f
                        title.setIfChanged(np.title.ifEmpty { if (np.playing) lbl.ifEmpty { "Reproduciendo" } else "Sin reproducción" }); artist.setIfChanged(np.artist)
                    }
                    // carátula dentro del círculo (si la canción no trae, vuelve el planeta) y color del reproductor
                    val art = np.art
                    if (changed || (art != null) != hadArt) {
                        hadArt = art != null
                        planet.setCover(art)
                        applyTint(if (art != null) dominantColor(art) else null)
                    }
                    playIcon.set(if (np.playing) TransportIcon.Kind.PAUSE else TransportIcon.Kind.PLAY)
                    planet.setPlaying(np.playing)
                    if (np.pkg.isNotEmpty()) music.setOnClickListener { host.launch(np.pkg) }
                    else { music.setOnClickListener(null); music.isClickable = false }
                }
                root.postDelayed(this, 1000)
            }
        }
        // Progreso: se actualiza cada medio segundo con lo último leído (sin volver a preguntarle al sistema)
        val prog = object : Runnable {
            override fun run() {
                if (!root.isAttachedToWindow) return
                val n = lastNp
                if (n != null && n.dur > 0 && n.source.isEmpty()) {
                    var pos = if (n.pos < 0) 0L else n.pos
                    if (n.playing && n.pos >= 0 && n.posAt > 0) pos += ((SystemClock.elapsedRealtime() - n.posAt) * n.speed).toLong()
                    pos = pos.coerceIn(0L, n.dur)
                    slim.frac = pos.toFloat() / n.dur
                    elapsed.setIfChanged(fmtTime(pos)); total.setIfChanged(fmtTime(n.dur))
                    progressRow.visibility = View.VISIBLE
                } else progressRow.visibility = View.GONE
                root.postDelayed(this, 500)
            }
        }
        root.post(prog)
        root.post(tick)
        return root
    }
}

/** Planeta low-poly dibujado con Canvas; gira y tiene un satélite cuando suena música. */
class PlanetView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var spin = 0f
    private var orbit = 0f
    private var playing = false
    private var anim: ValueAnimator? = null

    // carátula (se dibuja recortada en el círculo del planeta, con fundido entre canciones)
    private var curShader: BitmapShader? = null
    private var prevShader: BitmapShader? = null
    private var curAlpha = 0f
    private var prevAlpha = 0f
    private var prevStart = 0f
    private var coverAnim: ValueAnimator? = null
    private val shaderMatrix = Matrix()

    private fun shaderFor(src: Bitmap): BitmapShader {
        val side = minOf(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val target = if (side > 400) 400 else side          // la radio tiene poca RAM: no guardamos imágenes enormes
        val bmp = if (target != side) Bitmap.createScaledBitmap(sq, target, target, true) else sq
        val sh = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val k = 156f / bmp.width
        shaderMatrix.setScale(k, k); shaderMatrix.postTranslate(47f, 37f)
        sh.setLocalMatrix(shaderMatrix)
        return sh
    }

    /** Pone la carátula en el círculo; null = vuelve el planeta. Cruza con un fundido de 0,6 s. */
    fun setCover(src: Bitmap?) {
        coverAnim?.cancel()
        prevShader = curShader; prevStart = curAlpha
        curShader = if (src != null) try { shaderFor(src) } catch (e: Exception) { null } else null
        if (prevShader == null && curShader == null) { curAlpha = 0f; prevAlpha = 0f; invalidate(); return }
        coverAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            addUpdateListener {
                val f = it.animatedValue as Float
                curAlpha = if (curShader != null) f else 0f
                prevAlpha = if (curShader != null) prevStart else prevStart * (1f - f)
                if (f >= 1f) { prevShader = null; prevAlpha = 0f }
                invalidate()
            }
            start()
        }
    }

    private fun drawCover(c: Canvas, sh: BitmapShader?, a: Float) {
        if (sh == null || a <= 0f) return
        p.reset(); p.isAntiAlias = true; p.shader = sh; p.alpha = (a * 255).toInt().coerceIn(0, 255)
        c.drawCircle(125f, 115f, 78f, p)
        p.shader = null
    }

    private val pts = listOf(
        floatArrayOf(125f, 37f, 178f, 70f, 150f, 110f),
        floatArrayOf(125f, 37f, 72f, 70f, 100f, 110f),
        floatArrayOf(178f, 70f, 200f, 125f, 150f, 110f),
        floatArrayOf(72f, 70f, 50f, 125f, 100f, 110f),
        floatArrayOf(100f, 110f, 150f, 110f, 125f, 160f),
        floatArrayOf(50f, 125f, 100f, 110f, 90f, 175f),
        floatArrayOf(200f, 125f, 150f, 110f, 160f, 175f),
        floatArrayOf(90f, 175f, 125f, 160f, 125f, 193f),
        floatArrayOf(160f, 175f, 125f, 160f, 125f, 193f)
    )
    private val cols = intArrayOf(
        0xFFF27A6B.toInt(), 0xFFC9443A.toInt(), 0xFFD24E42.toInt(), 0xFFEE6D5F.toInt(), 0xFFF58B7C.toInt(),
        0xFFBF3D33.toInt(), 0xFFA8362D.toInt(), 0xFFD9574A.toInt(), 0xFFB33D33.toInt()
    )

    fun setPlaying(on: Boolean) {
        if (on == playing) return
        playing = on
        if (on) startAnim() else { anim?.cancel(); anim = null }
    }

    private fun startAnim() {
        if (!isAttachedToWindow) return
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 26000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { spin = it.animatedValue as Float; orbit = (spin * 3f) % 360f; invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (playing) startAnim() }
    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; coverAnim?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val s = minOf(width / 250f, height / 230f)
        c.save()
        c.translate((width - 250f * s) / 2f, (height - 230f * s) / 2f)
        c.scale(s, s)

        // órbita punteada
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.color = 0x55FFFFFF
        p.strokeCap = Paint.Cap.ROUND; p.pathEffect = DashPathEffect(floatArrayOf(2f, 6f), 0f)
        c.save(); c.rotate(-18f, 125f, 115f); c.drawOval(RectF(7f, 73f, 243f, 157f), p); c.restore()

        // satélite
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = 0xFFF6C64A.toInt()
        c.save(); c.rotate(orbit, 125f, 115f); c.drawCircle(125f, 20f, 9f, p); c.restore()

        // planeta
        c.save(); c.rotate(spin, 125f, 115f)
        p.color = 0xFFE0584A.toInt(); c.drawCircle(125f, 115f, 78f, p)
        for (i in pts.indices) {
            val a = pts[i]
            path.reset(); path.moveTo(a[0], a[1]); path.lineTo(a[2], a[3]); path.lineTo(a[4], a[5]); path.close()
            p.color = cols[i]; c.drawPath(path, p)
        }
        c.restore()

        // carátula encima del planeta (la anterior queda debajo mientras entra la nueva)
        drawCover(c, prevShader, prevAlpha)
        drawCover(c, curShader, curAlpha)

        // luz fija sobre el planeta
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
        p.shader = RadialGradient(
            102f, 84f, 117f, intArrayOf(0x47FFFFFF, 0x00000000, 0x73000000),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(125f, 115f, 78f, p)
        p.shader = null

        // lunita
        p.color = 0xFFF0A4A8.toInt(); c.drawCircle(30f, 150f, 9f, p)
        c.restore()
    }
}

/** Iconos de línea dibujados con Canvas (no dependen de la fuente de la radio). */
class LineIcon(ctx: Context, private val kind: Kind, private val color: Int) : View(ctx) {
    enum class Kind { GRID, VOL }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        val u = minOf(width, height) / 24f
        c.save()
        c.translate((width - 24f * u) / 2f, (height - 24f * u) / 2f)
        c.scale(u, u)
        p.color = color; p.strokeWidth = 1.8f
        when (kind) {
            Kind.GRID -> {
                c.drawRoundRect(RectF(3f, 3f, 10f, 10f), 1.5f, 1.5f, p)
                c.drawRoundRect(RectF(14f, 3f, 21f, 10f), 1.5f, 1.5f, p)
                c.drawRoundRect(RectF(3f, 14f, 10f, 21f), 1.5f, 1.5f, p)
                c.drawRoundRect(RectF(14f, 14f, 21f, 21f), 1.5f, 1.5f, p)
            }
            Kind.VOL -> {
                val sp = Path()
                sp.moveTo(11f, 5f); sp.lineTo(6f, 9f); sp.lineTo(2f, 9f)
                sp.lineTo(2f, 15f); sp.lineTo(6f, 15f); sp.lineTo(11f, 19f); sp.close()
                c.drawPath(sp, p)
                c.drawArc(RectF(8f, 7f, 20f, 17f), -45f, 90f, false, p)
                c.drawArc(RectF(6f, 3f, 24f, 21f), -45f, 90f, false, p)
            }
        }
        c.restore()
    }
}
