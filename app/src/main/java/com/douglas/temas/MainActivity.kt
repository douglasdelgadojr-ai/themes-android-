package com.douglas.temas

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.FileOutputStream

class MainActivity : Activity(), ThemeHost {
    private val themes: List<LauncherTheme> = listOf(BjTheme(), DashboardTheme(), PanelTheme(), CardsTheme()) // aqui se agregan mas temas
    private lateinit var root: FrameLayout
    private var drawer: View? = null
    private var pickSlot = -1
    private var homeBanner: View? = null
    private var drawerGrid: GridView? = null
    private var drawerStyleBtn: TextView? = null
    private var drawerTintBtn: TextView? = null
    private var drawerTab = 0                 // 0 = Todas, 1 = Recientes, 2 = Favoritas
    private var drawerQuery = ""
    private var drawerRefresh: (() -> Unit)? = null
    private var projectionPkg: String? = null   // app de proyección (CarPlay/RLink...) abierta por última vez
    private var iconPickPkg: String? = null
    private var needsRebuild = false
    private val REQ_ICON = 77
    private val prefs by lazy { getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        root = FrameLayout(this)
        setContentView(root)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // Si la barra de la radio reaparece (por ejemplo al deslizar desde el borde), se vuelve a ocultar sola
        window.decorView.setOnSystemUiVisibilityChangeListener { v ->
            if (v and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) root.postDelayed({ hideBars() }, 2500)
        }
        hideBars()
        rebuild()
    }

    /** Modo inmersivo: oculta la barra superior (atrás / inicio / recientes) y la de navegación. */
    private fun hideBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun rebuild() {
        root.removeAllViews(); drawer = null; drawerGrid = null; homeBanner = null
        var idx = prefs.getInt("theme_bj", 0).coerceIn(0, themes.size - 1)
        for (n in themes.indices) {
            try { root.addView(themes[idx].build(this, this)); checkHome(); return }
            catch (e: Exception) { root.removeAllViews(); idx = (idx + 1) % themes.size }
        }
        root.addView(TextView(this).apply {
            text = "No se pudo cargar ningún tema"; setTextColor(Color.WHITE); textSize = 24f; gravity = Gravity.CENTER
        })
    }

    override fun onResume() { super.onResume(); hideBars(); checkHome() }

