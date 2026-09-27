package com.codenamezeroseven.gitpulse.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import com.codenamezeroseven.gitpulse.ui.Anim
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.codenamezeroseven.gitpulse.GitHubData
import com.codenamezeroseven.gitpulse.MainActivity
import com.codenamezeroseven.gitpulse.Prefs
import com.codenamezeroseven.gitpulse.StatsEngine
import com.codenamezeroseven.gitpulse.TaskSync
import com.codenamezeroseven.gitpulse.databinding.FragmentGoalsBinding
import kotlinx.coroutines.launch
import java.time.LocalDate

class GoalsFragment : Fragment() {

    private var _b: FragmentGoalsBinding? = null
    private val b get() = _b!!

    // Re-render when a background sync pulls new goals from another device.
    private val syncListener: () -> Unit = {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (_b != null && isAdded) render()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentGoalsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.swipe.setOnRefreshListener { load(true) }
        b.swipe.setOnChildScrollUpCallback { _, _ -> b.content.canScrollVertically(-1) }
        b.connectBtn.setOnClickListener { (activity as? MainActivity)?.openProfile() }

        b.dailyValue.setText(Prefs.dailyGoal.toString())
        b.weeklyValue.setText(Prefs.weeklyGoal.toString())

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
        TaskSync.addListener(syncListener)

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
            result.onSuccess { render() }
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
        pushGoalsAsync()
        b.dailyValue.setText(Prefs.dailyGoal.toString())
        b.weeklyValue.setText(Prefs.weeklyGoal.toString())
        render()
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
        val daily = GitHubData.cachedDailyCommits()

        // Reflect synced goals in the inputs (unless the user is typing).
        if (!b.dailyValue.hasFocus()) b.dailyValue.setText(Prefs.dailyGoal.toString())
        if (!b.weeklyValue.hasFocus()) b.weeklyValue.setText(Prefs.weeklyGoal.toString())

        val weekTotal = StatsEngine.weeklyTotal(daily)
        b.weekCaption.text = "$weekTotal of ${Prefs.weeklyGoal} commits this week"
        b.weekRing.setProgress(weekTotal.toFloat() / Prefs.weeklyGoal, animate = true)

        b.goalHeatmap.setData(daily, weeks = 5, threshold = Prefs.dailyGoal)

        var met = 0
        for (i in 0 until 35) {
            val day = LocalDate.now().minusDays(i.toLong())
            if ((daily[day] ?: 0) >= Prefs.dailyGoal) met++
        }
        b.goalHistorySummary.text = "$met of the last 35 days met your daily goal"

        val cutoff = LocalDate.now().minusDays(89)
        Anim.countUp(b.streakValue, StatsEngine.streak(daily), suffix = " days")
        Anim.countUp(b.bestValue, StatsEngine.bestStreak(daily), suffix = " days")
        Anim.countUp(b.activeValue, daily.count { it.value > 0 && !it.key.isBefore(cutoff) })
        b.totalValue.text = if (GitHubData.totalCommits >= 0) GitHubData.totalCommits.toString() else "…"
    }
}
