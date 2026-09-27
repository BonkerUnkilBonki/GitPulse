package com.codenamezeroseven.gitpulse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.codenamezeroseven.gitpulse.databinding.ActivityMainBinding
import com.codenamezeroseven.gitpulse.ui.ActivityFragment
import com.codenamezeroseven.gitpulse.ui.DashboardFragment
import com.codenamezeroseven.gitpulse.ui.GoalsFragment
import com.codenamezeroseven.gitpulse.ui.ProfileFragment
import com.codenamezeroseven.gitpulse.ui.ReposFragment
import com.codenamezeroseven.gitpulse.ui.TasksFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val dashboard = DashboardFragment()
    private val activityFrag = ActivityFragment()
    private val repos = ReposFragment()
    private val tasks = TasksFragment()
    private val goals = GoalsFragment()
    private val profile = ProfileFragment()
    private var current: Fragment = dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(applicationContext)
        // Selected color palette (skipped when dynamic color is on).
        if (Prefs.palette != "dynamic") {
            theme.applyStyle(Palette.overlayRes(), true)
        }
        if (Prefs.theme == "black") {
            theme.applyStyle(R.style.ThemeOverlay_GitPulse_PitchBlack, true)
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Pad only the top (status bar) on the root. The bottom nav pads
            // itself so its surface extends behind the system 3-button /
            // gesture bar - no gap, no duplicated bar.
            v.setPadding(0, bars.top, 0, 0)
            binding.bottomNav.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        supportFragmentManager.beginTransaction()
            .add(R.id.container, profile).hide(profile)
            .add(R.id.container, goals).hide(goals)
            .add(R.id.container, repos).hide(repos)
            .add(R.id.container, tasks).hide(tasks)
            .add(R.id.container, activityFrag).hide(activityFrag)
            .add(R.id.container, dashboard)
            .commit()

        binding.bottomNav.setOnItemSelectedListener { item ->
            val f = when (item.itemId) {
                R.id.nav_dashboard -> dashboard
                R.id.nav_activity -> activityFrag
                R.id.nav_repos -> repos
                R.id.nav_tasks -> tasks
                R.id.nav_goals -> goals
                else -> profile
            }
            if (f !== current) {
                supportFragmentManager.beginTransaction().hide(current).show(f).commit()
                current = f
                // Quick fade-in so tab switches feel smooth, not instant.
                f.view?.let { v ->
                    v.alpha = 0f
                    v.animate().alpha(1f).setDuration(180)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
            }
            true
        }

        if (Prefs.token.isBlank()) {
            binding.bottomNav.selectedItemId = R.id.nav_profile
        } else {
            // Auto-refresh and sync in the background every time the app
            // opens; the UI already shows the cached snapshot meanwhile.
            lifecycleScope.launch {
                runCatching { GitHubData.refresh(force = true) }
                // Keep tasks and goals in sync with the other devices while
                // the app is open (pull every ~75s; GitHub has no push).
                while (true) {
                    delay(75_000)
                    val owner = GitHubData.user?.login
                        ?: Prefs.ownerLogin.takeIf { it.isNotBlank() }
                        ?: continue
                    runCatching { TaskSync.sync(Prefs.token, owner) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back to the app: pull fresh tasks/goals if it has been a bit.
        if (Prefs.token.isNotBlank() && System.currentTimeMillis() - TaskSync.lastAttempt > 20_000) {
            val owner = GitHubData.user?.login ?: Prefs.ownerLogin.takeIf { it.isNotBlank() }
            if (owner != null) {
                lifecycleScope.launch {
                    runCatching { TaskSync.sync(Prefs.token, owner) }
                }
            }
        }
    }

    fun openProfile() {
        binding.bottomNav.selectedItemId = R.id.nav_profile
    }
}
