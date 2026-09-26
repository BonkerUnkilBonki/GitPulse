package com.codenamezeroseven.gitpulse

import kotlinx.coroutines.Dispatchers
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
        return out
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
            val query = """query { user(login: \"$login\") { contributionsCollection { contributionCalendar { totalContributions weeks { contributionDays { date contributionCount } } } } } }"""
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
