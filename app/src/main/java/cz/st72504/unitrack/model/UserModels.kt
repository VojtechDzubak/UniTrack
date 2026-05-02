package cz.st72504.unitrack.model

/**
 * Modely reprezentující uživatele a jeho statistiky.
 */

data class UserRecord(
    val id: String,
    val name: String,
    val email: String,
    val team: String?,
    val public: Boolean?,
    val avatar: String?,
    val strava_athlete_id: String?
)

data class UserStatistics(
    val id: String,
    val name: String,
    val team: String,
    val avatar: String,
    val total_distance: Double,
    val total_time: Int,
    val longest_run: Double,
    val avg_distance: Double,
    val avg_time: Double,
    val longest_time: Int,
    val calories: Int,
    val total_xp: Int,
    val level: Int,
    val current_level_xp: Int,
    val xp_for_next_level: Int,
    val rank: Int = 0
)

data class UserStatisticsListResponse(val items: List<UserStatistics>)
