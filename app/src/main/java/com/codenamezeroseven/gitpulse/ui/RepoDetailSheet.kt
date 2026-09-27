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
import androidx.lifecycle.lifecycleScope
import com.codenamezeroseven.gitpulse.GitHubApi
import com.codenamezeroseven.gitpulse.Prefs
import com.codenamezeroseven.gitpulse.Repo
import com.codenamezeroseven.gitpulse.TimeAgo
import com.codenamezeroseven.gitpulse.databinding.SheetRepoDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import androidx.appcompat.R as AppCompatR
import kotlinx.coroutines.launch
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

        // ---- In-app repo details: recent commits + README ----
        b.commitsContainer.removeAllViews()
        b.commitsContainer.addView(textRow("Loading commits…"))
        b.readmeText.text = "Loading README…"

        val token = Prefs.token
        if (token.isBlank()) {
            b.commitsContainer.removeAllViews()
            b.commitsContainer.addView(textRow("Connect GitHub to load commits and README."))
            b.readmeTitle.visibility = View.GONE
            b.readmeText.visibility = View.GONE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val commits = runCatching { GitHubApi.fetchRepoCommits(token, r.fullName) }.getOrNull()
            if (_b == null) return@launch
            b.commitsContainer.removeAllViews()
            if (commits.isNullOrEmpty()) {
                b.commitsContainer.addView(textRow("No commits visible for this repo."))
            } else {
                commits.forEachIndexed { i, (sha, msg, day) ->
                    val row = commitRow(sha, msg, TimeAgo.since(day))
                    b.commitsContainer.addView(row)
                    Anim.listIn(row, i)
                }
            }

            val readme = runCatching { GitHubApi.fetchReadme(token, r.fullName) }.getOrNull()
            if (_b == null) return@launch
            if (readme.isNullOrBlank()) {
                b.readmeTitle.visibility = View.GONE
                b.readmeText.visibility = View.GONE
            } else {
                b.readmeTitle.visibility = View.VISIBLE
                b.readmeText.visibility = View.VISIBLE
                b.readmeText.text = cleanReadme(readme)
            }
        }
    }

    /** Light markdown cleanup so the README reads as plain text. */
    private fun cleanReadme(md: String): String {
        var t = md
        t = t.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")   // images -> alt text
        t = t.replace(Regex("\\[([^\\]]*)]\\(([^)]*)\\)"), "$1") // links -> text
        t = t.replace(Regex("^[#>\\s]+", RegexOption.MULTILINE), "") // headers / quotes marks
        t = t.replace(Regex("[*_`|]{1,2}"), "")                  // emphasis / code / pipes
        t = t.replace(Regex("\\s+"), " ")                        // collapse whitespace
        t = t.trim()
        return if (t.length > 1200) t.take(1200).trimEnd() + " …" else t
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

    private fun commitRow(sha: String, msg: String, whenText: String): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val shaV = TextView(ctx).apply {
            text = sha
            typeface = Typeface.MONOSPACE
            setTextAppearance(ctx, MaterialR.style.TextAppearance_Material3_LabelMedium)
            setTextColor(MaterialColors.getColor(ctx, AppCompatR.attr.colorPrimary, "p"))
        }
        val timeV = TextView(ctx).apply {
            text = whenText
            setTextAppearance(ctx, MaterialR.style.TextAppearance_Material3_LabelMedium)
            setTextColor(MaterialColors.getColor(ctx, MaterialR.attr.colorOnSurfaceVariant, "v"))
        }
        top.addView(shaV, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(timeV, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val msgV = TextView(ctx).apply {
            text = msg
            setTextAppearance(ctx, MaterialR.style.TextAppearance_Material3_BodyMedium)
            setTextColor(MaterialColors.getColor(ctx, MaterialR.attr.colorOnSurface, "s"))
            maxLines = 2
        }
        row.addView(top)
        row.addView(msgV)
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
        fun show(fm: FragmentManager, repo: Repo) {
            RepoDetailSheet().apply {
                arguments = bundleOf("repo" to repo)
            }.show(fm, "repoDetail")
        }
    }
}
