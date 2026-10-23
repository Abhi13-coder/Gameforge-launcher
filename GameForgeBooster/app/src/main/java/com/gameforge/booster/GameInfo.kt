package com.gameforge.booster

import android.graphics.drawable.Drawable

/**
 * A launchable app GameForge has classified as a game, either because the
 * developer tagged it android:appCategory="game" (Android 8+) or because it
 * sits in a known game-store package namespace. Heuristic, not perfect —
 * users can still launch anything from their normal app drawer.
 */
data class GameInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)
