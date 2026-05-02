package cz.st72504.unitrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.st72504.unitrack.model.*
import cz.st72504.unitrack.ui.screens.*
import cz.st72504.unitrack.ui.theme.UpceRed

@Composable
fun MainScreen(
    isLoggedIn: Boolean,
    activeTab: String,
    onTabChange: (String) -> Unit,
    showRegistration: Boolean,
    activities: List<ActivityRecord>,
    publicActivities: List<PublicActivityRecord>,
    userStats: UserStatistics?,
    userAchievements: UserAchievements?,
    teamStats: List<TeamStatistics>,
    onMicrosoftClick: () -> Unit,
    onSaveProfile: (String, String, Boolean, ByteArray?) -> Unit,
    userName: String,
    userTeam: String,
    userAvatarUrl: String,
    onSettingsClick: () -> Unit,
    onRankingClick: () -> Unit,
    onDistanceClick: () -> Unit,
    onTimeClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSyncClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == "celkove",
                    onClick = { onTabChange("celkove") },
                    icon = { Text("🏆", fontSize = 20.sp) },
                    label = { Text("Celkové výsledky", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UpceRed,
                        selectedTextColor = UpceRed,
                        indicatorColor = UpceRed.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == "moje_vysledky",
                    onClick = { onTabChange("moje_vysledky") },
                    icon = { Text("🏃", fontSize = 20.sp) },
                    label = { Text("Osobní výsledky", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UpceRed,
                        selectedTextColor = UpceRed,
                        indicatorColor = UpceRed.copy(alpha = 0.1f)
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (activeTab == "celkove") {
                OverallResultsView(
                    teamStats = teamStats,
                    publicActivities = publicActivities,
                    onRefreshClick = onRefreshClick
                )
            } else {
                if (!isLoggedIn) {
                    LoggedOutView(onMicrosoftClick)
                } else if (showRegistration) {
                    RegistrationForm(onSaveProfile)
                } else {
                    MyResultsView(
                        userName = userName,
                        userTeam = userTeam,
                        userAvatarUrl = userAvatarUrl,
                        onSettingsClick = onSettingsClick,
                        onRankingClick = onRankingClick,
                        onDistanceClick = onDistanceClick,
                        onTimeClick = onTimeClick,
                        onAchievementsClick = onAchievementsClick,
                        activities = activities,
                        userStats = userStats,
                        userAchievements = userAchievements,
                        onRefreshClick = onRefreshClick
                    )
                }
            }
        }
    }
}
