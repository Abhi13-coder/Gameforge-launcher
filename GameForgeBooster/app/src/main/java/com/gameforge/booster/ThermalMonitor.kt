package com.gameforge.booster

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.util.concurrent.Executor

/**
 * Wraps PowerManager's thermal-status API (added in API 29, so it covers
 * your Redmi 9A's Android 10 floor exactly). This reads the thermal state
 * the system itself already computes from its own sensors — it is real
 * data, not a simulation — but GameForge cannot change throttling policy;
 * only the OEM's thermal daemon decides that. What GameForge *can* do is
 * warn you early and suggest a cooldown break.
 */
class ThermalMonitor(private val context: Context) {

    interface Listener {
        fun onThermalStatus(status: Int, label: String)
    }

    private var powerManager: PowerManager? = null
    private var listener: PowerManager.OnThermalStatusChangedListener? = null
    private val mainExecutor: Executor = context.mainExecutor

    fun start(callback: Listener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        powerManager = pm

        val l = PowerManager.OnThermalStatusChangedListener { status ->
            callback.onThermalStatus(status, labelFor(status))
        }
        listener = l
        pm.addThermalStatusListener(mainExecutor, l)
        // Emit current status immediately so the UI isn't blank until the next change.
        callback.onThermalStatus(pm.currentThermalStatus, labelFor(pm.currentThermalStatus))
    }

    fun stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        listener?.let { powerManager?.removeThermalStatusListener(it) }
        listener = null
    }

    private fun labelFor(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Nominal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
        PowerManager.THERMAL_STATUS_MODERATE -> "Hot — consider a break"
        PowerManager.THERMAL_STATUS_SEVERE -> "Throttling likely"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical — cool down now"
        PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> "Emergency"
        else -> "Unknown"
    }

    companion object {
        fun isModerateOrWorse(status: Int) = status >= PowerManager.THERMAL_STATUS_MODERATE
    }
}
