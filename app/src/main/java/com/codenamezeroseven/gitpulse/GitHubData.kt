package com.codenamezeroseven.gitpulse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

object GitHubData {
    var user: User? = null
    var events: List<GhEvent> = emptyList()
    var repos: List<Repo> = emptyList()
    var dailyCommits: Map<LocalDate, Int> = emptyMap()
    var totalCommits: Int = -1
    var totalPRs: Int = -1
    var lastRefresh: Long = 0

    private val mutex = Mutex()

    val connected: Boolean get() = Prefs.token.isNotBlank()

    suspend fun refresh(force: Boolean = false) {
        val token = Prefs.token
        if (token.isBlank()) throw GitHubApi.ApiException("Not connected")
        mutex.withLock {
            // Throttle inside the lock so a queued duplicate refresh sees the
            // just-completed one and skips instead of re-fetching.
            if (!force && user != null &&
                System.currentTimeMillis() - lastRefresh < 5 * 60_000L
            ) return@withLock
            withContext(Dispatchers.IO) {
                val u = GitHubApi.fetchUser(token)
                Prefs.ownerLogin = u.login

                // Cross-device sync comes FIRST - nothing else can block it.
                runCatching { TaskSync.sync(token, u.login, notify = true) }

                val ev = GitHubApi.fetchEvents(token, u.login)

                // Auto-complete tasks whose keywords match recent GitHub activity
                val completedTasks = TaskStore.processEvents(ev)
                if (completedTasks.isNotEmpty()) {
                    Notifier.notifyCompleted(completedTasks)
                    // Push the auto-completions to the other devices.
                    runCatching { TaskSync.sync(token, u.login) }
                }

                val reps = GitHubApi.fetchRepos(token, u.login)

                // Preferred source: the real contribution calendar (matches the
                // GitHub profile graph, includes private contributions).
                // Fallback: events-derived daily counts (public events, ~90 days).
                var daily: Map<LocalDate, Int>
                var commitsTotal: Int
                val calendar = runCatching { GitHubApi.fetchContributionCalendar(token, u.login) }
                if (calendar.isSuccess) {
                    val (map, total) = calendar.getOrThrow()
                    daily = map
                    commitsTotal = total
                } else {
                    daily = StatsEngine.dailyCommits(ev)
                    commitsTotal = runCatching {
                        GitHubApi.searchTotal(token, "search/commits", "author:${u.login}")
                    }.getOrDefault(-1)
                }

                // Rate-limit-prone search: never abort the whole refresh for it.
                val prs = runCatching {
                    GitHubApi.searchTotal(token, "search/issues", "author:${u.login} type:pr")
                }.getOrDefault(-1)

                user = u
                events = ev
                repos = reps
                dailyCommits = daily
                totalCommits = commitsTotal
                totalPRs = prs
                lastRefresh = System.currentTimeMillis()
                Prefs.saveDailyCommits(daily)
                // Persist the snapshot so the app opens with data, not blank.
                DataCache.save()
            }
        }
    }

    fun cachedDailyCommits(): Map<LocalDate, Int> =
        if (dailyCommits.isNotEmpty()) dailyCommits else Prefs.dailyCommits()

    /** Restores the last snapshot from disk. Call once at app start. */
    fun loadCache(): Boolean = DataCache.load()

    fun clearLocal() {
        user = null
        events = emptyList()
        repos = emptyList()
        dailyCommits = emptyMap()
        totalCommits = -1
        totalPRs = -1
        lastRefresh = 0
        DataCache.clear()
    }
}
