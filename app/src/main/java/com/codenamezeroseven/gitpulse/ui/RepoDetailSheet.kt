package com.codenamezeroseven.gitpulse.ui

import android.content.Intent
import android.content.res.ColorStateList
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
import com.codenamezeroseven.gitpulse.Repo
import com.codenamezeroseven.gitpulse.TimeAgo
import com.codenamezeroseven.gitpulse.databinding.SheetRepoDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import kotlin.math.roundToInt

class RepoDetailSheet : BottomSheetDialogFragment() {

    private var _b: SheetRepoDetailBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = SheetRepoDetailBinding.inflate(requireActivity().layoutInflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val sheet = dialog?.findViewById<View>(MaterialR.id.design_bottom_sheet)
        val surface = MaterialColors.getColor(requireActivity(), MaterialR.attr.colorSurface, "surface")
        val shape = com.google.android.material.shape.MaterialShapeDrawable(
            com.google.android.material.shape.ShapeAppearanceModel.builder()
                .setTopLeftCorner(com.google.android.material.shape.CornerFamily.ROUNDED, dp(32).toFloat())
                .setTopRightCorner(com.google.android.material.shape.CornerFamily.ROUNDED, dp(32).toFloat())
                .build()
        )
        shape.fillColor = ColorStateList.valueOf(surface)
        sheet?.background = shape
        val r = requireArguments().getParcelable<Repo>("repo")
        if (r == null) {
            dismiss()
            return
        }
        render(r)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun render(r: Repo) {
        val ctx = requireContext()
        b.name.text = r.name
        b.fullName.text = r.fullName
        b.description.text = r.description ?: "No description"
        b.languageDot.backgroundTintList = ColorStateList.valueOf(LangColors.of(r.language))

        b.sectionContainer.removeAllViews()
        addRow("Language", r.language ?: "Mixed")
        addRow("Stars", if (r.stars > 0) r.stars.toString() else "0")
        addRow("Last pushed", r.pushedAt?.let { TimeAgo.since(it) } ?: "Unknown")
        addRow("Fork", if (r.isFork) "Yes" else "No")

        b.openBtn.setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun addRow(label: String, value: String) {
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
        b.sectionContainer.addView(row)
    }

    companion object {
        fun show(fm: FragmentManager, repo: Repo) {
            RepoDetailSheet().apply {
                arguments = bundleOf("repo" to repo)
            }.show(fm, "repoDetail")
        }
    }
}
