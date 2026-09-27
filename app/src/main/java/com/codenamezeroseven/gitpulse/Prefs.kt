package com.codenamezeroseven.gitpulse

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("gitpulse", Context.MODE_PRIVATE)
    }

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(value) { sp.edit().putString("token", value.trim()).apply() }

    var dailyGoal: Int
        get() = sp.getInt("dailyGoal", 3)
        set(value) { sp.edit().putInt("dailyGoal", value.coerceIn(1, 200)).apply() }

    var weeklyGoal: Int
        get() = sp.getInt("weeklyGoal", 15)
        set(value) { sp.edit().putInt("weeklyGoal", value.coerceIn(1, 1000)).apply() }

    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(value) { sp.edit().putString("theme", value).apply() }

    var ownerLogin: String
        get() = sp.getString("ownerLogin", "") ?: ""
        set(value) { sp.edit().putString("ownerLogin", value).apply() }

    var goalsUpdatedAt: Long
        get() = sp.getLong("goalsUpdatedAt", 0L)
        set(value) { sp.edit().putLong("goalsUpdatedAt", value).apply() }

    var palette: String
        get() = sp.getString("palette", "teal") ?: "teal"
        set(value) { sp.edit().putString("palette", value).apply() }

    var notifAsked: Boolean
        get() = sp.getBoolean("notifAsked", false)
        set(value) { sp.edit().putBoolean("notifAsked", value).apply() }

    fun dailyCommits(): Map<LocalDate, Int> {
        val raw = sp.getString("dailyCommits", null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            val out = mutableMapOf<LocalDate, Int>()
            for (k in o.keys()) out[LocalDate.parse(k)] = o.optInt(k)
            out
        }.getOrDefault(emptyMap())
    }

    fun saveDailyCommits(map: Map<LocalDate, Int>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k.toString(), v) }
        sp.edit().putString("dailyCommits", o.toString()).apply()
    }

    fun signOut() {
        sp.edit().remove("token").remove("dailyCommits").remove("ownerLogin").apply()
    }
}
