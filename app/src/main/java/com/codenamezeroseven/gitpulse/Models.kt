package com.codenamezeroseven.gitpulse

import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import java.time.LocalDate

@Parcelize
data class CommitInfo(val sha: String, val message: String) : android.os.Parcelable

@Parcelize
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
) : android.os.Parcelable {
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

@Parcelize
data class Repo(
    val name: String,
    val fullName: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    val pushedAtEpochDay: Long,
    val url: String,
    val isFork: Boolean
) : android.os.Parcelable {
    val pushedAt: LocalDate?
        get() = if (pushedAtEpochDay == -1L) null else LocalDate.ofEpochDay(pushedAtEpochDay)

    companion object {
        fun fromJson(o: JSONObject): Repo = Repo(
            name = o.optString("name"),
            fullName = o.optString("full_name"),
            description = if (o.isNull("description")) null else o.optString("description"),
            language = if (o.isNull("language")) null else o.optString("language"),
            stars = o.optInt("stargazers_count"),
            pushedAtEpochDay = run {
                val s = o.optString("pushed_at")
                if (s.isNullOrBlank()) -1L
                else runCatching { LocalDate.parse(s.take(10)).toEpochDay() }.getOrDefault(-1L)
            },
            url = o.optString("html_url"),
            isFork = o.optBoolean("fork")
        )
    }
}

@Parcelize
data class GhEvent(
    val type: String,
    val repoName: String,
    val createdAtEpochDay: Long,
    val detail: String,
    val commits: Int,
    val commitList: List<CommitInfo> = emptyList(),
    val title: String? = null,
    val action: String? = null,
    val ref: String? = null,
    val tag: String? = null
) : android.os.Parcelable {
    val createdAt: LocalDate
        get() = LocalDate.ofEpochDay(createdAtEpochDay)

    companion object {
        fun fromJson(o: JSONObject): GhEvent {
            val type = o.optString("type")
            val repo = o.optJSONObject("repo")?.optString("name") ?: ""
            val date = runCatching {
                LocalDate.parse(o.optString("created_at").take(10))
            }.getOrDefault(LocalDate.now()).toEpochDay()
            val payload = o.optJSONObject("payload") ?: JSONObject()

            var commits = 0
            var commitList: List<CommitInfo> = emptyList()
            var title: String? = null
            var action: String? = null
            var ref: String? = null
            var tag: String? = null

            val detail = when (type) {
                "PushEvent" -> {
                    commitList = payload.optJSONArray("commits")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            val c = arr.optJSONObject(i) ?: return@mapNotNull null
                            CommitInfo(c.optString("sha"), c.optString("message"))
                        }
                    } ?: emptyList()
                    commits = if (commitList.isNotEmpty()) commitList.size else payload.optInt("size", 0)
                    "Pushed $commits commit" + if (commits == 1) "" else "s"
                }
                "PullRequestEvent" -> {
                    action = payload.optString("action")
                    title = payload.optJSONObject("pull_request")?.optString("title")
                    "Pull request " + action
                }
                "PullRequestReviewEvent" -> {
                    title = payload.optJSONObject("pull_request")?.optString("title")
                    "Reviewed a pull request"
                }
                "PullRequestReviewCommentEvent" -> {
                    title = payload.optJSONObject("pull_request")?.optString("title")
                    "Commented on a review"
                }
                "IssuesEvent" -> {
                    action = payload.optString("action")
                    title = payload.optJSONObject("issue")?.optString("title")
                    "Issue " + action
                }
                "IssueCommentEvent" -> {
                    title = payload.optJSONObject("issue")?.optString("title")
                    "Commented on an issue"
                }
                "CreateEvent" -> {
                    val rt = payload.optString("ref_type")
                    ref = payload.optString("ref")
                    "Created $rt" + (if (!ref.isNullOrBlank()) " $ref" else "")
                }
                "DeleteEvent" -> {
                    val rt = payload.optString("ref_type")
                    ref = payload.optString("ref")
                    "Deleted $rt" + (if (!ref.isNullOrBlank()) " $ref" else "")
                }
                "WatchEvent" -> "Starred repository"
                "ForkEvent" -> "Forked repository"
                "ReleaseEvent" -> {
                    val rel = payload.optJSONObject("release")
                    tag = rel?.optString("tag_name")
                    title = rel?.optString("name")
                    "Published a release"
                }
                "PublicEvent" -> "Made repository public"
                "MemberEvent" -> "Collaborator added"
                "GollumEvent" -> "Updated the wiki"
                else -> type.removeSuffix("Event")
            }
            return GhEvent(type, repo, date, detail, commits, commitList, title, action, ref, tag)
        }
    }
}
