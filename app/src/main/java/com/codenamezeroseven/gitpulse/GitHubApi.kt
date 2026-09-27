package com.codenamezeroseven.gitpulse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object GitHubApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    const val BASE = "https://api.github.com"

    class ApiException(message: String) : Exception(message)

    private const val SYNC_REPO = "gitpulse-sync"

    private suspend fun get(token: String, path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(BASE + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw ApiException(
                    when (resp.code) {
                        401 -> "Invalid or expired token"
                        403 -> "GitHub rate limit reached. Try again in a little while."
                        404 -> "Not found"
                        else -> "GitHub error ${resp.code}: ${msg ?: ""}".trim()
                    }
                )
            }
            body
        }
    }

    suspend fun fetchUser(token: String): User =
        User.fromJson(JSONObject(get(token, "/user")))

    suspend fun fetchEvents(token: String, login: String): List<GhEvent> {
        val out = mutableListOf<GhEvent>()
        for (page in 1..3) {
            val arr = JSONArray(get(token, "/users/$login/events?per_page=100&page=$page"))
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) out += GhEvent.fromJson(arr.getJSONObject(i))
        }
        enrichPushCounts(token, out)
        return out
    }

    /**
     * The user-events endpoint no longer returns commit counts, so for the most
     * recent push events we ask the compare API how many commits each push
     * actually contains (ahead_by of before...head).
     */
    private suspend fun enrichPushCounts(token: String, out: MutableList<GhEvent>) {
        var seen = 0
        coroutineScope {
            val jobs = out.mapIndexed { i, e ->
                if (e.type == "PushEvent" && seen++ < 25) {
                    async {
                        runCatching { compareAhead(token, e) }.getOrNull()?.let { r -> Triple(i, r.first, r.second) }
                    }
                } else null
            }.filterNotNull()
            for (result in jobs.awaitAll().filterNotNull()) {
                val (i, count, list) = result
                if (count > 0) {
                    out[i] = out[i].copy(
                        commits = count,
                        commitList = if (list.isNotEmpty()) list else out[i].commitList,
                        detail = "Pushed $count commit" + if (count == 1) "" else "s"
                    )
                }
            }
        }
    }

    /** Returns (commits in push, list of commit sha+message) or null. */
    private suspend fun compareAhead(token: String, e: GhEvent): Pair<Int, List<CommitInfo>>? {
        val before = e.beforeSha ?: return null
        val head = e.headSha ?: return null
        if (before.isBlank() || head.isBlank() || before.all { it == '0' }) return null
        val o = JSONObject(get(token, "/repos/${e.repoName}/compare/$before...$head"))
        val ahead = o.optInt("ahead_by", -1)
        if (ahead < 0) return null
        val list = mutableListOf<CommitInfo>()
        val arr = o.optJSONArray("commits")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val msg = c.optJSONObject("commit")?.optString("message") ?: ""
                list += CommitInfo(c.optString("sha"), msg)
            }
        }
        return Pair(ahead, list)
    }

    suspend fun fetchRepos(token: String, login: String): List<Repo> {
        val out = mutableListOf<Repo>()
        for (page in 1..2) {
            val arr = JSONArray(get(token, "/users/$login/repos?per_page=100&sort=pushed&page=$page"))
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) out += Repo.fromJson(arr.getJSONObject(i))
        }
        return out
    }

    suspend fun searchTotal(token: String, endpoint: String, query: String): Int =
        runCatching {
            val q = query.replace(" ", "%20")
            JSONObject(get(token, "/$endpoint?q=$q")).optInt("total_count", 0)
        }.getOrDefault(0)

    private fun buildRequest(token: String, path: String, method: String, body: String?): Request {
        val b = Request.Builder()
            .url(BASE + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (method == "GET") {
            b.get()
        } else {
            b.method(method, (body ?: "{}").toRequestBody("application/json".toMediaType()))
        }
        return b.build()
    }

    private suspend fun request(
        token: String, path: String, method: String = "GET", body: String? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        client.newCall(buildRequest(token, path, method, body)).execute().use { resp ->
            resp.code to (resp.body?.string() ?: "")
        }
    }

    /** Creates the private sync repo on first use. */
    suspend fun ensureSyncRepo(token: String, owner: String) {
        val (code, _) = request(token, "/repos/$owner/$SYNC_REPO")
        if (code == 404) {
            val body = JSONObject()
                .put("name", SYNC_REPO)
                .put("private", true)
                .put("description", "GitPulse task sync (created automatically)")
                .toString()
            val (c, t) = request(token, "/user/repos", "POST", body)
            if (c !in 200..299) {
                val msg = runCatching { JSONObject(t).optString("message") }.getOrDefault("")
                if (c == 403) {
                    // Fine-grained tokens cannot create repositories. The user
                    // can create the repo once on the web and we take over.
                    throw ApiException(
                        "Your token can't create repositories. Open github.com/new and create a PRIVATE repo named gitpulse-sync (no README needed), then make sure your token has read+write access to it. Pull-to-refresh afterwards."
                    )
                }
                throw ApiException("Could not create sync repo ($c): $msg")
            }
        } else if (code !in 200..299) {
            throw ApiException("Sync repo check failed ($code)")
        }
    }

    /** Returns decoded file content + git blob sha, or null when absent. */
    suspend fun fetchFile(token: String, owner: String, path: String): Pair<String, String>? {
        val (code, text) = request(token, "/repos/$owner/$SYNC_REPO/contents/$path")
        if (code == 404) return null
        if (code !in 200..299) throw ApiException("File fetch failed ($code)")
        val o = JSONObject(text)
        val sha = o.optString("sha")
        val content = o.optString("content", "").replace("\n", "")
        val decoded = runCatching {
            String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
        }.getOrDefault("")
        return decoded to sha
    }

    suspend fun putFile(
        token: String, owner: String, path: String, message: String, content: String, sha: String?
    ) {
        val body = JSONObject()
            .put("message", message)
            .put("content", android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP))
        if (sha != null) body.put("sha", sha)
        val (code, text) = request(token, "/repos/$owner/$SYNC_REPO/contents/$path", "PUT", body.toString())
        if (code !in 200..299) {
            throw ApiException("Sync upload failed ($code): " + runCatching { JSONObject(text).optString("message") }.getOrDefault(""))
        }
    }

    /** Fetch a single repo by full name, or null. */
    suspend fun fetchRepo(token: String, fullName: String): Repo? {
        val (code, text) = request(token, "/repos/$fullName")
        if (code != 200) return null
        return runCatching { Repo.fromJson(JSONObject(text)) }.getOrNull()
    }

    /** Recent commits of any repo: (sha, message, date). Null when not loadable. */
    suspend fun fetchRepoCommits(
        token: String, repoFullName: String, limit: Int = 15
    ): List<Triple<String, String, LocalDate>>? {
        val (code, text) = request(token, "/repos/$repoFullName/commits?per_page=$limit")
        if (code != 200) return null
        return runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val sha = o.optString("sha").take(7)
                val c = o.optJSONObject("commit") ?: JSONObject()
                val msg = c.optString("message").lineSequence().firstOrNull() ?: ""
                val d = c.optJSONObject("committer")?.optString("date")?.take(10)
                val day = runCatching { LocalDate.parse(d) }.getOrNull() ?: LocalDate.now()
                Triple(sha, msg, day)
            }
        }.getOrNull()
    }

    /** Decoded README of a repo, or null when there is none. */
    suspend fun fetchReadme(token: String, repoFullName: String): String? {
        val (code, text) = request(token, "/repos/$repoFullName/readme")
        if (code != 200) return null
        return runCatching {
            val o = JSONObject(text)
            val content = o.optString("content").replace("\n", "")
            String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
        }.getOrNull()
    }

    /** Day -> commit count for the private sync repo (up to 200 commits). */
    suspend fun fetchSyncRepoCommitDays(token: String, owner: String): Map<LocalDate, Int> {
        val out = mutableMapOf<LocalDate, Int>()
        var page = 1
        while (page <= 2) {
            val (code, text) = request(token, "/repos/$owner/gitpulse-sync/commits?per_page=100&page=$page")
            if (code != 200) break
            val arr = runCatching { JSONArray(text) }.getOrNull() ?: break
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val d = o.optJSONObject("commit")?.optJSONObject("committer")?.optString("date")?.take(10)
                val day = runCatching { LocalDate.parse(d) }.getOrNull() ?: continue
                out[day] = (out[day] ?: 0) + 1
            }
            if (arr.length() < 100) break
            page++
        }
        return out
    }

    suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        }.getOrNull()
    }

    /**
     * Fetches the real contribution calendar (same data as the GitHub profile
     * graph, private contributions included when the token allows) covering
     * roughly the last 12 months. Returns (day -> count) plus the total.
     */
    suspend fun fetchContributionCalendar(token: String, login: String): Pair<Map<LocalDate, Int>, Int> =
        withContext(Dispatchers.IO) {
            val query = "query { user(login: \"$login\") { contributionsCollection " +
                "{ contributionCalendar { totalContributions weeks { contributionDays " +
                "{ date contributionCount } } } } } }"
            val payload = JSONObject().put("query", query).toString()
            val req = Request.Builder()
                .url("https://api.github.com/graphql")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw ApiException("GraphQL error ${resp.code}")
                val root = JSONObject(text)
                if (root.has("errors")) {
                    throw ApiException("GraphQL: " + root.optJSONArray("errors")?.optJSONObject(0)?.optString("message").orEmpty())
                }
                val calendar = root.getJSONObject("data").getJSONObject("user")
                    .getJSONObject("contributionsCollection").getJSONObject("contributionCalendar")
                val total = calendar.optInt("totalContributions", -1)
                val weeks = calendar.getJSONArray("weeks")
                val map = mutableMapOf<LocalDate, Int>()
                for (w in 0 until weeks.length()) {
                    val days = weeks.getJSONObject(w).getJSONArray("contributionDays")
                    for (d in 0 until days.length()) {
                        val day = days.getJSONObject(d)
                        val date = runCatching { LocalDate.parse(day.optString("date")) }.getOrNull() ?: continue
                        val count = day.optInt("contributionCount", 0)
                        if (count > 0) map[date] = count
                    }
                }
                map to total
            }
        }
}
