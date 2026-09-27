package com.codenamezeroseven.gitpulse.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codenamezeroseven.gitpulse.Task
import com.codenamezeroseven.gitpulse.databinding.ItemTaskBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TaskAdapter(
    private val onToggle: (Long) -> Unit,
    private val onDelete: (Long) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private val items = mutableListOf<Task>()

    fun submit(list: List<Task>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        val b = holder.binding

        b.check.setOnCheckedChangeListener(null)
        b.check.isChecked = t.completed
        b.check.setOnCheckedChangeListener { _, _ -> onToggle(t.id) }

        b.title.text = t.title
        b.title.paintFlags = if (t.completed) {
            b.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            b.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        b.title.alpha = if (t.completed) 0.6f else 1f

        b.keywords.text =
            if (t.keywords.isBlank()) "Manual completion only"
            else "Keywords: ${t.keywords}"

        val fmt = DateTimeFormatter.ofPattern("d MMM")
        b.status.text = when {
            t.completed && t.completionSource.startsWith("GitHub:") ->
                "Auto-completed ${LocalDate.ofEpochDay(t.completedAtEpochDay).format(fmt)} · ${t.completionSource.removePrefix("GitHub: ")}"
            t.completed ->
                "Completed manually ${LocalDate.ofEpochDay(t.completedAtEpochDay).format(fmt)}"
            else ->
                "Added ${LocalDate.ofEpochDay(t.createdAtEpochDay).format(fmt)}"
        }

        b.delete.setOnClickListener { onDelete(t.id) }
    }
}
