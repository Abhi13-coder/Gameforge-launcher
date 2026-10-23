package com.gameforge.booster

object Constants {
    const val CHANNEL_BOOSTER = "gameforge_booster"
    const val CHANNEL_CAPTURE = "gameforge_capture"

    const val NOTIF_ID_BOOSTER = 1001
    const val NOTIF_ID_CAPTURE = 1002

    const val ACTION_STOP_OVERLAY = "com.gameforge.booster.action.STOP_OVERLAY"
    const val ACTION_TOGGLE_HDR = "com.gameforge.booster.action.TOGGLE_HDR"
    const val ACTION_TOGGLE_DND = "com.gameforge.booster.action.TOGGLE_DND"

    const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"
    const val EXTRA_MODE = "extra_mode" // "record" | "screenshot"

    const val PREFS = "gameforge_prefs"
    const val PREF_HDR_ENABLED = "hdr_enabled"
    const val PREF_LAST_GAME = "last_game_pkg"
}
