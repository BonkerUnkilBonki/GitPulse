package com.codenamezeroseven.gitpulse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    suspend fun sync(token: String, owner: String): Result<Unit> {
        lastAttempt = System.currentTimeMillis()
        return mutex.withLock {
            runCatching {
                withContext(Dispatchers.IO) {
                    GitHubApi.ensureSyncRepo(token, owner)
                    val remote = GitHubApi.fetchFile(token, owner, FILE)
                    var localChanged = false
                    var remoteRaw: String? = null
                    if (remote != null) {
                        remoteRaw = remote.first
                        localChanged = TaskStore.mergeFromRemoteJson(remote.first)
                    }
                    val serialized = TaskStore.serializeFile()
                    if (localChanged || remoteRaw != serialized) {
                        GitHubApi.putFile(
                            token, owner, FILE,
                            "GitPulse: sync tasks", serialized, remote?.second
                        )
                    }
                    lastSync = System.currentTimeMillis()
                }
            }
        }
    }
}
