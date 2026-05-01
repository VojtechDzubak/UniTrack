package cz.st72504.unitrack2

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DataCache(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("UniTrackCache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveUserStats(stats: UserStatistics?) {
        val json = gson.toJson(stats)
        prefs.edit().putString("user_stats", json).apply()
    }

    fun getUserStats(): UserStatistics? {
        val json = prefs.getString("user_stats", null) ?: return null
        return gson.fromJson(json, UserStatistics::class.java)
    }

    fun saveAllUserStats(stats: List<UserStatistics>) {
        val json = gson.toJson(stats)
        prefs.edit().putString("all_user_stats", json).apply()
    }

    fun getAllUserStats(): List<UserStatistics> {
        val json = prefs.getString("all_user_stats", null) ?: return emptyList()
        val type = object : TypeToken<List<UserStatistics>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveActivities(activities: List<ActivityRecord>) {
        val json = gson.toJson(activities)
        prefs.edit().putString("activities", json).apply()
    }

    fun getActivities(): List<ActivityRecord> {
        val json = prefs.getString("activities", null) ?: return emptyList()
        val type = object : TypeToken<List<ActivityRecord>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveDailyActivities(dailyActivities: List<UserDailyActivity>) {
        prefs.edit().putString("daily_activities", gson.toJson(dailyActivities)).apply()
    }

    fun getDailyActivities(): List<UserDailyActivity> {
        val json = prefs.getString("daily_activities", null) ?: return emptyList()
        val type = object : TypeToken<List<UserDailyActivity>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveUserAchievements(achievements: UserAchievements?) {
        val json = gson.toJson(achievements)
        prefs.edit().putString("user_achievements", json).apply()
    }

    fun getUserAchievements(): UserAchievements? {
        val json = prefs.getString("user_achievements", null) ?: return null
        return gson.fromJson(json, UserAchievements::class.java)
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}