    /** Si no somos el home por defecto, muestra un aviso para elegirnos como pantalla de inicio. */
    private fun checkHome() {
        homeBanner?.let { root.removeView(it) }; homeBanner = null
        val ri = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        if (ri?.activityInfo?.packageName == packageName) return
        homeBanner = label(this, "Toca aquí para hacer de «Temas» tu pantalla de inicio", 15f).apply {
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = grad(this@MainActivity, GradientDrawable.Orientation.LEFT_RIGHT, 0xFFFF6A57, 0xFFE0584A, 20)
            setOnClickListener {
                try { startActivity(Intent("android.settings.HOME_SETTINGS")) }
                catch (e: Exception) {
                    try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
                    catch (e2: Exception) { Toast.makeText(this@MainActivity, "No disponible", Toast.LENGTH_SHORT).show() }
                }
            }
        }
        root.addView(homeBanner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(6) })
    }

    // ---- Favoritas y recientes (para el cajón de apps) ----
    private fun readList(key: String): MutableList<String> =
        (prefs.getString(key, "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()

    private fun favs(): MutableList<String> = readList("fav_apps")
    private fun recents(): MutableList<String> = readList("recent_apps")

    private fun pushRecent(pkg: String) {
        val l = recents()
        l.remove(pkg); l.add(0, pkg)
        prefs.edit().putString("recent_apps", l.take(12).joinToString(",")).apply()
    }

    private fun toggleFav(pkg: String) {
        val l = favs()
        if (l.contains(pkg)) l.remove(pkg) else l.add(pkg)
        prefs.edit().putString("fav_apps", l.joinToString(",")).apply()
        drawerRefresh?.invoke()
    }

    // ---- ThemeHost ----
    override fun launch(pkg: String?) {
        val i = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (i == null) {
            Toast.makeText(this, if (pkg == NAV_PKG) "Waze no está instalado" else "App no instalada", Toast.LENGTH_SHORT).show()
            return
        }
        if (pkg != null && isProjection(pkg)) projectionPkg = pkg
        if (pkg != null) pushRecent(pkg)
        try { startActivity(i) }
        catch (e: Exception) { Toast.makeText(this, "No se pudo abrir la app", Toast.LENGTH_SHORT).show() }
    }
    override fun dockPackage(slot: Int): String? = prefs.getString("dock_$slot", null)
    override fun pickDockSlot(slot: Int) { pickSlot = slot; openDrawer() }
    override fun clearSlot(slot: Int) { prefs.edit().putString("dock_$slot", "").apply(); rebuild() }
    override fun nextTheme() {
        prefs.edit().putInt("theme_bj", (prefs.getInt("theme_bj", 0) + 1) % themes.size).apply(); rebuild()
    }
    override fun media(keyCode: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun appLabelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { "" }

    private fun isProjection(pkg: String): Boolean {
        val k = (pkg + " " + appLabelOf(pkg)).lowercase()
        return listOf("rlink", "carplay", "easyconn", "zlink", "carlink", "android auto", "androidauto").any { k.contains(it) }
    }

    private fun isBluetoothSession(pkg: String): Boolean {
        val k = (pkg + " " + appLabelOf(pkg)).lowercase()
        return listOf("bluetooth", "btmusic", "bt music", "a2dp").any { k.contains(it) }
    }

    /** Paquete de la app que está sacando audio ahora (Android 8+; usa un método oculto, por eso la reflexión). */
    private fun audioSourcePkg(am: AudioManager): String? = try {
        val cfgs = AudioManager::class.java.getMethod("getActivePlaybackConfigurations").invoke(am) as? List<*>
        var found: String? = null
        if (cfgs != null) {
            for (cfg in cfgs) {
                if (cfg == null) continue
                val uid = cfg.javaClass.getMethod("getClientUid").invoke(cfg) as Int
                val pkgs = packageManager.getPackagesForUid(uid) ?: continue
                val p = pkgs.firstOrNull { it != packageName && it != "android" }
                if (p != null) { found = p; break }
            }
        }
        found
    } catch (e: Throwable) { null }

    private fun fromSession(c: android.media.session.MediaController, playing: Boolean): NowPlaying {
        val m = c.metadata
        return NowPlaying(
            m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
            m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            playing,
            m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            c.packageName
        )
    }

    override fun nowPlaying(): NowPlaying? = try {
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val all = msm.getActiveSessions(ComponentName(this, MediaListener::class.java))
        val musicOn = am.isMusicActive
        if (!musicOn) projectionPkg = null           // ya no suena nada: olvida la proyección
        val src = if (musicOn) audioSourcePkg(am) else null   // quién está sacando el audio de verdad
        val proj = projectionPkg ?: src?.takeIf { isProjection(it) }
        // Con CarPlay/RLink sonando, una sesión vieja de Bluetooth no cuenta
        val list = if (proj != null) all.filter { !isBluetoothSession(it.packageName) } else all
        val playing = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        when {
            playing != null -> fromSession(playing, true)
            musicOn && proj != null -> {
                val k = (proj + " " + appLabelOf(proj)).lowercase()
                when {
                    listOf("carplay", "rlink", "zlink", "carlink").any { k.contains(it) } ->
                        NowPlaying("Apple CarPlay", "Reproduciendo desde tu iPhone", true, null, proj, "CARPLAY")
                    k.contains("auto") ->
                        NowPlaying("Android Auto", "Reproduciendo desde tu teléfono", true, null, proj, "ANDROID AUTO")
                    else -> NowPlaying(appLabelOf(proj).ifEmpty { "Proyección" }, "Reproduciendo", true, null, proj, "PROYECCIÓN")
                }
            }
            musicOn && src != null -> NowPlaying("", "", true, null, src)   // app sin sesión de música (radio, etc.)
            musicOn -> NowPlaying("Reproduciendo", "", true, null)
            list.isNotEmpty() -> fromSession(list.first(), false)
            else -> NowPlaying("Sin reproducción", "", false, null)
        }
    } catch (e: SecurityException) { null } catch (e: Exception) { NowPlaying("Sin reproducción", "", false, null) }

    override fun requestMediaAccess() {
        try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        catch (e: Exception) { Toast.makeText(this, "No se pudo abrir ajustes", Toast.LENGTH_SHORT).show() }
    }

    override fun openSettings(action: String) {
        try { startActivity(Intent(action)) }
        catch (e: Exception) { Toast.makeText(this, "No disponible", Toast.LENGTH_SHORT).show() }
    }

    // ---- Cajón de apps ----
    private fun glassTile(r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x1CFFFFFF, 0x08FFFFFF)).apply {
        cornerRadius = dp(r).toFloat(); setStroke(dp(1).coerceAtLeast(1), 0x14FFFFFF)
    }

    private fun styleChip(c: TextView, on: Boolean) {
        c.background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            if (on) setColor(0xFFFF6A57.toInt()) else { setColor(0x14FFFFFF); setStroke(dp(1).coerceAtLeast(1), 0x1FFFFFFF) }
        }
        c.setTextColor(if (on) Color.WHITE else 0xFFB0BEC5.toInt())
    }

    private fun chip(text: String, on: Boolean, onClick: () -> Unit): TextView = label(this, text, 15f).apply {
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(9), dp(18), dp(9))
        styleChip(this, on)
        setOnClickListener { onClick() }
    }

    override fun openDrawer() {
        if (drawer != null) return
        val pm = packageManager
        val items: List<Pair<ResolveInfo, String>> = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }

        drawerTab = 0; drawerQuery = ""
        var shown: List<Pair<ResolveInfo, String>> = items

        val tile = { glassTile(22) }

        val grid = GridView(this).apply {
            numColumns = 6; horizontalSpacing = dp(14); verticalSpacing = dp(14)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(dp(28), dp(4), dp(28), dp(24)); clipToPadding = false
            isFastScrollEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            selector = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(0x33FFFFFF) }
            setDrawSelectorOnTop(true)
            adapter = object : BaseAdapter() {
                override fun getCount() = shown.size
                override fun getItem(p: Int) = shown[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                    val (ri, lbl) = shown[p]
                    val pkg = ri.activityInfo.packageName
                    val isFav = favs().contains(pkg)
                    return LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        layoutParams = AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, dp(136))
                        background = tile()
                        val iconBox = FrameLayout(this@MainActivity)
                        iconBox.addView(IconStyle.view(this@MainActivity, pkg, 84, ri.loadIcon(pm)),
                            FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER))
                        if (isFav) iconBox.addView(label(this@MainActivity, "★", 16f).apply { setTextColor(0xFFFFC857.toInt()) },
                            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
                        addView(iconBox, LinearLayout.LayoutParams(dp(100), dp(88)))
                        addView(label(this@MainActivity, lbl, 13f).apply {
                            maxLines = 1; gravity = Gravity.CENTER
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            setTextColor(0xFFD5E0E6.toInt())
                        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); leftMargin = dp(6); rightMargin = dp(6) })
                    }
                }
            }
            setOnItemClickListener { _, _, p, _ ->
                val pkg = shown[p].first.activityInfo.packageName
                if (pickSlot >= 0) {
                    prefs.edit().putString("dock_$pickSlot", pkg).apply(); pickSlot = -1
                    rebuild()
                } else { closeDrawer(); launch(pkg) }
            }
            setOnItemLongClickListener { _, _, p, _ -> iconMenu(shown[p].first); true }
        }
        drawerGrid = grid

        val empty = label(this, "", 17f).apply {
            setTextColor(0xFF8097A0.toInt()); gravity = Gravity.CENTER; visibility = View.GONE
        }
        val count = label(this, "${items.size}", 16f).apply { setTextColor(0xFF8097A0.toInt()) }

        fun applyFilter() {
            val q = drawerQuery.trim().lowercase()
            val base: List<Pair<ResolveInfo, String>> = when (drawerTab) {
                1 -> recents().mapNotNull { p -> items.firstOrNull { it.first.activityInfo.packageName == p } }
                2 -> { val f = favs(); items.filter { f.contains(it.first.activityInfo.packageName) } }
                else -> items
            }
            shown = if (q.isEmpty()) base else base.filter { it.second.lowercase().contains(q) }
            (grid.adapter as? BaseAdapter)?.notifyDataSetChanged()
            empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            empty.text = when {
                q.isNotEmpty() -> "Ninguna app coincide con «${drawerQuery.trim()}»"
                drawerTab == 1 -> "Todavía no has abierto apps"
                drawerTab == 2 -> "Mantén pulsada una app para añadirla a favoritas"
                else -> ""
            }
            count.text = "${shown.size}"
        }
        drawerRefresh = { applyFilter() }

        // ---- Fila 1: título, contador y cerrar ----
        val row1 = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(28), dp(14), dp(28), dp(4))
            addView(label(this@MainActivity, if (pickSlot >= 0) "Elige app para el dock" else "Aplicaciones", 24f, true))
            addView(count, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12); topMargin = dp(4) })
            addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(label(this@MainActivity, "✕", 20f).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x1AFFFFFF) }
                setOnClickListener { closeDrawer() }
            }, LinearLayout.LayoutParams(dp(46), dp(46)))
        }

        // ---- Fila 2: pestañas, buscador y estilo de íconos ----
        val row2 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(28), dp(8), dp(28), dp(12)) }
        val chips = ArrayList<TextView>()
        fun restyle() { chips.forEachIndexed { i, c -> styleChip(c, i == drawerTab) } }
        listOf("Todas", "Recientes", "Favoritas").forEachIndexed { i, n ->
            val c = chip(n, i == 0) { drawerTab = i; restyle(); applyFilter(); grid.setSelection(0) }
            chips.add(c)
            row2.addView(c, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        val search = EditText(this).apply {
            hint = "Buscar app"; setHintTextColor(0x88FFFFFF.toInt()); setTextColor(Color.WHITE); textSize = 16f
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_DONE
            background = glassTile(22)
            setPadding(dp(18), 0, dp(18), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { drawerQuery = s?.toString() ?: ""; applyFilter() }
            })
        }
        row2.addView(search, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(4); rightMargin = dp(12) })
        if (pickSlot >= 0) row2.addView(chip("Quitar ícono", false) { val s = pickSlot; closeDrawer(); clearSlot(s) },
            LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        val styleBtn = chip("Íconos: ${IconStyle.modeName(this)}", false) { IconStyle.cycle(this); refreshDrawerIcons() }
        drawerStyleBtn = styleBtn
        row2.addView(styleBtn, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        val tintBtn = chip("Tinte: ${IconStyle.tintName(this)}", false) { tintMenu() }
        drawerTintBtn = tintBtn
        row2.addView(tintBtn, LinearLayout.LayoutParams(-2, -2))

        val body = FrameLayout(this).apply {
            addView(grid, FrameLayout.LayoutParams(-1, -1))
            addView(empty, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        val d = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF0F1D24.toInt(), 0xFF050608.toInt()))
            isFocusable = true; isFocusableInTouchMode = true     // para que el buscador no abra el teclado solo
            addView(row1); addView(row2); addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            alpha = 0f
        }
        drawer = d
        root.addView(d, FrameLayout.LayoutParams(-1, -1))
        d.requestFocus()
        d.animate().alpha(1f).setDuration(180).start()
    }

    private fun closeDrawer() {
        try { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0) } catch (e: Exception) { }
        drawer?.let { root.removeView(it) }; drawer = null; drawerGrid = null; drawerStyleBtn = null; drawerTintBtn = null
        drawerRefresh = null; pickSlot = -1
        if (needsRebuild) { needsRebuild = false; rebuild() }
    }

    /** Vuelve a pintar los íconos del cajón y marca que la pantalla de inicio se reconstruya al cerrarlo. */
    private fun refreshDrawerIcons() {
        needsRebuild = true
        (drawerGrid?.adapter as? BaseAdapter)?.notifyDataSetChanged()
        drawerStyleBtn?.text = "Íconos: ${IconStyle.modeName(this)}"
        drawerTintBtn?.text = "Tinte: ${IconStyle.tintName(this)}"
    }

    // ---- Íconos personalizados (mantener pulsada una app en el cajón) ----
    private fun iconMenu(ri: ResolveInfo) {
        val pkg = ri.activityInfo.packageName
        val name = ri.loadLabel(packageManager).toString()
        val hasCustom = IconStyle.hasCustom(this, pkg)
        val hasOwnStyle = IconStyle.appMode(this, pkg) >= 0
        val items = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        items.add(if (favs().contains(pkg)) "Quitar de favoritas" else "Añadir a favoritas"); acts.add { toggleFav(pkg) }
        items.add("Estilo de este ícono…"); acts.add { styleMenu(pkg, name) }
        items.add(if (hasCustom) "Elegir otra imagen…" else "Elegir imagen…"); acts.add { pickIcon(pkg) }
        if (hasCustom || hasOwnStyle) {
            items.add("Restaurar ícono")
            acts.add { IconStyle.customFile(this, pkg).delete(); IconStyle.setAppMode(this, pkg, -1); refreshDrawerIcons() }
        }
        AlertDialog.Builder(this).setTitle("Ícono de $name").setItems(items.toTypedArray()) { _, which -> acts[which]() }.show()
    }

    private fun styleMenu(pkg: String, name: String) {
        val opts = arrayOf("Como todos (${IconStyle.modeName(this)})") + IconStyle.styleNames
        AlertDialog.Builder(this).setTitle("Estilo de $name")
            .setSingleChoiceItems(opts, IconStyle.appMode(this, pkg) + 1) { d, w ->
                IconStyle.setAppMode(this, pkg, w - 1)
                IconStyle.customFile(this, pkg).delete()   // elegir un estilo reemplaza la imagen propia
                d.dismiss(); refreshDrawerIcons()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun tintMenu() {
        AlertDialog.Builder(this).setTitle("Color del estilo Teñido")
            .setSingleChoiceItems(IconStyle.tintNames, IconStyle.tintIndex(this)) { d, w ->
                IconStyle.setTint(this, w); d.dismiss(); refreshDrawerIcons()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun pickIcon(pkg: String) {
        iconPickPkg = pkg
        try {
            val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(Intent.createChooser(i, "Elegir imagen"), REQ_ICON)
        } catch (e: Exception) {
            Toast.makeText(this, "No hay una app para elegir imágenes", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val pkg = iconPickPkg
        val uri = data?.data
        if (req == REQ_ICON && res == RESULT_OK && pkg != null && uri != null) saveIcon(pkg, uri)
        iconPickPkg = null
    }

    private fun saveIcon(pkg: String, uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) { Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show(); return }
            val side = minOf(bmp.width, bmp.height)
            val crop = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(crop, 256, 256, true)
            FileOutputStream(IconStyle.customFile(this, pkg)).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            refreshDrawerIcons()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo guardar el ícono", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); closeDrawer() }  // boton Home cierra el cajon
    override fun onBackPressed() { if (drawer != null) closeDrawer() }
}
