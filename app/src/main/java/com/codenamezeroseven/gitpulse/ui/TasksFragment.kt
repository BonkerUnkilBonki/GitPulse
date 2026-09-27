package com.codenamezeroseven.gitpulse.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codenamezeroseven.gitpulse.GitHubData
import com.codenamezeroseven.gitpulse.Prefs
import com.codenamezeroseven.gitpulse.TaskStore
import com.codenamezeroseven.gitpulse.TaskSync
import com.codenamezeroseven.gitpulse.databinding.FragmentTasksBinding
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TasksFragment : Fragment() {

    private var _b: FragmentTasksBinding? = null
    private val b get() = _b!!

    private var syncing = false

    private val adapter by lazy {
        TaskAdapter(
            onToggle = {
                TaskStore.toggle(it)
                render()
                pushAsync()
            },
            onDelete = {
                TaskStore.delete(it)
                render()
                pushAsync()
            }
        )
    }

    // Re-render the list the moment a background sync lands, so newly
    // pulled tasks appear without leaving the tab.
    private val syncListener: () -> Unit = {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (_b != null && isAdded) render()
        }
    }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentTasksBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        b.addBtn.setOnClickListener {
            val title = b.taskTitleInput.text?.toString()?.trim().orEmpty()
            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Give your task a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val keywords = b.taskKeywordsInput.text?.toString()?.trim().orEmpty()
            TaskStore.add(title, keywords)
            b.taskTitleInput.setText("")
            b.taskKeywordsInput.setText("")
            render()
            pushAsync()
        }

        TaskSync.addListener(syncListener)

        Anim.pressable(b.addBtn)

        if (Build.VERSION.SDK_INT >= 33 && !Prefs.notifAsked) {
            Prefs.notifAsked = true
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        TaskSync.removeListener(syncListener)
        _b = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden && _b != null) {
            render()
            Anim.staggerIn(b.syncInfo, b.summary, b.recycler, b.empty)
        }
    }

    private fun pushAsync() {
        val token = Prefs.token
        val owner = GitHubData.user?.login ?: Prefs.ownerLogin.takeIf { it.isNotBlank() } ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { TaskSync.sync(token, owner) }
        }
    }

    /** Pull remote tasks if the cache is stale, then re-render. */
    private fun maybePull() {
        if (syncing) return
        if (Prefs.token.isBlank()) return
        if (System.currentTimeMillis() - TaskSync.lastAttempt < 2 * 60_000L) return
        val owner = GitHubData.user?.login ?: Prefs.ownerLogin.takeIf { it.isNotBlank() } ?: return
        syncing = true
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { TaskSync.sync(Prefs.token, owner) }
            syncing = false
            if (_b != null) render()
        }
    }

    private fun render() {
        maybePull()

        // Catch keyword matches even if the data was refreshed elsewhere.
        val newly = TaskStore.processEvents(GitHubData.events)
        if (newly.isNotEmpty()) {
            Toast.makeText(
                requireContext(),
                "Task${if (newly.size == 1) "" else "s"} auto-completed from GitHub activity",
                Toast.LENGTH_SHORT
            ).show()
            pushAsync()
        }

        val tasks = TaskStore.list()
        adapter.submit(tasks)

        val open = tasks.count { !it.completed }
        val done = tasks.count { it.completed }
        b.summary.text = "$open open · $done completed"

        b.syncInfo.text = when {
            Prefs.token.isBlank() ->
                "Local only — connect GitHub to sync tasks across devices"
            TaskSync.lastError != null ->
                "Sync problem: ${TaskSync.lastError} — will retry on next refresh"
            TaskSync.lastSync > 0 -> {
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                    .format(java.util.Date(TaskSync.lastSync))
                "Synced with gitpulse-sync at $time"
            }
            else -> "Waiting for first sync…"
        }

        b.empty.visibility = if (tasks.isEmpty()) {
            Anim.breathe(b.empty)
            View.VISIBLE
        } else View.GONE
    }
}
