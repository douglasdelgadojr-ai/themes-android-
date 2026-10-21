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
import android.view.*
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
    private var iconPickPkg: String? = null
    private var needsRebuild = false
    private val REQ_ICON = 77
    private val prefs by lazy { getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        root = FrameLayout(this)
        setContentView(root)
        rebuild()
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

    override fun onResume() { super.onResume(); checkHome() }

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

    // ---- ThemeHost ----
    override fun launch(pkg: String?) {
        val i = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (i == null) {
            Toast.makeText(this, if (pkg == NAV_PKG) "Waze no está instalado" else "App no instalada", Toast.LENGTH_SHORT).show()
            return
        }
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

    override fun nowPlaying(): NowPlaying? = try {
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val list = msm.getActiveSessions(ComponentName(this, MediaListener::class.java))
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (c == null) {
            // Ninguna app publica sesión de música: al menos avisa si el sistema detecta audio sonando
            if (am.isMusicActive) NowPlaying("Reproduciendo", "", true, null) else NowPlaying("Sin reproducción", "", false, null)
        } else {
            val m = c.metadata
            NowPlaying(
                m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
                m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
                c.playbackState?.state == PlaybackState.STATE_PLAYING || am.isMusicActive,
                m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
                c.packageName
            )
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

    override fun openDrawer() {
        if (drawer != null) return
        val pm = packageManager
        val apps: List<ResolveInfo> = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val grid = GridView(this).apply {
            numColumns = 6; verticalSpacing = dp(10); setPadding(dp(12), dp(8), dp(12), dp(8))
            adapter = object : BaseAdapter() {
                override fun getCount() = apps.size
                override fun getItem(p: Int) = apps[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                    val ri = apps[p]
                    val pkg = ri.activityInfo.packageName
                    return LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        addView(IconStyle.view(this@MainActivity, pkg, 64, ri.loadIcon(pm)),
                            LinearLayout.LayoutParams(dp(64), dp(64)))
                        addView(label(context, ri.loadLabel(pm).toString(), 13f).apply {
                            maxLines = 1; gravity = Gravity.CENTER
                        })
                    }
                }
            }
            setOnItemClickListener { _, _, p, _ ->
                val pkg = apps[p].activityInfo.packageName
                if (pickSlot >= 0) {
                    prefs.edit().putString("dock_$pickSlot", pkg).apply(); pickSlot = -1
                    rebuild()
                } else { closeDrawer(); launch(pkg) }
            }
            setOnItemLongClickListener { _, _, p, _ -> iconMenu(apps[p]); true }
        }
        drawerGrid = grid
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(label(context, if (pickSlot >= 0) "Elige app para el dock" else "Apps  ·  mantén pulsada una app para cambiar su ícono", 18f, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (pickSlot >= 0) addView(label(context, "Quitar icono", 18f).apply {
                setPadding(0, 0, dp(24), 0)
                setOnClickListener { val s = pickSlot; closeDrawer(); clearSlot(s) }
            })
            val styleBtn = label(context, "Íconos: ${IconStyle.modeName(this@MainActivity)}", 18f).apply {
                setPadding(0, 0, dp(24), 0)
                setOnClickListener { IconStyle.cycle(this@MainActivity); refreshDrawerIcons() }
            }
            drawerStyleBtn = styleBtn
            addView(styleBtn)
            addView(label(context, "Cerrar ✕", 18f).apply { setOnClickListener { closeDrawer() } })
        }
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xEE0B1030.toInt())
            addView(bar); addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
    }

    private fun closeDrawer() {
        drawer?.let { root.removeView(it) }; drawer = null; drawerGrid = null; drawerStyleBtn = null; pickSlot = -1
        if (needsRebuild) { needsRebuild = false; rebuild() }
    }

    /** Vuelve a pintar los íconos del cajón y marca que la pantalla de inicio se reconstruya al cerrarlo. */
    private fun refreshDrawerIcons() {
        needsRebuild = true
        (drawerGrid?.adapter as? BaseAdapter)?.notifyDataSetChanged()
        drawerStyleBtn?.text = "Íconos: ${IconStyle.modeName(this)}"
    }

    // ---- Íconos personalizados (mantener pulsada una app en el cajón) ----
    private fun iconMenu(ri: ResolveInfo) {
        val pkg = ri.activityInfo.packageName
        val name = ri.loadLabel(packageManager).toString()
        val items = if (IconStyle.hasCustom(this, pkg)) arrayOf("Elegir otra imagen…", "Restaurar ícono original")
        else arrayOf("Elegir imagen…")
        AlertDialog.Builder(this).setTitle("Ícono de $name").setItems(items) { _, which ->
            if (which == 0) pickIcon(pkg)
            else { IconStyle.customFile(this, pkg).delete(); refreshDrawerIcons() }
        }.show()
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
