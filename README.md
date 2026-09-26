# GitPulse — GitHub Productivity & Goal Tracker

A Material 3 (Material You) Android app that links to your GitHub account and
tracks your productivity: daily commits, streaks, weekly charts, contribution
heatmap, recent activity, repositories and goal tracking.

## App features
- **Dashboard** — today's commits vs goal (animated progress ring), streak chip,
  repos / stars / followers / PRs stat cards, 7-day bar chart with goal line,
  26-week contribution heatmap (tap any day for details), records.
- **Activity** — your last ~90 days of GitHub events (pushes, PRs, issues, stars,
  forks, releases). Tap an event to open the repo on GitHub.
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
- Daily-commit data comes from the GitHub Events API (up to 300 events /
  ~90 days) plus Search API for all-time totals. Streaks are computed from that.
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
