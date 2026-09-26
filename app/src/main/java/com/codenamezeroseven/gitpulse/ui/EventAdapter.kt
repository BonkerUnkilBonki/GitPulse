package com.codenamezeroseven.gitpulse.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codenamezeroseven.gitpulse.GhEvent
import com.codenamezeroseven.gitpulse.R
import com.codenamezeroseven.gitpulse.TimeAgo
import com.codenamezeroseven.gitpulse.databinding.ItemEventBinding
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors

class EventAdapter : RecyclerView.Adapter<EventAdapter.VH>() {

    private val items = mutableListOf<GhEvent>()

    fun submit(list: List<GhEvent>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val ctx = holder.binding.root.context
        val (icon, bgAttr, fgAttr) = when {
            e.type == "PushEvent" -> Triple(
                R.drawable.ic_ev_push,
                MaterialR.attr.colorPrimaryContainer, MaterialR.attr.colorOnPrimaryContainer
            )
            e.type.startsWith("PullRequest") -> Triple(
                R.drawable.ic_ev_pr,
                MaterialR.attr.colorSecondaryContainer, MaterialR.attr.colorOnSecondaryContainer
            )
            e.type == "IssuesEvent" || e.type == "IssueCommentEvent" -> Triple(
                R.drawable.ic_ev_issue,
                MaterialR.attr.colorTertiaryContainer, MaterialR.attr.colorOnTertiaryContainer
            )
            e.type == "WatchEvent" -> Triple(
                R.drawable.ic_ev_star,
                MaterialR.attr.colorErrorContainer, MaterialR.attr.colorOnErrorContainer
            )
            e.type == "ForkEvent" -> Triple(
                R.drawable.ic_ev_fork,
                MaterialR.attr.colorSecondaryContainer, MaterialR.attr.colorOnSecondaryContainer
            )
            e.type == "CreateEvent" || e.type == "DeleteEvent" -> Triple(
                R.drawable.ic_ev_create,
                MaterialR.attr.colorPrimaryContainer, MaterialR.attr.colorOnPrimaryContainer
            )
            e.type == "ReleaseEvent" -> Triple(
                R.drawable.ic_ev_release,
                MaterialR.attr.colorTertiaryContainer, MaterialR.attr.colorOnTertiaryContainer
            )
            else -> Triple(
                R.drawable.ic_ev_push,
                MaterialR.attr.colorSurfaceVariant, MaterialR.attr.colorOnSurfaceVariant
            )
        }
        val bg = MaterialColors.getColor(ctx, bgAttr, "bg")
        val fg = MaterialColors.getColor(ctx, fgAttr, "fg")
        holder.binding.icon.setImageResource(icon)
        holder.binding.icon.setColorFilter(fg)
        holder.binding.iconBg.backgroundTintList = ColorStateList.valueOf(bg)

        holder.binding.title.text = e.detail
        holder.binding.repo.text = e.repoName
        holder.binding.time.text = TimeAgo.since(e.createdAt)
        holder.binding.root.setOnClickListener {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${e.repoName}")))
            }
        }
    }
}

object LangColors {
    private val map = mapOf(
        "Kotlin" to "#A97BFF", "Java" to "#B07219", "Python" to "#3572A5",
        "JavaScript" to "#F1E05A", "TypeScript" to "#3178C6", "Go" to "#00ADD8",
        "Rust" to "#DEA584", "C" to "#555555", "C++" to "#F34B7D", "C#" to "#178600",
        "Ruby" to "#701516", "PHP" to "#4F5D95", "Swift" to "#F05138", "Dart" to "#00B4AB",
        "HTML" to "#E34C26", "CSS" to "#563D7C", "Shell" to "#89E051",
        "Jupyter Notebook" to "#DA5B0B", "Vue" to "#41B883", "Scala" to "#C22D40",
        "Lua" to "#000080", "Perl" to "#0298C3", "Haskell" to "#5E5086"
    )

    fun of(lang: String?): Int =
        runCatching { Color.parseColor(map[lang] ?: "#78909C") }.getOrDefault(Color.GRAY)
}
