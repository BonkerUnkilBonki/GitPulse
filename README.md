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
