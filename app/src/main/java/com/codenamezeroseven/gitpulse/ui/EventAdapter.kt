package com.codenamezeroseven.gitpulse.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.codenamezeroseven.gitpulse.GhEvent
import com.codenamezeroseven.gitpulse.R
import com.codenamezeroseven.gitpulse.TimeAgo
import com.codenamezeroseven.gitpulse.databinding.ItemEventBinding
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors

object EventVisual {
    data class Style(val icon: Int, val bgAttr: Int, val fgAttr: Int)

    fun of(type: String): Style = when {
        type == "PushEvent" -> Style(
            R.drawable.ic_ev_push,
            MaterialR.attr.colorPrimaryContainer, MaterialR.attr.colorOnPrimaryContainer
        )
        type.startsWith("PullRequest") -> Style(
            R.drawable.ic_ev_pr,
            MaterialR.attr.colorSecondaryContainer, MaterialR.attr.colorOnSecondaryContainer
        )
        type == "IssuesEvent" || type == "IssueCommentEvent" -> Style(
            R.drawable.ic_ev_issue,
            MaterialR.attr.colorTertiaryContainer, MaterialR.attr.colorOnTertiaryContainer
        )
        type == "WatchEvent" -> Style(
            R.drawable.ic_ev_star,
            MaterialR.attr.colorErrorContainer, MaterialR.attr.colorOnErrorContainer
        )
        type == "ForkEvent" -> Style(
            R.drawable.ic_ev_fork,
            MaterialR.attr.colorSecondaryContainer, MaterialR.attr.colorOnSecondaryContainer
        )
        type == "CreateEvent" || type == "DeleteEvent" -> Style(
            R.drawable.ic_ev_create,
            MaterialR.attr.colorPrimaryContainer, MaterialR.attr.colorOnPrimaryContainer
        )
        type == "ReleaseEvent" -> Style(
            R.drawable.ic_ev_release,
            MaterialR.attr.colorTertiaryContainer, MaterialR.attr.colorOnTertiaryContainer
        )
        else -> Style(
            R.drawable.ic_ev_push,
            MaterialR.attr.colorSurfaceVariant, MaterialR.attr.colorOnSurfaceVariant
        )
    }
}

class EventAdapter : RecyclerView.Adapter<EventAdapter.VH>() {

    private val items = mutableListOf<GhEvent>()
    private var lastAnimated = -1

    fun submit(list: List<GhEvent>) {
        if (items == list) return
        items.clear()
        items.addAll(list)
        lastAnimated = -1
        notifyDataSetChanged()
    }

    class VH(val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val ctx = holder.binding.root.context
        val style = EventVisual.of(e.type)

        holder.binding.icon.setImageResource(style.icon)
        holder.binding.icon.setColorFilter(
            MaterialColors.getColor(ctx, style.fgAttr, "fg")
        )
        holder.binding.iconBg.backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(ctx, style.bgAttr, "bg")
        )

        holder.binding.title.text = e.detail
        holder.binding.repo.text = e.repoName
        holder.binding.time.text = TimeAgo.since(e.createdAt)

        val extra = when {
            e.type == "PushEvent" && e.commitList.isNotEmpty() ->
                e.commitList.first().message.lineSequence().firstOrNull()
            e.title != null -> e.title
            else -> null
        }
        if (extra.isNullOrBlank()) {
            holder.binding.subdetail.visibility = View.GONE
        } else {
            holder.binding.subdetail.visibility = View.VISIBLE
            holder.binding.subdetail.text = extra
        }

        holder.binding.root.setOnClickListener { view ->
            (view.context as? AppCompatActivity)?.let { act ->
                EventDetailSheet.show(act.supportFragmentManager, e)
            }
        }

        if (position > lastAnimated) {
            lastAnimated = position
            Anim.listIn(holder.itemView, position)
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
        runCatching { android.graphics.Color.parseColor(map[lang] ?: "#78909C") }
            .getOrDefault(android.graphics.Color.GRAY)
}
