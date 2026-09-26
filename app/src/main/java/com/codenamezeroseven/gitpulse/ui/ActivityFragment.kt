package com.codenamezeroseven.gitpulse.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codenamezeroseven.gitpulse.GitHubData
import com.codenamezeroseven.gitpulse.MainActivity
import com.codenamezeroseven.gitpulse.Prefs
import com.codenamezeroseven.gitpulse.databinding.FragmentActivityBinding
import kotlinx.coroutines.launch

class ActivityFragment : Fragment() {

    private var _b: FragmentActivityBinding? = null
    private val b get() = _b!!
    private val adapter = EventAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentActivityBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter
        b.swipe.setOnRefreshListener { load(true) }
        b.swipe.setOnChildScrollUpCallback { _, _ -> b.recycler.canScrollVertically(-1) }
        b.connectBtn.setOnClickListener { (activity as? MainActivity)?.openProfile() }

        b.chipAll.setOnCheckedChangeListener { _, checked -> if (checked) render() }
        b.chipFCommits.setOnCheckedChangeListener { _, checked -> if (checked) render() }
        b.chipFPrs.setOnCheckedChangeListener { _, checked -> if (checked) render() }
        b.chipFIssues.setOnCheckedChangeListener { _, checked -> if (checked) render() }
        b.chipFStars.setOnCheckedChangeListener { _, checked -> if (checked) render() }

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
        val events = GitHubData.events

        val daily = GitHubData.cachedDailyCommits()
        val cutoff = java.time.LocalDate.now().minusDays(89)
        val commits = daily.filterKeys { !it.isBefore(cutoff) }.values.sum()
        val prs = events.count { it.type.startsWith("PullRequest") }
        val issues = events.count { it.type == "IssuesEvent" || it.type == "IssueCommentEvent" }
        val stars = events.count { it.type == "WatchEvent" }
        b.statCommits.text = commits.toString()
        b.statPrs.text = prs.toString()
        b.statIssues.text = issues.toString()
        b.statStars.text = stars.toString()

        val filtered = when {
            b.chipFCommits.isChecked -> events.filter { it.type == "PushEvent" }
            b.chipFPrs.isChecked -> events.filter { it.type.startsWith("PullRequest") }
            b.chipFIssues.isChecked ->
                events.filter { it.type == "IssuesEvent" || it.type == "IssueCommentEvent" || it.type.startsWith("PullRequest") }
            b.chipFStars.isChecked -> events.filter { it.type == "WatchEvent" || it.type == "ForkEvent" }
            else -> events
        }
        adapter.submit(filtered.take(300))
        b.empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
