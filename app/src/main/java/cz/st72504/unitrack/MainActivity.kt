package cz.st72504.unitrack

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import cz.st72504.unitrack.model.*
import cz.st72504.unitrack.ui.MainScreen
import cz.st72504.unitrack.ui.screens.*
import cz.st72504.unitrack.ui.theme.UniTrack2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hlavní aktivita aplikace UniTrack.
 * Spravuje stav přihlášení, navigaci a načítání dat.
 */
class MainActivity : ComponentActivity() {

    private lateinit var dataCache: DataCache

    // Stavové proměnné pro UI
    private var statusText by mutableStateOf("Stav: Nepřihlášen")
    private var activeTab by mutableStateOf("celkove")
    private var showRegistrationForm by mutableStateOf(false)
    private var userName by mutableStateOf("")
    private var userTeam by mutableStateOf("")
    private var userIsPublic by mutableStateOf(true)
    private var userAvatarUrl by mutableStateOf("")
    private var userStats by mutableStateOf<UserStatistics?>(null)
    private var allUserStatsList by mutableStateOf<List<UserStatistics>>(emptyList())
    private var teamStatsList by mutableStateOf<List<TeamStatistics>>(emptyList())
    private var userAchievements by mutableStateOf<UserAchievements?>(null)
    private var showRankingScreen by mutableStateOf(false)
    private var showDistanceStatsScreen by mutableStateOf(false)
    private var showTimeStatsScreen by mutableStateOf(false)
    private var showAchievementsScreen by mutableStateOf(false)
    private var dailyActivities by mutableStateOf<List<UserDailyActivity>>(emptyList())

    private var isStravaLinked by mutableStateOf(false)
    private var loggedInPbToken by mutableStateOf("")
    private var loggedInUserId by mutableStateOf("")
    private var activitiesList by mutableStateOf<List<ActivityRecord>>(emptyList())
    private var publicActivitiesList by mutableStateOf<List<PublicActivityRecord>>(emptyList())
    private var showSettingsScreen by mutableStateOf(false)

    private val pbClient = PocketBaseClient()
    private val redirectUri = "https://pb.unitrack.fun/redirect.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        dataCache = DataCache(this)

        // Načtení uloženého přihlášení
        val prefs = getSharedPreferences("UniTrackPrefs", MODE_PRIVATE)
        loggedInPbToken = prefs.getString("pbToken", "") ?: ""
        loggedInUserId = prefs.getString("userId", "") ?: ""
        userName = prefs.getString("userName", "") ?: ""
        userTeam = prefs.getString("userTeam", "") ?: ""
        userIsPublic = prefs.getBoolean("userIsPublic", true)
        userAvatarUrl = prefs.getString("userAvatarUrl", "") ?: ""

        val savedStravaId = prefs.getString("stravaId", "") ?: ""
        isStravaLinked = savedStravaId.isNotEmpty()

        if (loggedInPbToken.isNotEmpty()) {
            statusText = "✅ Přihlášen jako: $userName"
            if (userTeam.isEmpty()) showRegistrationForm = true
            loadDataFromCache()
            fetchData(forceRefresh = false)
        } else {
            loadDataFromCache()
            fetchPublicDataOnly()
        }

