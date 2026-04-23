package com.example.deckbridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.deckbridge.MainActivity
import com.example.deckbridge.R
import com.example.deckbridge.logging.DeckBridgeLog

/**
 * Foreground service that keeps the DeckBridge process alive when the app is minimized.
 * Maintains active LAN connections, Mac Bridge server, and allows the Accessibility Service
 * to keep forwarding keyboard events to the PC.
 *
 * Lifecycle:
 * - Started from [MainActivity.onStop] when the user leaves the app.
 * - Stopped from [MainActivity.onResume] when the user returns.
 */
class DeckBridgeService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        // Always satisfy Android's 5-second startForeground() deadline immediately.
        startForeground(NOTIFICATION_ID, buildNotification())
        // If stop() was called before our onCreate() ran (fast background→foreground cycle),
        // self-terminate now that the OS deadline is satisfied.
        if (stopRequested) {
            stopRequested = false
            DeckBridgeLog.state("DeckBridgeService: immediate self-stop (stop requested before onCreate)")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-promote to foreground in case the service was demoted or restarted by the system.
        startForeground(NOTIFICATION_ID, buildNotification())
        if (stopRequested) {
            stopRequested = false
            DeckBridgeLog.state("DeckBridgeService: immediate self-stop (stop requested before onStartCommand)")
            stopSelf()
            return START_NOT_STICKY
        }
        DeckBridgeLog.state("DeckBridgeService: started (background mode)")
        return START_STICKY
    }

    override fun onDestroy() {
        DeckBridgeLog.state("DeckBridgeService: stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.service_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.service_channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "deckbridge_bg"
        private const val NOTIFICATION_ID = 1001

        /**
         * Set to true by [stop] when called before the service's [onCreate] has run.
         * The service checks this flag in [onCreate]/[onStartCommand] and self-terminates
         * immediately after satisfying Android's startForeground() deadline.
         * This prevents the ForegroundServiceDidNotStartInTimeException race condition
         * on devices with aggressive battery optimization (e.g. Samsung Exynos).
         */
        @Volatile var stopRequested = false

        fun start(context: Context) {
            stopRequested = false
            val intent = Intent(context, DeckBridgeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            stopRequested = true
            context.stopService(Intent(context, DeckBridgeService::class.java))
        }
    }
}
