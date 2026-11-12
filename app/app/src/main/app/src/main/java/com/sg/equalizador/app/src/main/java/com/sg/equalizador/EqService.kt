package com.sg.equalizador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder

class EqService : Service() {
    companion object {
        const val ACTION_START = "sg.START"
        const val ACTION_STOP = "sg.STOP"
        const val ACTION_UPDATE = "sg.UPDATE"
        const val ACTION_OPEN = "sg.OPEN"
        const val ACTION_CLOSE = "sg.CLOSE"
        const val EXTRA_SESSION = "session"
    }

    private val effects = HashMap<Int, Equalizer>()
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("eq", MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        if (intent == null && !prefs.getBoolean("enabled", false)) {
            stopSelf(); return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                releaseAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN -> attach(intent.getIntExtra(EXTRA_SESSION, -1))
            ACTION_CLOSE -> detach(intent.getIntExtra(EXTRA_SESSION, -1))
            ACTION_UPDATE -> effects.values.forEach { apply(it) }
            else -> attach(0)
        }
        return START_STICKY
    }

    private fun attach(session: Int) {
        if (session < 0) return
        effects[session]?.let { apply(it); return }
        try {
            val eq = Equalizer(0, session)
            eq.enabled = true
            effects[session] = eq
            apply(eq)
            prefs.edit().putString("status", "Activo: el ecualizador está funcionando.").apply()
        } catch (e: Exception) {
            if (session == 0) {
                prefs.edit().putString(
                    "status",
                    "Tu teléfono no permite el efecto global. Solo se aplicará a apps que lo soporten."
                ).apply()
            }
        }
    }

    private fun detach(session: Int) {
        if (session <= 0) return
        effects.remove(session)?.release()
    }

    private fun apply(eq: Equalizer) {
        try {
            val n = eq.numberOfBands.toInt()
            val r = eq.bandLevelRange
            for (i in 0 until n) {
                val l = prefs.getInt("band_$i", 0).coerceIn(r[0].toInt(), r[1].toInt())
                eq.setBandLevel(i.toShort(), l.toShort())
            }
        } catch (_: Exception) {}
    }

    private fun releaseAll() {
        effects.values.forEach { try { it.release() } catch (_: Exception) {} }
        effects.clear()
    }

    private fun goForeground() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel("eq", "Ecualizador", NotificationManager.IMPORTANCE_LOW)
            )
            val pi = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(this, "eq")
                .setContentTitle("Equalizador de SG")
                .setContentText("Ecualizador activo")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(1, n)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        releaseAll()
        super.onDestroy()
    }
}
