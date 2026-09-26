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
import com.codenamezeroseven.gitpulse.databinding.FragmentGoalsBinding
import kotlinx.coroutines.launch

class GoalsFragment : Fragment() {

    private var _b: FragmentGoalsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentGoalsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.swipe.setOnRefreshListener { load(true) }
        b.connectBtn.setOnClickListener { (activity as? MainActivity)?.openProfile() }

        b.dailyMinus.setOnClickListener { Prefs.dailyGoal = Prefs.dailyGoal - 1; render() }
        b.dailyPlus.setOnClickListener { Prefs.dailyGoal = Prefs.dailyGoal + 1; render() }
        b.weeklyMinus.setOnClickListener { Prefs.weeklyGoal = Prefs.weeklyGoal - 1; render() }
        b.weeklyPlus.setOnClickListener { Prefs.weeklyGoal = Prefs.weeklyGoal + 1; render() }

        if (Prefs.token.isBlank()) showDisconnected() else load(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden && _b != null && Prefs.token.isNotBlank() && b.content.visibility == View.GONE) {
            load(false)
        }
    }

    private fun showDisconnected() {
        b.swipe.isRefreshing = false
        b.content.visibility = View.GONE
        b.disconnected.visibility = View.VISIBLE
    }

    private fun load(force: Boolean) {
        if (Prefs.token.isBlank()) {
            showDisconnected()
            return
        }
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
        val daily = GitHubData.cachedDailyCommits()

        b.dailyValue.text = Prefs.dailyGoal.toString()
        b.weeklyValue.text = Prefs.weeklyGoal.toString()

        val weekTotal = StatsEngine.weeklyTotal(daily)
        b.weekCaption.text = "$weekTotal of ${Prefs.weeklyGoal} commits this week"
        b.weekRing.setProgress(weekTotal.toFloat() / Prefs.weeklyGoal, animate = true)

        b.goalHeatmap.setData(daily, weeks = 5, threshold = Prefs.dailyGoal)

        b.streakValue.text = StatsEngine.streak(daily).toString() + " days"
        b.bestValue.text = StatsEngine.bestStreak(daily).toString() + " days"
        b.activeValue.text = StatsEngine.activeDays(daily).toString()
        b.totalValue.text = if (GitHubData.totalCommits >= 0) GitHubData.totalCommits.toString() else "…"
    }
}
