package com.codenamezeroseven.gitpulse.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.codenamezeroseven.gitpulse.GitHubData
import com.codenamezeroseven.gitpulse.MainActivity
import com.codenamezeroseven.gitpulse.Prefs
import com.codenamezeroseven.gitpulse.StatsEngine
import com.codenamezeroseven.gitpulse.TaskSync
import com.codenamezeroseven.gitpulse.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class DashboardFragment : Fragment() {

    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!

    // Re-render when a background sync lands (tasks and synced goals).
    private val syncListener: () -> Unit = {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (_b != null && isAdded && GitHubData.user != null) render()
        }
    }

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
        TaskSync.addListener(syncListener)

        b.dailyValue.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveGoals()
                v.clearFocus()
                true
            } else false
        }
        b.weeklyValue.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveGoals()
                v.clearFocus()
                true
            } else false
        }
        b.dailyValue.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveGoals() }
        b.weeklyValue.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveGoals() }
        if (Prefs.token.isBlank()) showDisconnected() else load(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        TaskSync.removeListener(syncListener)
        _b = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden && _b != null) {
            if (Prefs.token.isBlank()) {
                showDisconnected()
            } else if (GitHubData.user != null) {
                render()
                animateIn()
            } else {
                load(false)
            }
        }
    }

    /** Staggered card entrance - the screen slides into place. */
    private fun animateIn() {
        (b.content.getChildAt(0) as? ViewGroup)?.let { Anim.staggerChildren(it) }
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
            result.onSuccess {
                render()
                animateIn()
            }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) return@launch
                    Toast.makeText(requireContext(), e.message ?: "Failed to load", Toast.LENGTH_LONG).show()
                    render()
                }
        }
    }

    private fun saveGoals() {
        val d = b.dailyValue.text?.toString()?.toIntOrNull() ?: Prefs.dailyGoal
        val w = b.weeklyValue.text?.toString()?.toIntOrNull() ?: Prefs.weeklyGoal
        if (d == Prefs.dailyGoal && w == Prefs.weeklyGoal) return
        Prefs.dailyGoal = d
        Prefs.weeklyGoal = w
        Prefs.goalsUpdatedAt = System.currentTimeMillis()
        render()
        pushGoalsAsync()
    }

    /** Push the new goal numbers to the sync repo (other devices pick them up). */
    private fun pushGoalsAsync() {
        val token = Prefs.token
        val owner = GitHubData.user?.login ?: Prefs.ownerLogin.takeIf { it.isNotBlank() } ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { TaskSync.sync(token, owner) }
        }
    }

    private fun render() {
        val d = GitHubData
        val u = d.user ?: return
        val daily = d.cachedDailyCommits()

        b.greeting.text = greeting() + ", " + (u.name ?: u.login)
        val today = daily[LocalDate.now()] ?: 0
        Anim.countUp(b.todayCommits, today)
        b.goalCaption.text = "of ${Prefs.dailyGoal} commits today"
        b.ring.setProgress(today.toFloat() / Prefs.dailyGoal, animate = true)
        b.todayWave.setProgress(
            ((today.toFloat() / Prefs.dailyGoal).coerceIn(0f, 1f) * 100).toInt()
        )

        val streak = StatsEngine.streak(daily)
        b.streakChip.text = if (streak > 0) "$streak day streak" else "No streak yet — commit today"

        Anim.countUp(b.statRepos, u.publicRepos)
        Anim.countUp(b.statStars, d.repos.filter { !it.isFork }.sumOf { it.stars })
        Anim.countUp(b.statFollowers, u.followers)
        if (d.totalPRs >= 0) Anim.countUp(b.statPrs, d.totalPRs) else b.statPrs.text = "—"

        val (labels, values) = StatsEngine.weekValues(daily)
        b.barChart.setData(labels, values, Prefs.dailyGoal)

        b.heatmap.setData(daily, weeks = 26)

        // Weekly goal ring, inputs and 5-week goal history (moved from Goals tab)
        val weekTotal = StatsEngine.weeklyTotal(daily)
        b.weekCaption.text = "$weekTotal of ${Prefs.weeklyGoal} commits this week"
        b.weekRing.setProgress(weekTotal.toFloat() / Prefs.weeklyGoal, animate = true)
        if (!b.dailyValue.hasFocus()) b.dailyValue.setText(Prefs.dailyGoal.toString())
        if (!b.weeklyValue.hasFocus()) b.weeklyValue.setText(Prefs.weeklyGoal.toString())
        b.goalHeatmap.setData(daily, weeks = 5, threshold = Prefs.dailyGoal)
        var met = 0
        for (i in 0 until 35) {
            val day = LocalDate.now().minusDays(i.toLong())
            if ((daily[day] ?: 0) >= Prefs.dailyGoal) met++
        }
        b.goalHistorySummary.text = "$met of the last 35 days met your daily goal"

        if (d.totalCommits >= 0) Anim.countUp(b.totalCommitsValue, d.totalCommits) else b.totalCommitsValue.text = "…"
        val cutoff = LocalDate.now().minusDays(89)
        Anim.countUp(b.activeDaysValue, daily.count { it.value > 0 && !it.key.isBefore(cutoff) })
        Anim.countUp(b.bestStreakValue, StatsEngine.bestStreak(daily))
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
