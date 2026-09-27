package com.codenamezeroseven.gitpulse.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.codenamezeroseven.gitpulse.Repo
import com.codenamezeroseven.gitpulse.TimeAgo
import com.codenamezeroseven.gitpulse.databinding.ItemRepoBinding

class RepoAdapter : RecyclerView.Adapter<RepoAdapter.VH>() {

    private val items = mutableListOf<Repo>()
    private var lastAnimated = -1

    fun submit(list: List<Repo>) {
        if (items == list) return
        items.clear()
        items.addAll(list)
        lastAnimated = -1
        notifyDataSetChanged()
    }

    class VH(val binding: ItemRepoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemRepoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        val b = holder.binding
        b.name.text = r.name
        b.description.text = r.description ?: "No description"
        b.languageDot.backgroundTintList = android.content.res.ColorStateList.valueOf(LangColors.of(r.language))
        b.language.text = r.language ?: "Mixed"
        b.stars.text = if (r.stars > 0) "\u2605 ${r.stars}" else ""
        b.pushed.text = r.pushedAt?.let { "Pushed " + TimeAgo.since(it) } ?: ""
        b.root.setOnClickListener { view ->
            (view.context as? AppCompatActivity)?.let { act ->
                RepoDetailSheet.show(act.supportFragmentManager, r)
            }
        }

        if (position > lastAnimated) {
            lastAnimated = position
            Anim.listIn(holder.itemView, position)
        }
    }
}
