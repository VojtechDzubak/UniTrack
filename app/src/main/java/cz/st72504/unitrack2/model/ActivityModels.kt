package cz.st72504.unitrack2.model

/**
 * Modely pro sportovní aktivity a jejich přehledy.
 */

data class ActivityListResponse(val items: List<ActivityRecord>)

data class ActivityRecord(
    val id: String,
    val name: String,
    val distance: Double,
    val duration: Int,
    val start_date: String,
    val activity_type: String
)

data class PublicActivityListResponse(val items: List<PublicActivityRecord>)

data class PublicActivityRecord(
    val id: String,
    val user_id: String,
    val user_name: String,
    val user_avatar: String,
    val user_team: String,
    val distance: Double,
    val activity_type: String,
    val duration: Int,
    val start_date: String
)

data class UserDailyActivity(
    val day: String,
    val total_distance: Double,
    val total_duration: Int
)

data class UserDailyActivityListResponse(val items: List<UserDailyActivity>)