        setContent {
            UniTrack2Theme {
                val isLoggedIn = loggedInPbToken.isNotEmpty()
                val snackbarHostState = remember { SnackbarHostState() }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        showDistanceStatsScreen -> {
                            DistanceStatsScreen(
                                userStats = userStats,
                                onBack = { showDistanceStatsScreen = false },
                                dailyActivities = dailyActivities
                            )
                        }
                        showTimeStatsScreen -> {
                            TimeStatsScreen(
                                userStats = userStats,
                                onBack = { showTimeStatsScreen = false },
                                dailyActivities = dailyActivities
                            )
                        }
                        showAchievementsScreen -> {
                            AchievementsScreen(
                                achievements = userAchievements,
                                onBack = { showAchievementsScreen = false }
                            )
                        }
                        showRankingScreen -> {
                            RankingScreen(
                                allUsers = allUserStatsList,
                                currentUserId = loggedInUserId,
                                currentUserTeam = userTeam,
                                onBack = { showRankingScreen = false }
                            )
                        }
                        showSettingsScreen -> {
                            SettingsScreen(
                                userName = userName,
                                userTeam = userTeam,
                                isPublic = userIsPublic,
                                isStravaLinked = isStravaLinked,
                                userAvatarUrl = userAvatarUrl,
                                onSave = { newName, newIsPublic, avatarBytes ->
                                    updateProfile(newName, userTeam, newIsPublic, avatarBytes)
                                },
                                onLogout = { logout() },
                                onDelete = { deleteAccount() },
                                onBack = { showSettingsScreen = false },
                                onLinkStrava = { openStravaBrowser() },
                                onSyncClick = { triggerSync(snackbarHostState) },
                                snackbarHostState = snackbarHostState
                            )
                        }
                        else -> {
                            MainScreen(
                                isLoggedIn = isLoggedIn,
                                activeTab = activeTab,
                                onTabChange = { activeTab = it },
                                showRegistration = showRegistrationForm,
                                activities = activitiesList,
                                publicActivities = publicActivitiesList,
                                userStats = userStats,
                                userAchievements = userAchievements,
                                teamStats = teamStatsList,
                                onMicrosoftClick = { startOAuthLogin("oidc") },
                                onSaveProfile = { name, team, isPublic, avatarBytes -> saveProfile(name, team, isPublic, avatarBytes) },
                                userName = userName,
                                userTeam = userTeam,
                                userAvatarUrl = userAvatarUrl,
                                onSettingsClick = { showSettingsScreen = true },
                                onRankingClick = { fetchAllUserStatsAndShowRanking() },
                                onDistanceClick = {
                                    fetchDailyActivities()
                                    showDistanceStatsScreen = true
                                },
                                onTimeClick = {
                                    fetchDailyActivities()
                                    showTimeStatsScreen = true
                                },
                                onAchievementsClick = { showAchievementsScreen = true },
                                onRefreshClick = { refreshDataFromDb(snackbarHostState) },
                                onSyncClick = { triggerSync(snackbarHostState) },
                                snackbarHostState = snackbarHostState
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Načítání a správa dat ---

    private fun fetchPublicDataOnly() {
        lifecycleScope.launch {
            fetchTeamStats(forceRefresh = false)
            fetchPublicActivities(forceRefresh = false)
        }
    }

    private fun loadDataFromCache() {
        val cachedUserStats = dataCache.getUserStats()
        activitiesList = dataCache.getActivities()
        publicActivitiesList = dataCache.getPublicActivities()
        val cachedAllStats = dataCache.getAllUserStats()
        dailyActivities = dataCache.getDailyActivities()
        userAchievements = dataCache.getUserAchievements()
        teamStatsList = rankTeamStats(dataCache.getTeamStats())

        allUserStatsList = rankStats(cachedAllStats)
        userStats = allUserStatsList.find { it.id == loggedInUserId } ?: cachedUserStats
    }

    private fun fetchData(forceRefresh: Boolean) {
        lifecycleScope.launch {
            fetchAllUserStats(forceRefresh)
            fetchUserStats(forceRefresh)
            fetchActivities(forceRefresh)
            fetchPublicActivities(forceRefresh)
            fetchUserAchievements(forceRefresh)
            fetchTeamStats(forceRefresh)
        }
    }

    private fun refreshDataFromDb(snackbarHostState: SnackbarHostState) {
        lifecycleScope.launch {
            if (loggedInPbToken.isNotEmpty()) {
                fetchData(forceRefresh = true)
            } else {
                fetchTeamStats(forceRefresh = true)
                fetchPublicActivities(forceRefresh = true)
            }

            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar("Data byla obnovena z databáze.")
            }
        }
    }

    private fun fetchAllUserStatsAndShowRanking() {
        fetchAllUserStats(forceRefresh = false)
        showRankingScreen = true
    }

    private fun rankStats(stats: List<UserStatistics>): List<UserStatistics> {
        return stats.sortedByDescending { it.total_distance }
            .mapIndexed { index, userStatistics ->
                userStatistics.copy(rank = index + 1)
            }
    }

    private fun rankTeamStats(stats: List<TeamStatistics>): List<TeamStatistics> {
        return stats.sortedByDescending { it.total_distance }
            .mapIndexed { index, teamStatistics ->
                teamStatistics.apply { rank = index + 1 }
            }
    }

    private fun fetchUserStats(forceRefresh: Boolean = false) {
        if (loggedInPbToken.isEmpty() || loggedInUserId.isEmpty()) return
        
        val foundInAll = allUserStatsList.find { it.id == loggedInUserId }
        if (!forceRefresh && foundInAll != null) {
            userStats = foundInAll
            return
        }

        lifecycleScope.launch {
            val stats = pbClient.getUserStatistics(loggedInPbToken, loggedInUserId)
            withContext(Dispatchers.Main) {
                if (stats != null) {
                    val currentRank = allUserStatsList.find { it.id == stats.id }?.rank ?: 0
                    val statsWithRank = stats.copy(rank = currentRank)
                    userStats = statsWithRank
                    dataCache.saveUserStats(statsWithRank)
                }
            }
        }
    }

    private fun fetchAllUserStats(forceRefresh: Boolean = false) {
        if (loggedInPbToken.isEmpty()) return
        
        val cached = dataCache.getAllUserStats()
        if (!forceRefresh && cached.isNotEmpty()) {
            val ranked = rankStats(cached)
            allUserStatsList = ranked
            ranked.find { it.id == loggedInUserId }?.let { 
                userStats = it 
            }
            return
        }

        lifecycleScope.launch {
            val stats = pbClient.getAllUserStatistics(loggedInPbToken)
            withContext(Dispatchers.Main) {
                val rankedStats = rankStats(stats)
                allUserStatsList = rankedStats
                dataCache.saveAllUserStats(rankedStats)
                
                rankedStats.find { it.id == loggedInUserId }?.let {
                    userStats = it
                    dataCache.saveUserStats(it)
                }
            }
        }
    }

    private fun fetchTeamStats(forceRefresh: Boolean = false) {
        val cached = dataCache.getTeamStats()
        if (!forceRefresh && cached.isNotEmpty()) {
            teamStatsList = rankTeamStats(cached)
            return
        }
        lifecycleScope.launch {
            val stats = pbClient.getTeamStatistics(loggedInPbToken)
            withContext(Dispatchers.Main) {
                val ranked = rankTeamStats(stats)
                teamStatsList = ranked
                dataCache.saveTeamStats(ranked)
            }
        }
    }

    private fun fetchActivities(forceRefresh: Boolean = false) {
        if (loggedInPbToken.isEmpty() || loggedInUserId.isEmpty()) return
        if (!forceRefresh && dataCache.getActivities().isNotEmpty()) {
            activitiesList = dataCache.getActivities()
            return
        }
        lifecycleScope.launch {
            val acts = pbClient.getUserActivities(loggedInPbToken, loggedInUserId)
            withContext(Dispatchers.Main) {
                activitiesList = acts
                dataCache.saveActivities(acts)
            }
        }
    }

    private fun fetchPublicActivities(forceRefresh: Boolean = false) {
        if (!forceRefresh && dataCache.getPublicActivities().isNotEmpty()) {
            publicActivitiesList = dataCache.getPublicActivities()
            return
        }
        lifecycleScope.launch {
            val acts = pbClient.getPublicActivities(loggedInPbToken)
            withContext(Dispatchers.Main) {
                publicActivitiesList = acts
                dataCache.savePublicActivities(acts)
            }
        }
    }

    private fun fetchDailyActivities(forceRefresh: Boolean = false) {
        if (loggedInPbToken.isEmpty() || loggedInUserId.isEmpty()) return
        if (!forceRefresh && dataCache.getDailyActivities().isNotEmpty()) {
            dailyActivities = dataCache.getDailyActivities()
            return
        }
        lifecycleScope.launch {
            val daily = pbClient.getUserDailyActivities(loggedInPbToken, loggedInUserId)
            withContext(Dispatchers.Main) {
                dailyActivities = daily
                dataCache.saveDailyActivities(daily)
            }
        }
    }

    private fun fetchUserAchievements(forceRefresh: Boolean = false) {
        if (loggedInPbToken.isEmpty() || loggedInUserId.isEmpty()) return
        if (!forceRefresh && dataCache.getUserAchievements() != null) {
            userAchievements = dataCache.getUserAchievements()
            return
        }
        lifecycleScope.launch {
            val achs = pbClient.getUserAchievements(loggedInPbToken, loggedInUserId)
            withContext(Dispatchers.Main) {
                if (achs != null) {
                    userAchievements = achs
                    dataCache.saveUserAchievements(achs)
                }
            }
        }
    }

    // --- Autentizace a externí služby ---

    private fun startOAuthLogin(providerName: String) {
        lifecycleScope.launch {
            val provider = pbClient.getAuthProvider(providerName)
            withContext(Dispatchers.Main) {
                if (provider != null) {
                    getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().apply {
                        putString("currentProvider", provider.name)
                        putString("currentCodeVerifier", provider.codeVerifier)
                        apply()
                    }
                    val cleanUri = Uri.parse(provider.authUrl + redirectUri).buildUpon().clearQuery()
                    Uri.parse(provider.authUrl + redirectUri).queryParameterNames.forEach {
                        cleanUri.appendQueryParameter(it, if (it == "scope" && providerName == "strava") "profile:read_all,activity:read_all" else Uri.parse(provider.authUrl + redirectUri).getQueryParameter(it))
                    }
                    startActivity(Intent(Intent.ACTION_VIEW, cleanUri.build()))
                }
            }
        }
    }

    private fun openStravaBrowser() {
        if (loggedInPbToken.isEmpty()) {
            statusText = "Nejdřív se musíš přihlásit!"
            return
        }
        val stravaUrl = "https://www.strava.com/oauth/mobile/authorize?client_id=182093&response_type=code&redirect_uri=$redirectUri&scope=activity:read_all&state=strava"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(stravaUrl)))
    }

    private fun triggerSync(snackbarHostState: SnackbarHostState) {
        if (loggedInPbToken.isEmpty()) return
        statusText = "Stahuji běhy..."
        lifecycleScope.launch {
            val sync = pbClient.triggerStravaSync(loggedInPbToken)
            withContext(Dispatchers.Main) {
                fetchData(forceRefresh = true)
                val message = if (sync != null) "Hotovo! Uloženo ${sync.saved} nových aktivit." else "Načteno z DB."
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // --- Správa uživatelského profilu ---

    private fun saveProfile(name: String, team: String, isPublic: Boolean, avatarBytes: ByteArray?) {
        statusText = "Ukládám profil na server..."
        lifecycleScope.launch {
            val updatedRecord = pbClient.updateUserProfile(loggedInPbToken, loggedInUserId, name, team, isPublic, avatarBytes)
            withContext(Dispatchers.Main) {
                if (updatedRecord != null) {
                    val avatarUrl = updatedRecord.avatar?.let { "https://pb.unitrack.fun/api/files/_pb_users_auth_/${updatedRecord.id}/$it" } ?: ""
                    getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().apply {
                        putString("userName", name)
                        putString("userTeam", team)
                        putBoolean("userIsPublic", isPublic)
                        putString("userAvatarUrl", avatarUrl)
                        apply()
                    }
                    userName = name
                    userTeam = team
                    userIsPublic = isPublic
                    userAvatarUrl = avatarUrl
                    showRegistrationForm = false
                    statusText = "✅ Profil úspěšně nastaven!"
                    fetchData(forceRefresh = true)
                } else {
                    statusText = "❌ Nepodařilo se uložit data na server."
                }
            }
        }
    }

    private fun updateProfile(name: String, team: String, isPublic: Boolean, avatarBytes: ByteArray?) {
        statusText = "Aktualizuji profil..."
        lifecycleScope.launch {
            val updatedRecord = pbClient.updateUserProfile(loggedInPbToken, loggedInUserId, name, team, isPublic, avatarBytes)
            withContext(Dispatchers.Main) {
                if (updatedRecord != null) {
                    val avatarUrl = updatedRecord.avatar?.let { "https://pb.unitrack.fun/api/files/_pb_users_auth_/${updatedRecord.id}/$it" } ?: ""
                    getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().apply {
                        putString("userName", name)
                        putBoolean("userIsPublic", isPublic)
                        putString("userAvatarUrl", avatarUrl)
                        apply()
                    }
                    userName = name
                    userIsPublic = isPublic
                    userAvatarUrl = avatarUrl
                    statusText = "✅ Profil aktualizován!"
                    showSettingsScreen = false
                    fetchData(forceRefresh = true)
                } else {
                    statusText = "❌ Chyba při aktualizaci profilu."
                }
            }
        }
    }

    private fun deleteAccount() {
        statusText = "Mažu účet..."
        lifecycleScope.launch {
            val success = pbClient.deleteUser(loggedInPbToken, loggedInUserId)
            withContext(Dispatchers.Main) {
                if (success) {
                    logout()
                    statusText = "Účet byl úspěšně smazán."
                } else {
                    statusText = "❌ Chyba při mazání účtu."
                }
            }
        }
    }

    private fun logout() {
        loggedInPbToken = ""
        loggedInUserId = ""
        userName = ""
        userTeam = ""
        userAvatarUrl = ""
        isStravaLinked = false
        statusText = "Stav: Nepřihlášen"

        showRegistrationForm = false
        showSettingsScreen = false

        activitiesList = emptyList()
        userStats = null
        allUserStatsList = emptyList()
        dailyActivities = emptyList()
        userAchievements = null

        dataCache.clearCache()
        getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().clear().apply()

        fetchPublicDataOnly()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        if (uri.scheme == "unitrack" && uri.host == "oauth2") {
            val code = uri.getQueryParameter("code") ?: return
            val state = uri.getQueryParameter("state")
            val prefs = getSharedPreferences("UniTrackPrefs", MODE_PRIVATE)
            val currentProvider = prefs.getString("currentProvider", "") ?: ""
            val currentCodeVerifier = prefs.getString("currentCodeVerifier", "") ?: ""

            lifecycleScope.launch {
                if (state == "strava") {
                    val success = pbClient.linkStravaWithCode(loggedInPbToken, code)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            val user = pbClient.getMe(loggedInPbToken, loggedInUserId)
                            if (user != null) {
                                prefs.edit().putString("stravaId", user.strava_athlete_id ?: "").apply()
                                statusText = "✅ Strava připojena!"
                                isStravaLinked = true
                                fetchData(forceRefresh = true)
                                lifecycleScope.launch {
                                    pbClient.triggerStravaSync(loggedInPbToken)
                                    fetchData(forceRefresh = true)
                                    statusText = "✅ Strava propojena a synchronizována!"
                                }
                            }
                        } else {
                            statusText = "❌ Chyba Stravy."
                        }
                    }
                } else {
                    val auth = pbClient.authWithOAuth2(currentProvider, code, currentCodeVerifier, redirectUri)
                    withContext(Dispatchers.Main) {
                        if (auth != null) {
                            loggedInPbToken = auth.token
                            loggedInUserId = auth.record.id
                            userName = auth.record.name
                            userTeam = auth.record.team ?: ""
                            userIsPublic = auth.record.public ?: true
                            userAvatarUrl = auth.record.avatar?.let { "https://pb.unitrack.fun/api/files/_pb_users_auth_/${auth.record.id}/$it" } ?: ""
                            statusText = "✅ Přihlášen"
                            isStravaLinked = !auth.record.strava_athlete_id.isNullOrEmpty()

                            if (auth.record.team.isNullOrEmpty()) {
                                showRegistrationForm = true
                            }

                            prefs.edit().apply {
                                putString("pbToken", auth.token)
                                putString("userId", auth.record.id)
                                putString("userName", auth.record.name)
                                putString("userTeam", auth.record.team ?: "")
                                putBoolean("userIsPublic", auth.record.public ?: true)
                                putString("stravaId", auth.record.strava_athlete_id ?: "")
                                putString("userAvatarUrl", userAvatarUrl)
                                remove("currentProvider")
                                remove("currentCodeVerifier")
                                apply()
                            }
                            fetchData(forceRefresh = true)
                        }
                    }
                }
            }
        }
    }
}
