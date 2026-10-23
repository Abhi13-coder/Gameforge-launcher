package com.gameforge.booster

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gameforge.booster.databinding.ActivityPermissionsBinding

/**
 * One screen, one honest list: every permission GameForge asks for, why,
 * and a direct link to grant it. No permission is requested silently.
 */
class PermissionActivity : AppCompatActivity() {

    private data class Row(val title: String, val why: String, val granted: () -> Boolean, val grant: () -> Unit)

    private lateinit var binding: ActivityPermissionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.permList.layoutManager = LinearLayoutManager(this)
        bind()
    }

    override fun onResume() {
        super.onResume()
        bind() // re-check granted state whenever user comes back from Settings
    }

    private fun rows(): List<Row> = listOf(
        Row(
            "Display over other apps",
            "Needed to draw the floating HUD (fps/ram/thermal + quick toggles) on top of your game.",
            { Settings.canDrawOverlays(this) },
            {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
        ),
        Row(
            "Notifications",
            "Android requires a visible notification for any foreground service (HUD, recorder).",
            { Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED },
            {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
                }
            }
        ),
        Row(
            "Do Not Disturb access",
            "Lets the HUD's DND toggle silence notifications during a match, and restore them after.",
            { PerformanceModeManager.hasNotificationPolicyAccess(this) },
            { PerformanceModeManager.requestNotificationPolicyAccess(this) }
        ),
        Row(
            "Usage access",
            "Used only to list background apps for the Cache/RAM tools and to read per-app cache size. GameForge never reads what you did inside those apps.",
            { PerformanceModeManager.hasUsageAccess(this) },
            { PerformanceModeManager.requestUsageAccess(this) }
        )
    )

    private fun bind() {
        binding.permList.adapter = object : RecyclerView.Adapter<RowHolder>() {
            val data = rows()
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RowHolder {
                val v = android.widget.LinearLayout(parent.context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(0, 24, 0, 24)
                }
                val title = android.widget.TextView(parent.context).apply { textSize = 15f }
                val why = android.widget.TextView(parent.context).apply { textSize = 12f; alpha = 0.7f }
                val btn = android.widget.Button(parent.context)
                v.addView(title); v.addView(why); v.addView(btn)
                return RowHolder(v, title, why, btn)
            }

            override fun getItemCount() = data.size

            override fun onBindViewHolder(holder: RowHolder, position: Int) {
                val row = data[position]
                val granted = row.granted()
                holder.title.text = row.title + if (granted) "  ✓" else ""
                holder.why.text = row.why
                holder.btn.text = if (granted) "Granted" else "Grant"
                holder.btn.isEnabled = !granted
                holder.btn.setOnClickListener { row.grant() }
            }
        }
    }

    private class RowHolder(
        view: android.view.View,
        val title: android.widget.TextView,
        val why: android.widget.TextView,
        val btn: android.widget.Button
    ) : RecyclerView.ViewHolder(view)
}
