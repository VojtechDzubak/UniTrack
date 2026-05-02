package cz.st72504.unitrack.model

/**
 * Modely pro statistiky týmů (fakult).
 */

data class TeamStatistics(
    val id: String,
    val team: String,
    val runners_count: Int,
    val total_distance: Double,
    val run_distance: Double,
    val walk_distance: Double,
    val ride_distance: Double,
    var rank: Int = 0
)

data class TeamStatisticsListResponse(val items: List<TeamStatistics>)

data class SyncResponse(
    val message: String,
    val saved: Int,
    val total: Int
)
