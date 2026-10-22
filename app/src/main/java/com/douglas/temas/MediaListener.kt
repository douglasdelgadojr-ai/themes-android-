package com.douglas.temas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService

/**
 * Sirve para que Android nos deje leer la sesión de música activa, y además trae «Temas» al frente
 * cuando la radio enciende o despierta (el sistema reconecta este servicio solo en cada arranque).
 */
class MediaListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastGo = 0L
    private var registered = false

    private val wake = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { goHome(2500) }
    }

    /** Se puede apagar con la preferencia "autostart" = false. */
    private fun enabled() = getSharedPreferences("launcher", Context.MODE_PRIVATE).getBoolean("autostart", true)

    private fun open() {
        try {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (e: Exception) { }
    }

    private fun goHome(delayMs: Long) {
        if (!enabled()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastGo < 15000) return          // SCREEN_ON y USER_PRESENT llegan juntos: solo una vez
        lastGo = now
        handler.postDelayed({ open() }, delayMs)
    }

    override fun onCreate() {
        super.onCreate()
        if (!registered) {
            registerReceiver(wake, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            })
            registered = true
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Solo en los primeros 4 minutos desde que enciende la radio (no cuando concedes el permiso a mitad de uso)
        if (enabled() && SystemClock.elapsedRealtime() < 4 * 60 * 1000L) {
            lastGo = SystemClock.elapsedRealtime()
            handler.postDelayed({ open() }, 2000)
            handler.postDelayed({ open() }, 8000)
        }
    }

    override fun onDestroy() {
        if (registered) { try { unregisterReceiver(wake) } catch (e: Exception) { }; registered = false }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
