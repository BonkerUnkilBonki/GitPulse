package com.codenamezeroseven.gitpulse.ui

import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.codenamezeroseven.gitpulse.GitPulseApp
import com.codenamezeroseven.gitpulse.GitHubApi
import com.codenamezeroseven.gitpulse.GitHubData
import com.codenamezeroseven.gitpulse.Palette
import com.codenamezeroseven.gitpulse.Prefs
import com.codenamezeroseven.gitpulse.R
import com.codenamezeroseven.gitpulse.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentProfileBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.connectBtn.setOnClickListener { connect() }
        Anim.pressable(b.connectBtn)
        b.refreshBtn.setOnClickListener { refreshNow() }
        b.signoutBtn.setOnClickListener { confirmSignOut() }
        b.btnTelegram.setOnClickListener {
            runCatching {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/BonkerUnkilBonki")))
            }
        }
        b.btnGithub.setOnClickListener {
            runCatching {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/BonkerUnkilBonki")))
            }
        }
        Anim.pressable(b.btnTelegram, b.btnGithub)
        setupThemeChips()
        setupPaletteRow()
        setupSyncSwitch()
        renderState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden && _b != null) renderState()
    }

    private fun setupThemeChips() {
        when (Prefs.theme) {
            "light" -> b.chipThemeLight.isChecked = true
            "dark" -> b.chipThemeDark.isChecked = true
            "black" -> b.chipThemeBlack.isChecked = true
            else -> b.chipThemeSystem.isChecked = true
        }
        b.chipThemeSystem.setOnCheckedChangeListener { _, checked -> if (checked) applyTheme("system") }
        b.chipThemeLight.setOnCheckedChangeListener { _, checked -> if (checked) applyTheme("light") }
        b.chipThemeDark.setOnCheckedChangeListener { _, checked -> if (checked) applyTheme("dark") }
        b.chipThemeBlack.setOnCheckedChangeListener { _, checked -> if (checked) applyTheme("black") }
    }

    /** Color palette picker - swatch circles for each palette. */
    private fun setupPaletteRow() {
        val row = b.paletteRow
        row.removeAllViews()
        val dp = resources.displayMetrics.density
        val entries = Palette.all.filter {
            it.id != "dynamic" || Build.VERSION.SDK_INT >= 31
        }
        entries.forEach { entry ->
            val col = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            }
            val circle = View(requireContext())
            circle.layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt())
            val selected = entry.id == Prefs.palette
            circle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(entry.swatch)
                if (selected) {
                    setStroke(
                        (3 * dp).toInt(),
                        com.google.android.material.color.MaterialColors.getColor(
                            circle, com.google.android.material.R.attr.colorOnSurface
                        )
                    )
                }
            }
            Anim.pressable(circle)
            circle.setOnClickListener {
                if (Prefs.palette != entry.id) {
                    Prefs.palette = entry.id
                    activity?.recreate()
                }
            }
            val label = TextView(requireContext()).apply {
                text = entry.label
                gravity = Gravity.CENTER
                textSize = 12f
                setPadding(0, (4 * dp).toInt(), 0, 0)
                setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        circle, com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
            }
            col.addView(circle)
            col.addView(label)
            row.addView(col)
        }
    }

    private fun setupSyncSwitch() {
        b.switchExcludeSync.isChecked = Prefs.excludeSyncCommits
        b.switchExcludeSync.setOnCheckedChangeListener { _, checked ->
            Prefs.excludeSyncCommits = checked
            // Recompute the stats with the new setting right away.
            if (Prefs.token.isNotBlank()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching { GitHubData.refresh(force = true) }
                }
            }
        }

        b.switchExcludeSyncTasks.isChecked = Prefs.excludeSyncTasks
        b.switchExcludeSyncTasks.setOnCheckedChangeListener { _, checked ->
            Prefs.excludeSyncTasks = checked
        }

        b.switchActivityNotifs.isChecked = Prefs.activityNotifs
        b.switchActivityNotifs.setOnCheckedChangeListener { _, checked ->
            Prefs.activityNotifs = checked
        }
    }

    private fun applyTheme(theme: String) {
        Prefs.theme = theme
        GitPulseApp.applyNightMode()
        activity?.recreate()
    }

    private fun renderState() {
        val connected = Prefs.token.isNotBlank()
        b.loginSection.visibility = if (connected) View.GONE else View.VISIBLE
        b.profileSection.visibility = if (connected) View.VISIBLE else View.GONE
        if (connected) renderProfile()
    }

    private fun connect() {
        val token = b.tokenInput.text?.toString()?.trim().orEmpty()
        if (token.length < 20) {
            Toast.makeText(requireContext(), "That does not look like a valid token", Toast.LENGTH_SHORT).show()
            return
        }
        b.progress.visibility = View.VISIBLE
        b.errorText.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val u = GitHubApi.fetchUser(token)
                Prefs.token = token
                GitHubData.clearLocal()
                u
            }
            b.progress.visibility = View.GONE
            result.onSuccess {
                Toast.makeText(requireContext(), "Connected as ${it.login}", Toast.LENGTH_SHORT).show()
                renderState()
                launch { runCatching { GitHubData.refresh(true) } }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@launch
                b.errorText.text = e.message ?: "Connection failed"
                b.errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun refreshNow() {
        b.progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { GitHubData.refresh(true) }
            b.progress.visibility = View.GONE
            result.onSuccess {
                Toast.makeText(requireContext(), "Data refreshed", Toast.LENGTH_SHORT).show()
                renderProfile()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) return@launch
                Toast.makeText(requireContext(), e.message ?: "Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sign out?")
            .setMessage("Your token and cached stats will be removed from this device.")
            .setPositiveButton("Sign out") { _, _ ->
                Prefs.signOut()
                GitHubData.clearLocal()
                renderState()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderProfile() {
        val u = GitHubData.user ?: return
        b.name.text = u.name ?: u.login
        b.loginHandle.text = "@" + u.login
        b.bio.text = u.bio ?: ""
        b.bio.visibility = if (u.bio.isNullOrBlank()) View.GONE else View.VISIBLE
        b.chipFollowers.text = "${u.followers} followers"
        b.chipFollowing.text = "${u.following} following"
        b.chipRepos.text = "${u.publicRepos} public repos"
        b.locationText.text = u.location ?: ""
        b.locationText.visibility = if (u.location.isNullOrBlank()) View.GONE else View.VISIBLE
        b.blogText.text = u.blog ?: ""
        b.blogText.visibility = if (u.blog.isNullOrBlank()) View.GONE else View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val bmp = GitHubApi.fetchBytes(u.avatarUrl)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            withContext(Dispatchers.Main) {
                if (_b != null && bmp != null) b.avatar.setImageBitmap(bmp)
            }
        }
    }
}
