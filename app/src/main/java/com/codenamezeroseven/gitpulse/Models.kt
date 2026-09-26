package com.codenamezeroseven.gitpulse

import org.json.JSONObject
import java.time.LocalDate

data class User(
    val login: String,
    val name: String?,
    val avatarUrl: String,
    val bio: String?,
    val followers: Int,
    val following: Int,
    val publicRepos: Int,
    val location: String?,
    val blog: String?,
    val htmlUrl: String
) {
    companion object {
        fun fromJson(o: JSONObject): User = User(
            login = o.optString("login"),
            name = if (o.isNull("name")) null else o.optString("name"),
            avatarUrl = o.optString("avatar_url"),
            bio = if (o.isNull("bio")) null else o.optString("bio"),
            followers = o.optInt("followers"),
            following = o.optInt("following"),
            publicRepos = o.optInt("public_repos"),
            location = if (o.isNull("location")) null else o.optString("location"),
            blog = if (o.isNull("blog")) null else o.optString("blog"),
            htmlUrl = o.optString("html_url")
        )
    }
}

data class Repo(
    val name: String,
    val fullName: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    val pushedAt: LocalDate?,
    val url: String,
    val isFork: Boolean
) {
    companion object {
        fun fromJson(o: JSONObject): Repo = Repo(
            name = o.optString("name"),
            fullName = o.optString("full_name"),
            description = if (o.isNull("description")) null else o.optString("description"),
            language = if (o.isNull("language")) null else o.optString("language"),
            stars = o.optInt("stargazers_count"),
            pushedAt = run {
                val s = o.optString("pushed_at")
                if (s.isNullOrBlank()) null
                else runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
            },
            url = o.optString("html_url"),
            isFork = o.optBoolean("fork")
        )
    }
}

data class GhEvent(
    val type: String,
    val repoName: String,
    val createdAt: LocalDate,
    val detail: String,
    val commits: Int
) {
    companion object {
        fun fromJson(o: JSONObject): GhEvent {
            val type = o.optString("type")
            val repo = o.optJSONObject("repo")?.optString("name") ?: ""
            val date = runCatching {
                LocalDate.parse(o.optString("created_at").take(10))
            }.getOrDefault(LocalDate.now())
            val payload = o.optJSONObject("payload") ?: JSONObject()
            var commits = 0
            val detail = when (type) {
                "PushEvent" -> {
                    commits = payload.optJSONArray("commits")?.length() ?: payload.optInt("size", 0)
                    "Pushed $commits commit" + if (commits == 1) "" else "s"
                }
                "PullRequestEvent" -> "Pull request " + payload.optString("action")
                "PullRequestReviewEvent" -> "Reviewed a pull request"
                "PullRequestReviewCommentEvent" -> "Commented on a review"
                "IssuesEvent" -> "Issue " + payload.optString("action")
                "IssueCommentEvent" -> "Commented on an issue"
                "CreateEvent" -> ("Created " + payload.optString("ref_type") + " " +
                        payload.optString("ref")).trim()
                "DeleteEvent" -> ("Deleted " + payload.optString("ref_type") + " " +
                        payload.optString("ref")).trim()
                "WatchEvent" -> "Starred repository"
                "ForkEvent" -> "Forked repository"
                "ReleaseEvent" -> "Published a release"
                "PublicEvent" -> "Made repository public"
                "MemberEvent" -> "Collaborator added"
                "GollumEvent" -> "Updated the wiki"
                else -> type.removeSuffix("Event")
            }
            return GhEvent(type, repo, date, detail, commits)
        }
    }
}
