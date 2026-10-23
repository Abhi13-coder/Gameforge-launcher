# Keep service/tile classes referenced only from the manifest.
-keep class com.gameforge.booster.** extends android.app.Service { *; }
-keep class com.gameforge.booster.** extends android.service.quicksettings.TileService { *; }
