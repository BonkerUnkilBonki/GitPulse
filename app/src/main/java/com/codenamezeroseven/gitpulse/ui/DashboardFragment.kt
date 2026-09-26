package com.codenamezeroseven.gitpulse.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.codenamezeroseven.gitpulse.GitHubData
import com.codenamezeroseven.gitpulse.MainActivity
import com.codenamezeroseven.gitpulse.Prefs
import com.codenamezeroseven.gitpulse.StatsEngine
import com.codenamezeroseven.gitpulse.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class DashboardFragment : Fragment() {

    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentDashboardBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.swipe.setOnRefreshListener { load(true) }
        // Only allow pull-to-refresh when the scrollable content is actually at
        // the top; otherwise the swipe steals scroll-up gestures.
        b.swipe.setOnChildScrollUpCallback { _, _ -> b.content.canScrollVertically(-1) }
        b.connectBtn.setOnClickListener { (activity as? MainActivity)?.openProfile() }
        if (Prefs.token.isBlank()) showDisconnected() else load(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden && _b != null) {
            if (Prefs.token.isBlank()) {
                showDisconnected()
            } else if (GitHubData.user != null) {
                render()
            } else {
                load(false)
            }
        }
    }

    private fun showDisconnected() {
        b.swipe.isRefreshing = false
        b.swipe.isEnabled = false
        b.content.visibility = View.GONE
        b.disconnected.visibility = View.VISIBLE
    }

    private fun load(force: Boolean) {
        if (Prefs.token.isBlank()) {
            showDisconnected()
            return
        }
        b.swipe.isEnabled = true
        b.disconnected.visibility = View.GONE
        b.content.visibility = View.VISIBLE
        b.swipe.isRefreshing = GitHubData.user == null
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { GitHubData.refresh(force) }
            b.swipe.isRefreshing = false
            result.onSuccess { render() }
                .onFailure { e ->
                    Toast.makeText(requireContext(), e.message ?: "Failed to load", Toast.LENGTH_LONG).show()
                    render()
                }
        }
    }

    private fun render() {
        val d = GitHubData
        val u = d.user ?: return
        val daily = d.cachedDailyCommits()

        b.greeting.text = greeting() + ", " + (u.name ?: u.login)
        val today = daily[LocalDate.now()] ?: 0
        b.todayCommits.text = today.toString()
        b.goalCaption.text = "of ${Prefs.dailyGoal} commits today"
        b.ring.setProgress(today.toFloat() / Prefs.dailyGoal, animate = true)

        val streak = StatsEngine.streak(daily)
        b.streakChip.text = if (streak > 0) "$streak day streak" else "No streak yet — commit today"

        b.statRepos.text = u.publicRepos.toString()
        b.statStars.text = d.repos.filter { !it.isFork }.sumOf { it.stars }.toString()
        b.statFollowers.text = u.followers.toString()
        b.statPrs.text = if (d.totalPRs >= 0) d.totalPRs.toString() else "—"

        val (labels, values) = StatsEngine.weekValues(daily)
        b.barChart.setData(labels, values, Prefs.dailyGoal)

        b.heatmap.setData(daily, weeks = 26)

        b.totalCommitsValue.text = if (d.totalCommits >= 0) d.totalCommits.toString() else "…"
        val cutoff = LocalDate.now().minusDays(89)
        b.activeDaysValue.text = daily.count { it.value > 0 && !it.key.isBefore(cutoff) }.toString()
        b.bestStreakValue.text = StatsEngine.bestStreak(daily).toString()
    }

    private fun greeting(): String {
        val h = LocalTime.now().hour
        return when {
            h < 5 -> "Up late"
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
