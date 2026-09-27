package com.codenamezeroseven.gitpulse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cross-device task sync. Tasks live in a private repo (gitpulse-sync) as
 * tasks.json. Every sync: pull + merge (last-write-wins per task,
 * deletions propagate via tombstones), then push the merged state if it
 * differs from the remote file.
 */
object TaskSync {
    private const val FILE = "tasks.json"
    private val mutex = Mutex()

    /** Last successful sync (epoch millis). */
    var lastSync: Long = 0

    /** Last sync attempt (success or failure) - used to throttle retries. */
    var lastAttempt: Long = 0

    /** Reason of the last failed sync, null when the last sync succeeded. */
    var lastError: String? = null

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** Called after every sync attempt (on a background thread). */
    fun addListener(l: () -> Unit) { listeners.add(l) }

    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    suspend fun sync(token: String, owner: String, notify: Boolean = false): Result<Unit> {
        lastAttempt = System.currentTimeMillis()
        return mutex.withLock {
            runCatching {
                withContext(Dispatchers.IO) {
                    GitHubApi.ensureSyncRepo(token, owner)
                    val remote = GitHubApi.fetchFile(token, owner, FILE)
                    var res: MergeResult? = null
                    var remoteRaw: String? = null
                    var goalsApplied = false
                    if (remote != null) {
                        remoteRaw = remote.first
                        val (tasks, deleted, settings) = TaskStore.parseFile(remote.first)
                        res = TaskStore.mergeFromRemote(tasks, deleted)
                        // Goal settings: newest wins.
                        if (settings != null && settings.updatedAt > Prefs.goalsUpdatedAt) {
                            Prefs.dailyGoal = settings.dailyGoal
                            Prefs.weeklyGoal = settings.weeklyGoal
                            Prefs.goalsUpdatedAt = settings.updatedAt
                            goalsApplied = true
                        }
                    }
                    val serialized = TaskStore.serializeFile()
                    if (res?.changed == true || goalsApplied || remoteRaw != serialized) {
                        GitHubApi.putFile(
                            token, owner, FILE,
                            "GitPulse: sync tasks", serialized, remote?.second
                        )
                    }
                    lastSync = System.currentTimeMillis()
                    lastError = null
                    if (notify) {
                        val r = res
                        val text = if ((r != null && r.added + r.completed + r.removed > 0) || goalsApplied) {
                            listOfNotNull(
                                if (r != null && r.added > 0) "${r.added} task${if (r.added == 1) "" else "s"} added" else null,
                                if (r != null && r.completed > 0) "${r.completed} completed" else null,
                                if (r != null && r.removed > 0) "${r.removed} removed" else null,
                                if (goalsApplied) "goals updated" else null
                            ).joinToString(", ")
                        } else {
                            "Tasks up to date"
                        }
                        Notifier.notifySync(text)
                    }
                }
            }
        }.onFailure { e ->
            lastError = e.message ?: "sync failed"
            if (notify) Notifier.notifySyncProblem(lastError ?: "sync failed")
        }.also {
            runCatching { WidgetHelper.updateAll(DataCache.appContext) }
            listeners.forEach { it() }
        }
    }
}
