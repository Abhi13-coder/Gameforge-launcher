package com.gameforge.booster

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.gameforge.booster.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingCaptureMode: String? = null

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val mode = pendingCaptureMode ?: return@registerForActivityResult
            pendingCaptureMode = null
            if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult

            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(Constants.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(Constants.EXTRA_RESULT_DATA, result.data)
                putExtra(Constants.EXTRA_MODE, mode)
            }
            ContextCompat.startForegroundService(this, intent)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.gameList.layoutManager = LinearLayoutManager(this)
        loadGames()
        updateStatusLine()
        updateAutoDetectSwitch()

        binding.btnOverlay.setOnClickListener { onOverlayClicked() }
        binding.btnRecord.setOnClickListener { onCaptureClicked("record") }
        binding.btnShot.setOnClickListener { onCaptureClicked("screenshot") }
        binding.btnCache.setOnClickListener { onCacheClicked() }
        binding.btnAddGame.setOnClickListener { startActivity(Intent(this, AddGameActivity::class.java)) }
        binding.btnDisplaySettings.setOnClickListener { BatteryAwarenessManager.openDisplaySettings(this) }
        binding.btnBatteryExempt.setOnClickListener { BatteryAwarenessManager.requestIgnoreBatteryOptimizations(this) }
        binding.batteryWarning.setOnClickListener { BatteryAwarenessManager.openBatterySaverSettings(this) }

        binding.autoDetectSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !PerformanceModeManager.hasUsageAccess(this)) {
                binding.autoDetectSwitch.isChecked = false
                startActivity(Intent(this, PermissionActivity::class.java))
                return@setOnCheckedChangeListener
            }
            val intent = Intent(this, GameForegroundWatcherService::class.java)
            if (checked) ContextCompat.startForegroundService(this, intent) else stopService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadGames()
        updateStatusLine()
    }

    private fun loadGames() {
        val games = GameRepository.findGames(this)
        val manual = ManualGameStore.getAll(this)
        binding.gameList.adapter = GameListAdapter(
            games, manual,
            onBoostLaunch = { game -> boostAndLaunch(game) },
            onRemoveManual = { game ->
                ManualGameStore.remove(this, game.packageName)
                loadGames()
            }
        )
    }

    private fun updateAutoDetectSwitch() {
        // Reflects reality without us tracking separate state: if the service
        // isn't running the switch shouldn't claim it is after a process restart.
        binding.autoDetectSwitch.isChecked = isServiceRunning(GameForegroundWatcherService::class.java)
    }

    private fun isServiceRunning(cls: Class<*>): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningServices(Int.MAX_VALUE).any { it.service.className == cls.name }
    }

    private fun boostAndLaunch(game: GameInfo) {
        if (!ensureOverlayPermission()) return
        // Kick background apps (excluding the game itself and us), then start the HUD,
        // then launch the game — in that order so the HUD is already up when it opens.
        PerformanceModeManager.trimBackgroundApps(this, setOf(game.packageName, packageName))
        startOverlay()
        GameRepository.launchIntentFor(this, game.packageName)?.let { startActivity(it) }
    }

    private fun onOverlayClicked() {
        if (!ensureOverlayPermission()) return
        startOverlay()
    }

    private fun startOverlay() {
        ContextCompat.startForegroundService(this, Intent(this, BoosterOverlayService::class.java))
    }

    private fun onCaptureClicked(mode: String) {
        pendingCaptureMode = mode
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun onCacheClicked() {
        if (!PerformanceModeManager.hasUsageAccess(this)) {
            startActivity(Intent(this, PermissionActivity::class.java))
            return
        }
        // Minimal first pass: send the user to the first installed game's own
        // storage screen, where "Clear cache" is one tap. A fuller per-app
        // cache browser is a natural next iteration (see README "Next up").
        val games = GameRepository.findGames(this)
        val target = games.firstOrNull()?.packageName ?: packageName
        PerformanceModeManager.openAppStorageSettings(this, target)
    }

    private fun ensureOverlayPermission(): Boolean {
        if (Settings.canDrawOverlays(this)) return true
        startActivity(Intent(this, PermissionActivity::class.java))
        return false
    }

    private fun updateStatusLine() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val freeMb = info.availMem / (1024 * 1024)
        val thermalText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            "Thermal: status ${pm.currentThermalStatus}"
        } else "Thermal: unavailable below Android 10"
        binding.statusLine.text = "RAM free: ${freeMb} MB · $thermalText"

        binding.batteryWarning.visibility =
            if (BatteryAwarenessManager.isPowerSaveMode(this)) android.view.View.VISIBLE
            else android.view.View.GONE
    }
}
