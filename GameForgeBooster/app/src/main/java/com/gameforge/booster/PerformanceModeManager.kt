package com.gameforge.booster

import android.app.NotificationManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Every method here does something real within what a non-root, non-system
 * app is actually allowed to do on stock Android 10+. None of this is a
 * substitute for root-level governor/CPU control — see README for the
 * honesty notes on each toggle.
 */
object PerformanceModeManager {

    // --- Do Not Disturb -----------------------------------------------

    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun requestNotificationPolicyAccess(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun setDoNotDisturb(context: Context, enabled: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        nm.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    // --- Usage access (needed for the background-app list + cache sizes) --

    fun hasUsageAccess(context: Context): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 60, now)
        return stats != null && stats.isNotEmpty()
    }

    fun requestUsageAccess(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // --- Background app trimming ---------------------------------------
    // ActivityManager#killBackgroundProcesses is a *hint*, not a guarantee:
    // Android reserves the right to ignore it for apps it thinks you still
    // want warm (e.g. recently used, foreground-service holders). MIUI's
    // own "Clear memory" button hits the same ceiling for 3rd-party apps.
    fun trimBackgroundApps(context: Context, excludePackages: Set<String>) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000L * 60 * 60 * 6, now)
            ?: return
        val myPkg = context.packageName
        stats.asSequence()
            .map { it.packageName }
            .filter { it != myPkg && it !in excludePackages }
            .distinct()
            .forEach { pkg ->
                runCatching { am.killBackgroundProcesses(pkg) }
            }
    }

    // --- Cache visibility -------------------------------------------------
    // Android forbids 3rd-party apps from clearing *other* apps' cache
    // directly (no public API since Android 6) — that's a root-only or
    // system-app capability. What GameForge can legitimately do: read
    // sizes via StorageStatsManager (needs PACKAGE_USAGE_STATS) and jump
    // you straight to that app's "Storage > Clear cache" screen.
    fun openAppStorageSettings(context: Context, packageName: String) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun cacheBytesFor(context: Context, packageName: String): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return -1
        return runCatching {
            val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val uuid = context.packageManager.getApplicationInfo(packageName, 0).storageUuid
            val stats = ssm.queryStatsForPackage(uuid, packageName, Process.myUserHandle())
            stats.cacheBytes
        }.getOrDefault(-1L)
    }

    // --- Android's own Game Mode API (deliberately NOT wired up) ---------
    // android.app.GameManager (API 31+) lets a privileged SYSTEM app query
    // or set another app's game mode (performance/battery/standard). The
    // permission that allows *setting* it — MANAGE_GAME_MODE — is a
    // signature|privileged permission: Google Play apps cannot hold it, no
    // matter what's declared in the manifest. A non-system app can only
    // call getGameMode() meaningfully for ITSELF while it's the one running
    // (a game checking its own mode, not a booster checking someone else's).
    // GameForge is not a game, so there is nothing real this hook could do
    // here — it's left out rather than shipped as a button that quietly
    // does nothing.
}
