package com.gameforge.booster

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Foreground service that owns two overlay windows while a gaming session
 * is active:
 *  1. The draggable HUD (fps / ram / thermal readout + quick toggles)
 *  2. The full-screen click-through HDR/vivid tint layer (optional)
 *
 * Starting this requires SYSTEM_ALERT_WINDOW to already be granted —
 * MainActivity/PermissionActivity gate that before ever starting us.
 */
class BoosterOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var hudView: View? = null
    private var hdrView: HdrSimulationView? = null
    private var fpsCounter: FpsCounter? = null
    private var thermalMonitor: ThermalMonitor? = null
    private var hdrEnabled = false
    private var dndEnabled = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_STOP_OVERLAY -> {
                stopSelf()
                return START_NOT_STICKY
            }
            Constants.ACTION_TOGGLE_HDR -> {
                toggleHdr()
                return START_STICKY
            }
            Constants.ACTION_TOGGLE_DND -> {
                toggleDnd()
                return START_STICKY
            }
        }

        startForeground(Constants.NOTIF_ID_BOOSTER, buildNotification())
        showHud()
        startFpsCounter()
        startThermal()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fpsCounter?.stop()
        thermalMonitor?.stop()
        removeHud()
        removeHdr()
        if (dndEnabled) PerformanceModeManager.setDoNotDisturb(this, false)
    }

    // --- HUD window -------------------------------------------------

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showHud() {
        if (hudView != null) return
        val inflater = android.view.LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_hud, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                // A visible window with this flag keeps the screen on system-wide for as
                // long as it's showing — real effect, not a simulated "stay awake" claim.
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }

        makeDraggable(view, params)

        view.findViewById<ImageButton>(R.id.hudClose).setOnClickListener { stopSelf() }
        view.findViewById<ImageButton>(R.id.hudHdrToggle).setOnClickListener { toggleHdr() }
        view.findViewById<ImageButton>(R.id.hudDndToggle).setOnClickListener { toggleDnd() }

        windowManager.addView(view, params)
        hudView = view
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun removeHud() {
        hudView?.let { runCatching { windowManager.removeView(it) } }
        hudView = null
    }

    // --- HDR / vivid tint window --------------------------------------

    private fun toggleHdr() {
        hdrEnabled = !hdrEnabled
        if (hdrEnabled) showHdr() else removeHdr()
    }

    private fun showHdr() {
        if (hdrView != null) return
        val view = HdrSimulationView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(view, params)
        hdrView = view
    }

    private fun removeHdr() {
        hdrView?.let { runCatching { windowManager.removeView(it) } }
        hdrView = null
    }

    // --- DND -----------------------------------------------------------

    private fun toggleDnd() {
        if (!PerformanceModeManager.hasNotificationPolicyAccess(this)) return
        dndEnabled = !dndEnabled
        PerformanceModeManager.setDoNotDisturb(this, dndEnabled)
    }

    // --- Live readouts ---------------------------------------------------

    private fun startFpsCounter() {
        fpsCounter = FpsCounter { fps ->
            hudView?.findViewById<TextView>(R.id.hudFps)?.text = "$fps FPS (overlay)"
        }.also { it.start() }
    }

    private fun startThermal() {
        updateRamReadout()
        thermalMonitor = ThermalMonitor(this).also {
            it.start(object : ThermalMonitor.Listener {
                override fun onThermalStatus(status: Int, label: String) {
                    hudView?.findViewById<TextView>(R.id.hudThermal)?.text = "Thermal: $label"
                    updateRamReadout()
                }
            })
        }
    }

    private fun updateRamReadout() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val freeMb = info.availMem / (1024 * 1024)
        hudView?.findViewById<TextView>(R.id.hudRam)?.text = "RAM free: ${freeMb} MB"
    }

    // --- Notification ----------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_BOOSTER, "Game HUD", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows while the performance HUD overlay is active" }
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, BoosterOverlayService::class.java).apply {
            action = Constants.ACTION_STOP_OVERLAY
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Constants.CHANNEL_BOOSTER)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("GameForge HUD active")
            .setContentText("Tap to manage, or stop from here")
            .addAction(0, "Stop", stopPending)
            .setOngoing(true)
            .build()
    }
}
