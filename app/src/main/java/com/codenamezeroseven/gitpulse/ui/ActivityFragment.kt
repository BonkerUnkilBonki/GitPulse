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
        b.connectBtn.setOnClickListener { (activity as? MainActivity)?.openProfile() }
        if (Prefs.token.isBlank()) showDisconnected() else load(false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden && _b != null && Prefs.token.isNotBlank() && b.disconnected.visibility == View.VISIBLE) {
            load(false)
        }
    }

    private fun showDisconnected() {
        b.swipe.isRefreshing = false
        b.recycler.visibility = View.GONE
        b.disconnected.visibility = View.VISIBLE
    }

    private fun load(force: Boolean) {
        if (Prefs.token.isBlank()) {
            showDisconnected()
            return
        }
        b.disconnected.visibility = View.GONE
        b.recycler.visibility = View.VISIBLE
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
        adapter.submit(events.take(200))
        b.empty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
    }
}
