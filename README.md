# GitPulse — GitHub Productivity & Goal Tracker

A Material 3 (Material You) Android app that links to your GitHub account and
tracks your productivity: daily commits, streaks, weekly charts, contribution
heatmap, recent activity, repositories and goal tracking.

## App features
- **Dashboard** — today's commits vs goal (animated progress ring), streak chip,
  repos / stars / followers / PRs stat cards, 7-day bar chart with goal line,
  26-week contribution heatmap (tap any day for details), records.
- **Activity** — your last ~90 days of GitHub events with a 90-day summary card
  (commits, PRs, issues, stars given) and filter chips (All / Commits / PRs /
  Issues / Stars). Each event shows a commit message or PR/issue title preview.
  Tap an event to open an in-app detail sheet with the full commit list
  (SHA + message), PR/issue titles, actions, tags and references — plus an
  optional "Open on GitHub" button.
- **Repo details** — tapping a repository in the Repos tab opens an in-app
  detail sheet (description, language, stars, last push, fork status) instead
  of leaving the app.
- **Repos** — your repositories with language color dots, star counts and
  last-pushed time. Filter forks, sort by recent / stars / name.
- **Goals** — set a daily and weekly commit goal with steppers, weekly progress
  ring, 5-week goal history heatmap, current / best streak, active days,
  all-time commits.
- **Tasks** — a to-do list with GitHub superpowers. Add a task with optional
  keywords (e.g. "deploy, readme, login"). Tasks complete manually via the
  checkbox, or automatically when your GitHub commits, branches, releases or
  PRs mention those keywords — and you get a system notification when it
  happens. Keyword matching looks at commit messages, repo names, branch/tag
  names, PR and issue titles, and only counts activity after the task was
  created.
- **Task sync across devices** — tasks are stored as `tasks.json` in a private
  repo `gitpulse-sync` that GitPulse creates automatically on first sync.
  Every refresh pulls and merges (newest change wins per task; deletions
  propagate via tombstones), then pushes the merged state if anything
  differed. Works with any device signed into the same GitHub account.
- **Offline cache** — your profile, activity, repos and commit calendar are
  saved to disk after every sync, so the app opens instantly with the last
  synced data instead of a blank screen while it refreshes.
- **Automatic sync** — every app open and pull-to-refresh syncs in the
  background (no buttons), and a low-priority notification confirms what
  synced: tasks added, completed, removed, or up to date.
- **Profile** — connect with a GitHub personal access token, avatar, bio,
  follower stats, refresh, sign out.

## Connect your account
1. On GitHub: Settings -> Developer settings -> Personal access tokens -> Tokens (classic)
2. Generate a new token with the `repo` and `read:user` scopes.
   (A fine-grained token with "read-only" account + repository access also works.)
3. Open GitPulse -> Profile tab -> paste the token -> Connect.

The token is stored only on your device and is sent only to api.github.com.

## Notes
- Daily commit counts come from the GitHub GraphQL contribution calendar —
  the same data as your profile graph, including private contributions when
  the token allows — with an Events-API fallback. Streaks and the heatmap
  are computed from that calendar. Per-push commit counts in the Activity
  feed come from the GitHub compare API (GitHub's user-events endpoint no
  longer returns them).
- Material You dynamic color is used on Android 12+; a custom teal M3 palette
  is the fallback on older versions. Dark mode is fully supported.
- **Theme options** — System / Light / Dark / Pitch black (true-black AMOLED mode)
  in the Profile tab.

## Building
Open in Android Studio (AGP 8.4, Kotlin 1.9, compileSdk 34, minSdk 26) and run,
or from the command line: `./gradlew assembleDebug`.

If this project came from the build sandbox, remove the `systemProp.*` proxy
lines from `gradle.properties` and point `local.properties` at your own SDK
before building locally.
