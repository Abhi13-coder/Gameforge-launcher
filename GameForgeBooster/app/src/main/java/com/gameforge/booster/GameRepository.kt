package com.gameforge.booster

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Finds installed, launchable apps that look like games.
 *
 * Primary signal: ApplicationInfo.category == CATEGORY_GAME, which is the
 * official Play Store classification flag (API 26+) most game APKs set.
 * Fallback signal: the legacy FLAG_IS_GAME bit some older/MIUI-built APKs
 * still carry. Anything else is left for the user's normal app drawer —
 * GameForge intentionally does not guess beyond these two documented
 * signals, to avoid mis-tagging random apps as "games".
 */
object GameRepository {

    /** Every launchable app, with a flag for whether our heuristic already thinks it's a game. */
    private fun resolveLaunchable(context: Context): List<Pair<ApplicationInfo, Boolean>> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val seen = LinkedHashSet<String>()
        val out = mutableListOf<Pair<ApplicationInfo, Boolean>>()

        for (ri in resolved) {
            val pkg = ri.activityInfo.packageName
            if (!seen.add(pkg)) continue
            val appInfo: ApplicationInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            }
            val isGameByCategory = appInfo.category == ApplicationInfo.CATEGORY_GAME
            @Suppress("DEPRECATION")
            val isGameByLegacyFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            out.add(appInfo to (isGameByCategory || isGameByLegacyFlag))
        }
        return out
    }

    /**
     * Auto-detected games (CATEGORY_GAME / legacy FLAG_IS_GAME) plus whatever
     * the user manually confirmed via "Add game" — a lot of real titles
     * (older Unity/Cocos exports, web-wrapper games, some emulators) set
     * neither official signal, so the manual list isn't a rare fallback.
     */
    fun findGames(context: Context): List<GameInfo> {
        val pm = context.packageManager
        val manual = ManualGameStore.getAll(context)
        val games = mutableListOf<GameInfo>()

        for ((appInfo, isAutoGame) in resolveLaunchable(context)) {
            if (isAutoGame || appInfo.packageName in manual) {
                games.add(
                    GameInfo(
                        packageName = appInfo.packageName,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
                    )
                )
            }
        }
        return games.sortedBy { it.label.lowercase() }
    }

    /** Full launchable-app list for the "Add game" picker, already-added apps excluded. */
    fun findAddableApps(context: Context): List<GameInfo> {
        val pm = context.packageManager
        val manual = ManualGameStore.getAll(context)
        return resolveLaunchable(context)
            .filter { (appInfo, isAutoGame) -> !isAutoGame && appInfo.packageName !in manual }
            .map { (appInfo, _) ->
                GameInfo(
                    packageName = appInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launchIntentFor(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
}
