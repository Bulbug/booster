package com.gameboostx.app.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gameboostx.app.GameBoostApplication
import com.gameboostx.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Purely a "keep the process alive and show what's happening" shell — all real session logic
 * lives in GamingSessionManager (a single Application-scoped instance both this service and the
 * UI observe). This service starts itself when a session begins and stops itself when it ends,
 * so it never outlives the thing it's reporting on (spec §40 — no dangling background work).
 */
class GamingSessionService : Service() {

    private var scopeJob: Job? = null
    private val scope by lazy { CoroutineScope(Dispatchers.Default + (scopeJob ?: Job().also { scopeJob = it })) }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as GameBoostApplication
        startForegroundCompat(buildNotification(app, active = true))

        app.gamingSessionManager.state
            .onEach { state ->
                if (!state.active) {
                    stopSelf()
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(app, active = true))
                }
            }
            .launchIn(scope)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scopeJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(app: GameBoostApplication, active: Boolean): Notification {
        val state = app.gamingSessionManager.state.value
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = state.displayName?.let { "Gaming session: $it" } ?: "GameBoost X session"
        val temp = state.latestSnapshot?.thermal?.batteryTempCelsius?.let { "%.0f°C".format(it) } ?: "?"
        val text = "CPU/RAM/temperature monitoring active · $temp"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(active)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gaming Session", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows while GameBoost X is monitoring an active gaming session."
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "gaming_session"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startService(Intent(context, GamingSessionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GamingSessionService::class.java))
        }
    }
}
