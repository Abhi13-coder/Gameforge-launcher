package com.gameforge.booster

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery Saver silently caps CPU/GPU clocks and background work on most
 * OEM skins — a real, measurable performance hit a user might not connect
 * to "why does my game feel laggy". GameForge can't override it (no 3rd
 * party API to toggle system battery saver), but it CAN detect it and
 * deep-link straight to the toggle, which is what real booster apps do.
 */
object BatteryAwarenessManager {

    fun isPowerSaveMode(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }

    fun openBatterySaverSettings(context: Context) {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.ACTION_BATTERY_SAVER_SETTINGS else Settings.ACTION_SETTINGS
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Smooth Display / refresh-rate toggle lives in system Display settings — GameForge
     *  cannot force a higher refresh rate on a device that supports one (no public API
     *  for 3rd-party apps to change the system's chosen mode), so it links straight there
     *  instead of pretending to flip it itself. */
    fun openDisplaySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Asks the user to exempt GameForge itself from Doze/App Standby, so the HUD's
     *  foreground service and the foreground-game watcher aren't deferred or killed
     *  in the background. Standard, Play-Store-accepted system dialog — not silent. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
