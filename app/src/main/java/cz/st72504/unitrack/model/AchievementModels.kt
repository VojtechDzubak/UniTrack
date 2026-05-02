package cz.st72504.unitrack.model

/**
 * Modely pro úspěchy (odznaky) uživatele.
 */

data class UserAchievements(
    val id: String,
    val badge_run_50: Int,
    val badge_walk_20: Int,
    val badge_ride_100: Int,
    val badge_total_150: Int,
    val badge_single_20: Int,
    val badge_morning: Int,
    val badge_sprinter: Int,
    val badge_weekend: Int
) {
    fun earnedCount(): Int {
        return listOf(
            badge_run_50, badge_walk_20, badge_ride_100, badge_total_150,
            badge_single_20, badge_morning, badge_sprinter, badge_weekend
        ).count { it > 0 }
    }

    fun totalCount(): Int = 8
}
