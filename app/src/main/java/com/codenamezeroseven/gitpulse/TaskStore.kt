package com.codenamezeroseven.gitpulse

import android.content.Context
import android.content.SharedPreferences
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@Parcelize
data class Task(
    val id: Long,
    val title: String,
    val keywords: String,
    val createdAtEpochDay: Long,
    val completed: Boolean,
    val completedAtEpochDay: Long,
    val completionSource: String,
    val updatedAt: Long
) : android.os.Parcelable

object TaskStore {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("gitpulse_tasks", Context.MODE_PRIVATE)
    }

    fun list(): MutableList<Task> {
        val raw = sp.getString("tasks", null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            val out = mutableListOf<Task>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += taskFromJson(o)
            }
            out
        }.getOrDefault(mutableListOf())
    }

    private fun taskFromJson(o: JSONObject) = Task(
        id = o.optLong("id"),
        title = o.optString("title"),
        keywords = o.optString("keywords"),
        createdAtEpochDay = o.optLong("created"),
        completed = o.optBoolean("done"),
        completedAtEpochDay = o.optLong("doneAt"),
        completionSource = o.optString("source"),
        updatedAt = o.optLong("updated", 0L)
    )

    private fun taskToJson(t: Task) = JSONObject()
        .put("id", t.id).put("title", t.title).put("keywords", t.keywords)
        .put("created", t.createdAtEpochDay).put("done", t.completed)
        .put("doneAt", t.completedAtEpochDay).put("source", t.completionSource)
        .put("updated", t.updatedAt)

    fun save(tasks: List<Task>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(taskToJson(it)) }
        sp.edit().putString("tasks", arr.toString()).apply()
    }

    fun deletedIds(): MutableSet<Long> {
        val raw = sp.getString("deletedIds", null) ?: return mutableSetOf()
        return runCatching {
            val arr = JSONArray(raw)
            val out = mutableSetOf<Long>()
            for (i in 0 until arr.length()) out += arr.optLong(i)
            out
        }.getOrDefault(mutableSetOf())
    }

    fun saveDeletedIds(ids: Set<Long>) {
        sp.edit().putString("deletedIds", JSONArray(ids).toString()).apply()
    }

    fun add(title: String, keywords: String): Task {
        val now = System.currentTimeMillis()
        val t = Task(
            id = now,
            title = title.trim(),
            keywords = keywords.trim(),
            createdAtEpochDay = LocalDate.now().toEpochDay(),
            completed = false,
            completedAtEpochDay = -1,
            completionSource = "",
            updatedAt = now
        )
        val l = list()
        l.add(0, t)
        save(l)
        return t
    }

    fun toggle(id: Long) {
        val l = list()
        val i = l.indexOfFirst { it.id == id }
        if (i >= 0) {
            val t = l[i]
            l[i] = if (t.completed)
                t.copy(completed = false, completedAtEpochDay = -1, completionSource = "",
                    updatedAt = System.currentTimeMillis())
            else
                t.copy(
                    completed = true,
                    completedAtEpochDay = LocalDate.now().toEpochDay(),
                    completionSource = "manual",
                    updatedAt = System.currentTimeMillis()
                )
            save(l)
        }
    }

    fun delete(id: Long) {
        save(list().filter { it.id != id })
        val d = deletedIds()
        d.add(id)
        saveDeletedIds(d)
    }

    // Event types that can auto-complete a task: pushes (commits, readme,
    // deploy in messages), branch/repo creation, releases, PRs, issues.
    private val matchableTypes = setOf(
        "PushEvent", "CreateEvent", "ReleaseEvent", "PullRequestEvent", "IssuesEvent"
    )

    /**
     * Auto-completes open tasks whose keywords appear in GitHub activity that
     * happened after the task was created. Returns newly completed tasks.
     */
    fun processEvents(events: List<GhEvent>): List<Task> {
        if (events.isEmpty()) return emptyList()
        val l = list()
        val newly = mutableListOf<Task>()
        var changed = false
        for (i in l.indices) {
            val t = l[i]
            if (t.completed) continue
            val kws = t.keywords.split(",")
                .map { it.trim().lowercase() }
                .filter { it.length >= 2 }
            if (kws.isEmpty()) continue

            for (e in events) {
                if (e.type !in matchableTypes) continue
                if (e.createdAtEpochDay < t.createdAtEpochDay) continue
                val hay = (
                    e.repoName + " " + e.detail + " " + (e.title ?: "") + " " +
                        (e.ref ?: "") + " " + (e.tag ?: "") + " " +
                        e.commitList.joinToString(" ") { it.message }
                    ).lowercase()
                val hit = kws.firstOrNull { hay.contains(it) } ?: continue
                val updated = t.copy(
                    completed = true,
                    completedAtEpochDay = LocalDate.now().toEpochDay(),
                    completionSource = "GitHub: ${e.repoName} (${hit})",
                    updatedAt = System.currentTimeMillis()
                )
                l[i] = updated
                newly += updated
                changed = true
                break
            }
        }
        if (changed) save(l)
        return newly
    }

    // ---------- Cross-device sync (merge + file serialization) ----------

    fun serializeFile(): String {
        val o = JSONObject()
        val arr = JSONArray()
        list().forEach { arr.put(taskToJson(it)) }
        o.put("tasks", arr)
        o.put("deleted", JSONArray(deletedIds()))
        o.put("settings", JSONObject()
            .put("dailyGoal", Prefs.dailyGoal)
            .put("weeklyGoal", Prefs.weeklyGoal)
            .put("goalsUpdatedAt", Prefs.goalsUpdatedAt))
        return o.toString()
    }

    /** Parse the sync file: tasks, tombstones and synced goal settings. */
    fun parseFile(raw: String): Triple<List<Task>, Set<Long>, SyncSettings?> {
        return runCatching {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("tasks") ?: JSONArray()
            val tasks = mutableListOf<Task>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { tasks += taskFromJson(it) }
            }
            val deleted = mutableSetOf<Long>()
            val d = o.optJSONArray("deleted")
            if (d != null) for (i in 0 until d.length()) deleted += d.optLong(i)
            val so = o.optJSONObject("settings")
            val settings = if (so != null) SyncSettings(
                dailyGoal = so.optInt("dailyGoal"),
                weeklyGoal = so.optInt("weeklyGoal"),
                updatedAt = so.optLong("goalsUpdatedAt")
            ) else null
            Triple(tasks, deleted, settings)
        }.getOrNull() ?: Triple(emptyList(), emptySet(), null)
    }

    /** Last-write-wins merge of remote state into local. */
    fun mergeFromRemote(remoteTasks: List<Task>, remoteDeleted: Set<Long>): MergeResult {
        val local = list()
        val localDeleted = deletedIds()
        var added = 0
        var completed = 0
        var removed = 0
        var changed = false
        val byId = local.associateBy { it.id }.toMutableMap()

        // Remote deletions win locally (unless re-added later with a newer id).
        for (id in remoteDeleted) {
            if (byId.remove(id) != null) {
                removed++
                changed = true
            }
        }
        // Remote tasks: newest updatedAt wins.
        for (rt in remoteTasks) {
            if (rt.id in localDeleted) continue
            val lt = byId[rt.id]
            if (lt == null) {
                byId[rt.id] = rt
                added++
                changed = true
            } else if (rt.updatedAt > lt.updatedAt) {
                if (rt.completed && !lt.completed) completed++
                byId[rt.id] = rt
                changed = true
            } else if (lt.updatedAt > rt.updatedAt) {
                changed = true // local is newer; push will propagate it
            }
        }
        if (changed) {
            save(byId.values.sortedByDescending { it.createdAtEpochDay })
        }
        val union = localDeleted + remoteDeleted
        if (union != localDeleted) {
            saveDeletedIds(union)
            changed = true
        }
        return MergeResult(changed, added, completed, removed)
    }
}

/** Goal settings carried in the sync file for cross-device goal sync. */
data class SyncSettings(val dailyGoal: Int, val weeklyGoal: Int, val updatedAt: Long)

data class MergeResult(
    val changed: Boolean,
    val added: Int,
    val completed: Int,
    val removed: Int
)
