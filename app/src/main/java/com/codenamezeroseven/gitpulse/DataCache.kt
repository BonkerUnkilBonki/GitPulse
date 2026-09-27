package com.codenamezeroseven.gitpulse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Persists the last successful data snapshot to disk so the app opens
 * instantly with real data instead of a blank screen, then refreshes and
 * syncs in the background.
 */
object DataCache {
    private const val FILE = "gitpulse-cache.json"
    lateinit var appContext: Context

    private fun JSONObject.strOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

    fun save() {
        if (!::appContext.isInitialized) return
        runCatching {
            val o = JSONObject()

            GitHubData.user?.let { u ->
                val uo = JSONObject()
                    .put("login", u.login)
                    .put("avatarUrl", u.avatarUrl)
                    .put("followers", u.followers)
                    .put("following", u.following)
                    .put("publicRepos", u.publicRepos)
                    .put("htmlUrl", u.htmlUrl)
                u.name?.let { uo.put("name", it) }
                u.bio?.let { uo.put("bio", it) }
                u.location?.let { uo.put("location", it) }
                u.blog?.let { uo.put("blog", it) }
                o.put("user", uo)
            }

            val ev = JSONArray()
            GitHubData.events.forEach { e ->
                val eo = JSONObject()
                    .put("type", e.type).put("repo", e.repoName)
                    .put("day", e.createdAtEpochDay).put("detail", e.detail)
                    .put("commits", e.commits)
                    .put("commitList", JSONArray().apply {
                        e.commitList.forEach { c ->
                            put(JSONObject().put("sha", c.sha).put("message", c.message))
                        }
                    })
                e.title?.let { eo.put("title", it) }
                e.action?.let { eo.put("action", it) }
                e.ref?.let { eo.put("ref", it) }
                e.tag?.let { eo.put("tag", it) }
                e.beforeSha?.let { eo.put("before", it) }
                e.headSha?.let { eo.put("head", it) }
                eo.put("id", e.id)
                ev.put(eo)
            }
            o.put("events", ev)

            val rs = JSONArray()
            GitHubData.repos.forEach { r ->
                val ro = JSONObject()
                    .put("name", r.name).put("fullName", r.fullName)
                    .put("stars", r.stars).put("pushedDay", r.pushedAtEpochDay)
                    .put("url", r.url).put("fork", r.isFork)
                r.description?.let { ro.put("description", it) }
                r.language?.let { ro.put("language", it) }
                rs.put(ro)
            }
            o.put("repos", rs)

            val daily = JSONObject()
            GitHubData.dailyCommits.forEach { (d, c) -> daily.put(d.toString(), c) }
            o.put("daily", daily)
            o.put("totalCommits", GitHubData.totalCommits)
            o.put("totalPRs", GitHubData.totalPRs)
            o.put("lastRefresh", GitHubData.lastRefresh)

            File(appContext.filesDir, FILE).writeText(o.toString())
        }
    }

    fun load(): Boolean = runCatching {
        val f = File(appContext.filesDir, FILE)
        if (!f.exists()) return false
        val o = JSONObject(f.readText())

        if (o.has("user")) {
            val u = o.getJSONObject("user")
            GitHubData.user = User(
                login = u.optString("login"),
                name = u.strOrNull("name"),
                avatarUrl = u.optString("avatarUrl"),
                bio = u.strOrNull("bio"),
                followers = u.optInt("followers"),
                following = u.optInt("following"),
                publicRepos = u.optInt("publicRepos"),
                location = u.strOrNull("location"),
                blog = u.strOrNull("blog"),
                htmlUrl = u.optString("htmlUrl")
            )
        }

        val evArr = o.optJSONArray("events")
        if (evArr != null) {
            val evs = mutableListOf<GhEvent>()
            for (i in 0 until evArr.length()) {
                val e = evArr.getJSONObject(i)
                val cl = e.optJSONArray("commitList")
                val commits = mutableListOf<CommitInfo>()
                if (cl != null) for (j in 0 until cl.length()) {
                    val c = cl.getJSONObject(j)
                    commits += CommitInfo(c.optString("sha"), c.optString("message"))
                }
                evs += GhEvent(
                    type = e.optString("type"),
                    repoName = e.optString("repo"),
                    createdAtEpochDay = e.optLong("day"),
                    detail = e.optString("detail"),
                    commits = e.optInt("commits"),
                    commitList = commits,
                    title = e.strOrNull("title"),
                    action = e.strOrNull("action"),
                    ref = e.strOrNull("ref"),
                    tag = e.strOrNull("tag"),
                    beforeSha = e.strOrNull("before"),
                    headSha = e.strOrNull("head"),
                    id = e.optLong("id", 0L)
                )
            }
            GitHubData.events = evs
        }

        val rsArr = o.optJSONArray("repos")
        if (rsArr != null) {
            val rs = mutableListOf<Repo>()
            for (i in 0 until rsArr.length()) {
                val r = rsArr.getJSONObject(i)
                rs += Repo(
                    name = r.optString("name"),
                    fullName = r.optString("fullName"),
                    description = r.strOrNull("description"),
                    language = r.strOrNull("language"),
                    stars = r.optInt("stars"),
                    pushedAtEpochDay = r.optLong("pushedDay"),
                    url = r.optString("url"),
                    isFork = r.optBoolean("fork")
                )
            }
            GitHubData.repos = rs
        }

        val daily = o.optJSONObject("daily")
        if (daily != null) {
            val map = mutableMapOf<LocalDate, Int>()
            val keys = daily.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                runCatching { LocalDate.parse(k) }.getOrNull()?.let {
                    map[it] = daily.optInt(k)
                }
            }
            GitHubData.dailyCommits = map
        }

        GitHubData.totalCommits = o.optInt("totalCommits", -1)
        GitHubData.totalPRs = o.optInt("totalPRs", -1)
        GitHubData.lastRefresh = o.optLong("lastRefresh", 0)
        true
    }.getOrDefault(false)

    fun clear() {
        if (!::appContext.isInitialized) return
        runCatching { File(appContext.filesDir, FILE).delete() }
    }
}
