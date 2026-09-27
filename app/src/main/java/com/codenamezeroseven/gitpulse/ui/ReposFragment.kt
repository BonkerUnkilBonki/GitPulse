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
import com.codenamezeroseven.gitpulse.databinding.FragmentReposBinding
import kotlinx.coroutines.launch

class ReposFragment : Fragment() {

    private var _b: FragmentReposBinding? = null
    private val b get() = _b!!
    private val adapter = RepoAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentReposBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.chipForks.setOnCheckedChangeListener { _, _ -> render() }
        b.chipSortRecent.setOnCheckedChangeListener { _, _ -> render() }
        b.chipSortStars.setOnCheckedChangeListener { _, _ -> render() }
        b.chipSortName.setOnCheckedChangeListener { _, _ -> render() }
        b.swipe.setOnChildScrollUpCallback { _, _ -> b.recycler.canScrollVertically(-1) }
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
                    if (e is kotlinx.coroutines.CancellationException) return@launch
                    Toast.makeText(requireContext(), e.message ?: "Failed to load", Toast.LENGTH_LONG).show()
                    render()
                }
        }
    }

    private fun render() {
        var list = GitHubData.repos
        if (b.chipForks.isChecked) list = list.filter { !it.isFork }
        list = when {
            b.chipSortStars.isChecked -> list.sortedByDescending { it.stars }
            b.chipSortName.isChecked -> list.sortedBy { it.name.lowercase() }
            else -> list.sortedByDescending { it.pushedAt }
        }
        adapter.submit(list)
        b.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}
