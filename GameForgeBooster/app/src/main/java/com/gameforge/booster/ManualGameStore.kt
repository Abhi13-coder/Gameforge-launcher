package com.gameforge.booster

import android.content.Context

/**
 * Persists package names the user manually confirmed are games — for the
 * ones GameForge's CATEGORY_GAME/legacy-flag heuristic misses. Several
 * genuinely popular titles (older Unity exports, some emulators, a lot of
 * web-wrapper games) never set either signal, so this is not an edge case.
 */
object ManualGameStore {
    private const val KEY = "manual_game_packages"

    fun getAll(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()

    fun add(context: Context, packageName: String) {
        val current = getAll(context).toMutableSet()
        current.add(packageName)
        prefs(context).edit().putStringSet(KEY, current).apply()
    }

    fun remove(context: Context, packageName: String) {
        val current = getAll(context).toMutableSet()
        current.remove(packageName)
        prefs(context).edit().putStringSet(KEY, current).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE)
}
