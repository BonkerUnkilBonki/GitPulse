package com.codenamezeroseven.gitpulse.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.codenamezeroseven.gitpulse.CommitInfo
import com.codenamezeroseven.gitpulse.GhEvent
import com.codenamezeroseven.gitpulse.TimeAgo
import com.codenamezeroseven.gitpulse.databinding.SheetEventDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class EventDetailSheet : BottomSheetDialogFragment() {

    private var _b: SheetEventDetailBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = SheetEventDetailBinding.inflate(requireActivity().layoutInflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Paint the sheet container with the activity's surface color so the
        // sheet matches the app theme (dynamic color / pitch black included).
        dialog?.findViewById<View>(MaterialR.id.design_bottom_sheet)?.setBackgroundColor(
            MaterialColors.getColor(requireActivity(), MaterialR.attr.colorSurface, "surface")
        )
        val e = requireArguments().getParcelable<GhEvent>("event")
        if (e == null) {
            dismiss()
            return
        }
        render(e)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun render(e: GhEvent) {
        val ctx = requireContext()
        val style = EventVisual.of(e.type)
        b.icon.setImageResource(style.icon)
        b.icon.setColorFilter(MaterialColors.getColor(ctx, style.fgAttr, "fg"))
        b.iconBg.backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(ctx, style.bgAttr, "bg")
        )
        b.title.text = e.detail
        b.repo.text = e.repoName
        b.time.text = TimeAgo.since(e.createdAt) + " · " +
                e.createdAt.format(DateTimeFormatter.ofPattern("d MMM yyyy"))

        b.sectionContainer.removeAllViews()
        when {
            e.type == "PushEvent" -> {
                b.sectionTitle.text = "Commits"
                val list = e.commitList.take(12)
                list.forEach { c -> b.sectionContainer.addView(commitRow(c)) }
                if (e.commitList.size > 12) {
                    b.sectionContainer.addView(textRow("+ ${e.commitList.size - 12} more commits"))
                }
                if (list.isEmpty()) {
                    b.sectionContainer.addView(textRow("Commit details are not available for this push."))
                }
            }
            e.title != null -> {
                b.sectionTitle.text = "Details"
                b.sectionContainer.addView(kvRow("Title", e.title!!))
                e.action?.let { b.sectionContainer.addView(kvRow("Action", it)) }
                e.tag?.takeIf { it.isNotBlank() }?.let { b.sectionContainer.addView(kvRow("Tag", it)) }
                e.ref?.takeIf { it.isNotBlank() }?.let { b.sectionContainer.addView(kvRow("Reference", it)) }
            }
            else -> {
                b.sectionTitle.text = "Details"
                e.tag?.takeIf { it.isNotBlank() }?.let { b.sectionContainer.addView(kvRow("Tag", it)) }
                e.ref?.takeIf { it.isNotBlank() }?.let { b.sectionContainer.addView(kvRow("Reference", it)) }
                if (b.sectionContainer.childCount == 0) {
                    b.sectionContainer.addView(textRow("No extra details for this event."))
                }
            }
        }

        b.openBtn.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${e.repoName}")))
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun commitRow(c: CommitInfo): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val sha = TextView(ctx).apply {
            text = c.sha.take(7)
            typeface = Typeface.MONOSPACE
            setTextAppearance(ctx, MaterialR.style.TextAppearance_Material3_LabelMedium)
            setTextColor(MaterialColors.getColor(ctx, MaterialR.attr.colorPrimary, "p"))
        }
        val msg = TextView(ctx).apply {
            text = c.message.lineSequence().firstOrNull() ?: ""
            setTextAppearance(ctx, MaterialR.style.TextAppearance_Material3_BodyMedium)
            setTextColor(MaterialColors.getColor(ctx, MaterialR.attr.colorOnSurface, "s"))
            maxLines = 2
        }
        row.addView(sha, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(14) })
        row.addView(msg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun kvRow(label: String, value: String): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(6), 0, dp(6))
        }
        val l = TextView(ctx).apply {
            text = label
            setTextAppearance(ctx, MaterialR.style.TextAppearance_Material3_BodyMedium)
            setTextColor(MaterialColors.getColor(ctx, MaterialR.attr.colorOnSurfaceVariant, "v"))
        }
        val v = TextView(ctx).apply {
            text = value
            setTextAppearance(ctx, MaterialR.style.TextAppearance_Material3_BodyMedium)
            setTextColor(MaterialColors.getColor(ctx, MaterialR.attr.colorOnSurface, "s"))
        }
        row.addView(l, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun textRow(text: String): View {
        val ctx = requireContext()
        return TextView(ctx).apply {
            this.text = text
            setTextAppearance(ctx, MaterialR.style.TextAppearance_Material3_BodyMedium)
            setTextColor(MaterialColors.getColor(ctx, MaterialR.attr.colorOnSurfaceVariant, "v"))
            setPadding(0, dp(6), 0, dp(6))
        }
    }

    companion object {
        fun show(fm: FragmentManager, event: GhEvent) {
            EventDetailSheet().apply {
                arguments = bundleOf("event" to event)
            }.show(fm, "eventDetail")
        }
    }
}
