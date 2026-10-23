package com.gameforge.booster

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gameforge.booster.databinding.ItemGameBinding

class GameListAdapter(
    private val games: List<GameInfo>,
    private val manuallyAdded: Set<String>,
    private val onBoostLaunch: (GameInfo) -> Unit,
    private val onRemoveManual: (GameInfo) -> Unit
) : RecyclerView.Adapter<GameListAdapter.VH>() {

    inner class VH(val binding: ItemGameBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun getItemCount() = games.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val game = games[position]
        holder.binding.name.text = game.label
        holder.binding.icon.setImageDrawable(game.icon)
        holder.binding.btnBoostLaunch.setOnClickListener { onBoostLaunch(game) }
        if (game.packageName in manuallyAdded) {
            holder.binding.root.setOnLongClickListener { onRemoveManual(game); true }
        } else {
            holder.binding.root.setOnLongClickListener(null)
        }
    }
}
