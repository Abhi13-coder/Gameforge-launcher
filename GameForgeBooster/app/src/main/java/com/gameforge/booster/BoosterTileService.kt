package com.gameforge.booster

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile ("Game Mode") so the HUD can be flicked on/off from
 * the notification shade without opening the app — mirrors what OnePlus /
 * Samsung game launchers put in their quick toggles.
 */
class BoosterTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                val pending = android.app.PendingIntent.getActivity(
                    this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val active = qsTile?.state == Tile.STATE_ACTIVE
        val serviceIntent = Intent(this, BoosterOverlayService::class.java)
        if (active) {
            serviceIntent.action = Constants.ACTION_STOP_OVERLAY
            startService(serviceIntent)
        } else {
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
        }
        qsTile?.state = if (active) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }

    private fun refresh() {
        qsTile?.label = getString(R.string.tile_game_mode)
        qsTile?.updateTile()
    }
}
