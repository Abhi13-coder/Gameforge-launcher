package com.gameforge.booster

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gameforge.booster.databinding.ActivityAddGameBinding
import com.gameforge.booster.databinding.ItemAppPickBinding

/**
 * Full installed-app picker for titles the CATEGORY_GAME / legacy-flag
 * heuristic in GameRepository missed. Picking one here persists it via
 * ManualGameStore so it shows up on the dashboard from then on.
 */
class AddGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddGameBinding
    private var allApps: List<GameInfo> = emptyList()
    private lateinit var adapter: PickAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        allApps = GameRepository.findAddableApps(this)
        adapter = PickAdapter(allApps) { app ->
            ManualGameStore.add(this, app.packageName)
            allApps = allApps.filter { it.packageName != app.packageName }
            adapter.update(filtered(binding.searchBox.text?.toString().orEmpty()))
        }
        binding.pickList.layoutManager = LinearLayoutManager(this)
        binding.pickList.adapter = adapter
        adapter.update(allApps)

        binding.searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                adapter.update(filtered(s?.toString().orEmpty()))
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun filtered(query: String): List<GameInfo> =
        if (query.isBlank()) allApps
        else allApps.filter { it.label.contains(query, ignoreCase = true) }

    private class PickAdapter(
        initial: List<GameInfo>,
        val onAdd: (GameInfo) -> Unit
    ) : RecyclerView.Adapter<PickAdapter.VH>() {

        private var items: List<GameInfo> = initial

        fun update(newItems: List<GameInfo>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(val binding: ItemAppPickBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val binding = ItemAppPickBinding.inflate(
                android.view.LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = items[position]
            holder.binding.pickName.text = app.label
            holder.binding.pickIcon.setImageDrawable(app.icon)
            holder.binding.pickAddBtn.setOnClickListener { onAdd(app) }
        }
    }
}
