package com.codenamezeroseven.gitpulse

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic background check (every ~15 min, when online): syncs tasks,
 * auto-completes them and posts notifications for new GitHub activity
 * (commits, new repos, PRs, issues, releases, stars, forks).
 */
class ActivityWorker(ctx: Context, params: WorkerParameters) :
    CoroutineWorker(ctx, params) {

    private val notifyTypes = setOf(
        "PushEvent", "CreateEvent", "PullRequestEvent", "IssuesEvent",
        "ReleaseEvent", "WatchEvent", "ForkEvent", "PublicEvent"
    )

    override suspend fun doWork(): Result {
        val token = Prefs.token
        if (token.isBlank()) return Result.success()

        val u = runCatching { GitHubApi.fetchUser(token) }.getOrNull()
            ?: return Result.success()
        Prefs.ownerLogin = u.login

        // Keep tasks and goals in sync + auto-complete from fresh activity.
        runCatching { TaskSync.sync(token, u.login) }
        val ev = runCatching { GitHubApi.fetchEvents(token, u.login) }.getOrNull()
            ?: return Result.success()
        val completed = TaskStore.processEvents(ev)
        if (completed.isNotEmpty()) Notifier.notifyCompleted(completed)

        // GitHub activity notifications.
        if (Prefs.activityNotifs && ev.isNotEmpty()) {
            val lastId = Prefs.lastSeenEventId
            val maxId = ev.maxOf { it.id }
            if (lastId == 0L) {
                // First run: just record the baseline, don't spam old events.
                Prefs.lastSeenEventId = maxId
            } else if (maxId > lastId) {
                val fresh = ev
                    .filter { it.id > lastId && it.type in notifyTypes }
                    .filter { !it.repoName.endsWith("/gitpulse-sync") }
                    .sortedByDescending { it.id }
                    .take(5)
                Notifier.notifyActivity(fresh)
                Prefs.lastSeenEventId = maxId
            }
        }
        return Result.success()
    }
}
