package com.codenamezeroseven.gitpulse

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors

class GitPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        TaskStore.init(this)
        Notifier.appContext = this
        Notifier.createChannel()
        DynamicColors.applyToActivitiesIfAvailable(this)
        applyNightMode()
    }

    companion object {
        fun applyNightMode() {
            AppCompatDelegate.setDefaultNightMode(
                when (Prefs.theme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark", "black" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
