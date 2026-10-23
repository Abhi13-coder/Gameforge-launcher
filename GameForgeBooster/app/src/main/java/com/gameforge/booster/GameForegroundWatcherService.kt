package com.gameforge.booster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Optional background watcher: polls UsageStatsManager's event log (not
 * AccessibilityService — Google actively rejects apps that use
 * accessibility for non-accessibility purposes, so real booster/app-lock
 * apps on Play Store use this polling approach instead) to learn which app
 * is currently in the foreground, and auto-arms the HUD the moment a known
 * game opens — no manual "Boost & Launch" tap needed. Stops the HUD again
 * once you leave the game.
 *
 * Requires PACKAGE_USAGE_STATS (Usage Access), same permission the
 * Cache/RAM tools already ask for.
 */
class GameForegroundWatcherService : Service() {

    companion object {
        const val NOTIF_ID = 1003
        const val CHANNEL = "gameforge_watcher"
        private const val POLL_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPkg: String? = null
    private var hudArmedFor: String? = null
    private var knownGames: Set<String> = emptySet()

    private val poll = object : Runnable {
        override fun run() {
            checkForeground()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        knownGames = GameRepository.findGames(this).map { it.packageName }.toSet()
        startForeground(NOTIF_ID, buildNotification())
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(poll)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForeground() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10_000, now)
        var latestPkg: String? = null
        var latestTime = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            else
                @Suppress("DEPRECATION") event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (isForegroundEvent && event.timeStamp >= latestTime) {
                latestTime = event.timeStamp
                latestPkg = event.packageName
            }
        }

        val pkg = latestPkg ?: return
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg

        when {
            pkg in knownGames && hudArmedFor != pkg -> {
                hudArmedFor = pkg
                ContextCompat.startForegroundService(this, Intent(this, BoosterOverlayService::class.java))
            }
            pkg !in knownGames && pkg != packageName && hudArmedFor != null -> {
                hudArmedFor = null
                startService(
                    Intent(this, BoosterOverlayService::class.java).apply {
                        action = Constants.ACTION_STOP_OVERLAY
                    }
                )
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Game auto-detect", NotificationManager.IMPORTANCE_MIN)
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("GameForge is watching for games")
            .setContentText("The HUD will auto-arm when a known game opens")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
}
