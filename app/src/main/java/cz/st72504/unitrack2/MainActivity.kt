package cz.st72504.unitrack2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.core.entry.entryOf
import cz.st72504.unitrack2.ui.theme.UniTrack2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// --- Designový systém ---
val UpceRed = Color(0xFFE32A22)
val UpceBlue = Color(0xFF009EE3)
val UpceGreen = Color(0xFF00A651)
val StravaOrange = Color(0xFFFC4C02)
val ProgressTeal = Color(0xFF00CED1)

// Barvy pro graf fakult
val FacultyColors = listOf(
    Color(0xFFE32A22),
    Color(0xFF009EE3),
    Color(0xFF00A651),
    Color(0xFFFFB300),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4),
    Color(0xFFFF5722),
    Color(0xFF795548)
)

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

    // Načte data z lokální mezipaměti pro rychlé zobrazení při startu
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

    // Spustí paralelní načítání všech dat ze serveru
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

    // Vyvolá vynucené načtení dat z DB s potvrzením v UI
    private fun refreshDataFromDb(snackbarHostState: SnackbarHostState) {
        if (loggedInPbToken.isEmpty()) return
        lifecycleScope.launch {
            fetchData(forceRefresh = true)
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar("Data byla obnovena z databáze.")
            }
        }
    }

    // Načte statistiky a otevře obrazovku žebříčku
    private fun fetchAllUserStatsAndShowRanking() {
        fetchAllUserStats(forceRefresh = false)
        showRankingScreen = true
    }

    // Seřadí uživatele podle naběhané vzdálenosti
    private fun rankStats(stats: List<UserStatistics>): List<UserStatistics> {
        return stats.sortedByDescending { it.total_distance }
            .mapIndexed { index, userStatistics ->
                userStatistics.copy(rank = index + 1)
            }
    }

    // Seřadí týmy (fakulty) podle celkové vzdálenosti
    private fun rankTeamStats(stats: List<TeamStatistics>): List<TeamStatistics> {
        return stats.sortedByDescending { it.total_distance }
            .mapIndexed { index, teamStatistics ->
                teamStatistics.apply { rank = index + 1 }
            }
    }

    // Načte statistiky pro aktuálně přihlášeného uživatele
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

    // Načte statistiky všech uživatelů ze serveru
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

    // Načte statistiky jednotlivých fakult
    private fun fetchTeamStats(forceRefresh: Boolean = false) {
        if (loggedInPbToken.isEmpty()) return
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

    // Načte historii všech aktivit uživatele
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

    // Načte historii veřejných aktivit všech uživatelů
    private fun fetchPublicActivities(forceRefresh: Boolean = false) {
        if (loggedInPbToken.isEmpty()) return
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

    // Načte sumární denní aktivity pro grafy
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

    // Načte přehled získaných odznaků
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

    // Zahájí OAuth proces pro daného poskytovatele (Microsoft, Strava)
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

    // Přesměruje uživatele do prohlížeče pro autorizaci Stravy
    private fun openStravaBrowser() {
        if (loggedInPbToken.isEmpty()) {
            statusText = "Nejdřív se musíš přihlásit!"
            return
        }
        val stravaUrl = "https://www.strava.com/oauth/mobile/authorize?client_id=182093&response_type=code&redirect_uri=$redirectUri&scope=activity:read_all&state=strava"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(stravaUrl)))
    }

    // Vyvolá synchronizaci dat ze Stravy na straně serveru
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

    // Prvotní uložení profilu při registraci
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

    // Aktualizace údajů ve stávajícím profilu
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

    // Smaže účet uživatele a odhlásí jej
    private fun deleteAccount() {
        statusText = "Mažu účet..."
        lifecycleScope.launch {
            val success = pbClient.deleteUser(loggedInPbToken, loggedInUserId)
            withContext(Dispatchers.Main) {
                if (success) {
                    logout()
                    statusText = "Účet smazán."
                }
            }
        }
    }

    // Vymaže lokální data a odhlásí uživatele z aplikace
    private fun logout() {
        loggedInPbToken = ""
        loggedInUserId = ""
        userName = ""
        userTeam = ""
        userAvatarUrl = ""
        activitiesList = emptyList()
        publicActivitiesList = emptyList()
        isStravaLinked = false
        showRegistrationForm = false
        showSettingsScreen = false
        userStats = null
        allUserStatsList = emptyList()
        teamStatsList = emptyList()
        dailyActivities = emptyList()
        userAchievements = null
        statusText = "Stav: Nepřihlášen"
        dataCache.clearCache()
        getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().clear().apply()
    }

    // Zpracování návratu z prohlížeče (OAuth callback)
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
                    // Propojení Stravy s existujícím účtem
                    val success = pbClient.linkStravaWithCode(loggedInPbToken, code)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            val user = pbClient.getMe(loggedInPbToken, loggedInUserId)
                            if (user != null) {
                                prefs.edit().putString("stravaId", user.strava_athlete_id ?: "").apply()
                                statusText = "✅ Strava připojena!"
                                isStravaLinked = true
                                fetchData(forceRefresh = true)
                            }
                        } else {
                            statusText = "❌ Chyba Stravy."
                        }
                    }
                } else {
                    // Přihlášení přes Microsoft/PocketBase
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

// --- UI Komponenty ---

/**
 * Hlavní kontejner aplikace se spodní navigací.
 */
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
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = UpceRed, selectedTextColor = UpceRed, indicatorColor = UpceRed.copy(alpha = 0.1f))
                )
                NavigationBarItem(
                    selected = activeTab == "moje_vysledky",
                    onClick = { onTabChange("moje_vysledky") },
                    icon = { Text("🏃", fontSize = 20.sp) },
                    label = { Text("Osobní výsledky", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = UpceRed, selectedTextColor = UpceRed, indicatorColor = UpceRed.copy(alpha = 0.1f))
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
                OverallResultsView(teamStats, publicActivities)
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

// Pohled pro celkové výsledky univerzitní výzvy
@Composable
fun OverallResultsView(teamStats: List<TeamStatistics>, publicActivities: List<PublicActivityRecord>) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d. M. yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        @Suppress("DEPRECATION")
        Text(
            "Celkové výsledky",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (teamStats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = UpceRed)
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Pořadí fakult",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    teamStats.forEach { team ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${team.rank}.",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                modifier = Modifier.width(36.dp),
                                color = if (team.rank <= 3) UpceRed else MaterialTheme.colorScheme.onSurface
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = team.team,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Účastníků: ${team.runners_count}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f km", team.total_distance / 1000),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "celkem",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (teamStats.last() != team) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 0.dp))
                        }
                    }
                }
            }
        }

        if (teamStats.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Podíl kilometrů",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    FacultyDonutChart(teamStats)
                }
            }
        }

        Text(
            text = "Poslední aktivity:",
            modifier = Modifier.padding(bottom = 12.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (publicActivities.isEmpty()) {
            Text(
                text = "Žádné aktivity k zobrazení.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 600.dp)) {
                items(publicActivities) { act ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = if (act.user_avatar.isNotEmpty()) "https://pb.unitrack.fun/api/files/_pb_users_auth_/${act.id}/${act.user_avatar}" else "",
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.ic_profile_placeholder),
                                error = painterResource(id = R.drawable.ic_profile_placeholder)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = act.user_name,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${act.user_team} • ${String.format("%.2f", act.distance / 1000.0)} km • ${act.duration / 60} min • ${LocalDate.parse(act.start_date.substring(0, 10)).format(dateFormatter)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Donut graf zobrazující podíl kilometrů jednotlivých fakult
@Composable
fun FacultyDonutChart(teamStats: List<TeamStatistics>) {
    val totalDistance = teamStats.sumOf { it.total_distance }
    val totalDistanceKm = totalDistance / 1000.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .aspectRatio(1f)
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(190.dp)) {
                var startAngle = -90f
                teamStats.forEachIndexed { index, team ->
                    val sweepAngle = if (totalDistance > 0) {
                        (team.total_distance / totalDistance).toFloat() * 360f
                    } else 0f

                    drawArc(
                        color = FacultyColors.getOrElse(index) { Color.Gray },
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Butt)
                    )
                    startAngle += sweepAngle
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CELKEM",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.0f km", totalDistanceKm),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(40.dp))

        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            teamStats.forEachIndexed { index, team ->
                val percentage = if (totalDistance > 0) {
                    (team.total_distance.toDouble() / totalDistance * 100)
                } else 0.0

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = FacultyColors.getOrElse(index) { Color.Gray },
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${team.team} (${String.format(java.util.Locale.US, "%.1f%%", percentage)})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

/**
 * Obrazovka pro nepřihlášeného uživatele.
 */
@Composable
fun LoggedOutView(onMicrosoftClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        @Suppress("DEPRECATION")
        Text("Tato sekce je uzamčena", fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Pro zobrazení statistik se musíš přihlásit.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Button(
            onClick = onMicrosoftClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UpceRed)
        ) {
            Text("Přihlásit se přes Microsoft", fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

// Formulář pro nastavení jména a fakulty po prvním přihlášení
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationForm(onSave: (String, String, Boolean, ByteArray?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var team by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    val teams = listOf("DFJP", "FES", "FEI", "FCHT", "FF", "FR", "FZS", "Rektorát")
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                avatarBytes = stream.readBytes()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Dokončení profilu", fontSize = 26.sp, fontWeight = FontWeight.Black, color = UpceRed)
        Text("Nastav si údaje pro univerzitní výzvu", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 32.dp))

        val imageBitmap = avatarBytes?.let {
            android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap()
        }

        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("👤", fontSize = 60.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Změnit fotku",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { imagePickerLauncher.launch("image/*") }
        )
        Spacer(Modifier.height(24.dp))


        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Tvé jméno") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(16.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = team, onValueChange = {}, readOnly = true,
                label = { Text("Vyber fakultu") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                teams.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { team = t; expanded = false })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isPublic, onCheckedChange = { isPublic = it }, colors = SwitchDefaults.colors(checkedThumbColor = UpceRed))
            Spacer(Modifier.width(12.dp))
            Column {
                @Suppress("DEPRECATION")
                Text("Veřejný profil", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("Ostatní uvidí tvé jméno v žebříčku", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (name.isNotEmpty() && team.isNotEmpty()) onSave(name, team, isPublic, avatarBytes) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UpceRed)
        ) {
            Text("Uložit a začít běhat", fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

// Osobní profil a výsledky přihlášeného uživatele
@Composable
fun MyResultsView(
    userName: String,
    userTeam: String,
    userAvatarUrl: String,
    onSettingsClick: () -> Unit,
    onRankingClick: () -> Unit,
    onDistanceClick: () -> Unit,
    onTimeClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    activities: List<ActivityRecord>,
    userStats: UserStatistics?,
    userAchievements: UserAchievements?,
    onRefreshClick: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d. M. yyyy", Locale.getDefault()) }
    val lastActivities = activities.take(100)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                @Suppress("DEPRECATION")
                Text(text = userName, fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(text = userTeam, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AsyncImage(
                model = userAvatarUrl,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_profile_placeholder),
                error = painterResource(id = R.drawable.ic_profile_placeholder)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            @Suppress("DEPRECATION")
            OutlinedButton(
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text("Nastavení", fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Synchronizovat"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LevelStatCard(
                userStats = userStats,
                onClick = { }
            )
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "Celková vzdálenost",
                    value = if (userStats != null) String.format(java.util.Locale.US, "%.2f km", userStats.total_distance / 1000) else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onDistanceClick
                )
                StatCard(
                    label = "Celková doba",
                    value = if (userStats != null) "${userStats.total_time / 3600}h ${(userStats.total_time % 3600) / 60}m" else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onTimeClick
                )
            }
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "Umístění",
                    value = if (userStats != null && userStats.rank > 0) userStats.rank.toString() else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onRankingClick
                )
                StatCard(
                    label = "Počet odznaků",
                    value = if (userAchievements != null) "${userAchievements.earnedCount()} / ${userAchievements.totalCount()}" else "0 / 8",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onAchievementsClick
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Poslední aktivity:",
            modifier = Modifier.padding(bottom = 12.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (lastActivities.isEmpty()) {
            Text(
                text = "Žádné aktivity k zobrazení.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 300.dp)) {
                items(lastActivities) { act ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(UpceRed.copy(0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text("🏃") }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = act.name,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${act.activity_type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} • ${String.format("%.2f", act.distance / 1000.0)} km • ${act.duration / 60} min • ${LocalDate.parse(act.start_date.substring(0, 10)).format(dateFormatter)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Základní karta se statistikou
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

// Karta zobrazující aktuální úroveň a XP progres
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelStatCard(userStats: UserStatistics?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(0.6f, fill = false)
                ) {
                    Text(
                        text = "AKTUÁLNÍ ÚROVEŇ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Úroveň ${userStats?.level ?: "?"}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(0.4f, fill = false)
                ) {
                    Text(
                        text = "ZKUŠENOSTI (XP)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (userStats != null) "${userStats.current_level_xp} / ${userStats.xp_for_next_level} XP" else "Načítání...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProgressTeal
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            val progress = if (userStats != null && userStats.xp_for_next_level > 0) {
                userStats.current_level_xp.toFloat() / userStats.xp_for_next_level.toFloat()
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = ProgressTeal,
                trackColor = ProgressTeal.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Za každý naběhaný kilometr získáš 100 XP.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Obrazovka nastavení aplikace a profilu
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String,
    userTeam: String,
    isPublic: Boolean,
    isStravaLinked: Boolean,
    userAvatarUrl: String,
    onSave: (String, Boolean, ByteArray?) -> Unit,
    onLogout: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onLinkStrava: () -> Unit,
    onSyncClick: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    BackHandler { onBack() }
    var currentName by remember { mutableStateOf(userName) }
    var currentIsPublic by remember { mutableStateOf(isPublic) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                avatarBytes = stream.readBytes()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nastavení") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Nastavení profilu",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val imageBitmap = avatarBytes?.let {
                        android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size).asImageBitmap()
                    }

                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = userAvatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.ic_profile_placeholder),
                                error = painterResource(id = R.drawable.ic_profile_placeholder)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Změnit fotku",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { imagePickerLauncher.launch("image/*") }
                    )
                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = currentName,
                        onValueChange = { currentName = it },
                        label = { Text("Jméno") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Switch(checked = currentIsPublic, onCheckedChange = { currentIsPublic = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Veřejný profil", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onSave(currentName, currentIsPublic, avatarBytes) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = UpceRed)
                    ) {
                        Text("Uložit změny", color = Color.White)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Připojení stravy",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = onLinkStrava,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStravaLinked) UpceGreen else StravaOrange
                        ),
                        enabled = !isStravaLinked
                    ) {
                        Text(if (isStravaLinked) "Strava připojena ✅" else "Připojit Strava", color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onSyncClick,
                        enabled = isStravaLinked,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = UpceBlue)
                    ) {
                        Text("Synchronizovat", color = Color.White)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Odhlášení a smazání profilu",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Odhlásit se")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = UpceRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Smazat účet", color = Color.White)
                    }
                }
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Opravdu smazat účet?") },
                    text = { Text("Tato akce je nevratná. Všechna vaše data budou smazána.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                onDelete()
                                showDeleteDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = UpceRed)
                        ) { Text("Smazat", color = Color.White) }
                    },
                    dismissButton = {
                        Button(onClick = { showDeleteDialog = false }) { Text("Zrušit") }
                    }
                )
            }
        }
    }
}

// Žebříček nejlepších běžců celé univerzity nebo týmu
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    allUsers: List<UserStatistics>,
    currentUserId: String,
    currentUserTeam: String,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var selectedTab by remember { mutableStateOf(0) }

    val baseFilteredUsers = if (selectedTab == 0) {
        allUsers
    } else {
        allUsers.filter { it.team == currentUserTeam }
    }

    val rankedFilteredUsers = remember(baseFilteredUsers) {
        baseFilteredUsers.sortedByDescending { it.total_distance }
            .mapIndexed { index, userStatistics ->
                userStatistics.copy(rank = index + 1)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Síň slávy") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        },
        content = { padding ->
            Column(modifier = Modifier.padding(padding)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Nerozhoduje věk ani pohlaví. Zde jsou ti nejlepší z celé univerzity.",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        if (selectedTab == 0) {
                            Button(onClick = { selectedTab = 0 }) {
                                Text("Celá univerzita")
                            }
                        } else {
                            OutlinedButton(onClick = { selectedTab = 0 }) {
                                Text("Celá univerzita")
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (selectedTab == 1) {
                            Button(onClick = { selectedTab = 1 }) {
                                Text("Můj tým ($currentUserTeam)")
                            }
                        } else {
                            OutlinedButton(onClick = { selectedTab = 1 }) {
                                Text("Můj tým ($currentUserTeam)")
                            }
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(rankedFilteredUsers) { user ->
                        val isCurrentUser = user.id == currentUserId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrentUser) UpceRed.copy(alpha = 0.1f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${user.rank}.",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.width(30.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                AsyncImage(
                                    model = if (user.avatar.isNotEmpty()) "https://pb.unitrack.fun/api/files/_pb_users_auth_/${user.id}/${user.avatar}" else "",
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop,
                                    placeholder = painterResource(id = R.drawable.ic_profile_placeholder),
                                    error = painterResource(id = R.drawable.ic_profile_placeholder)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isCurrentUser) "${user.name} (Ty)" else user.name,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = if (isCurrentUser) UpceRed else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = user.team,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                 }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(vertical = 2.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("LVL ${user.level}", fontSize = 10.sp, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f km", user.total_distance / 1000),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    )
}

// Obrazovka s detailními statistikami vzdálenosti a grafem
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistanceStatsScreen(
    userStats: UserStatistics?,
    onBack: () -> Unit,
    dailyActivities: List<UserDailyActivity>
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiky vzdálenosti") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "Průměrná vzdálenost",
                    value = if (userStats != null) String.format(java.util.Locale.US, "%.2f km", userStats.avg_distance / 1000) else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = {}
                )
                StatCard(
                    label = "Nejdelší vzdálenost",
                    value = if (userStats != null) String.format(java.util.Locale.US, "%.2f km", userStats.longest_run / 1000) else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = {}
                )
            }
            DailyActivityChart(dailyActivities)
        }
    }
}

// Sloupcový graf naběhaných kilometrů za posledních 7 dní
@Composable
fun DailyActivityChart(activities: List<UserDailyActivity>) {
    val currentLocale = remember { java.util.Locale.getDefault() }
    val dayFormatter = DateTimeFormatter.ofPattern("EEE", currentLocale)

    val today = LocalDate.now()
    val startDate = today.minusDays(6)

    val activitiesByDate = activities.associateBy { LocalDate.parse(it.day.substring(0, 10)) }

    val chartEntries = (0..6).map { i ->
        val date = startDate.plusDays(i.toLong())
        val distance = activitiesByDate[date]?.total_distance ?: 0
        entryOf(i.toFloat(), (distance.toDouble() / 1000).toFloat())
    }

    val maxDistanceKm = chartEntries.maxOfOrNull { it.y } ?: 0f
    val targetMax = maxDistanceKm * 1.2f

    val dynamicMaxY = when {
        targetMax <= 0f -> 5f
        targetMax <= 5f -> 5f
        targetMax <= 10f -> 10f
        targetMax <= 20f -> 20f
        targetMax <= 50f -> 50f
        else -> (ceil(targetMax / 20.0) * 20).toFloat()
    }

    val yStep = when {
        dynamicMaxY <= 5f -> 1f
        dynamicMaxY <= 10f -> 2f
        dynamicMaxY <= 20f -> 5f
        dynamicMaxY <= 50f -> 10f
        else -> 20f
    }
    val yAxisItemCount = (dynamicMaxY / yStep).toInt() + 1

    val chartEntryModelProducer = remember { ChartEntryModelProducer() }
    LaunchedEffect(chartEntries) {
        chartEntryModelProducer.setEntries(chartEntries)
    }

    val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        val date = startDate.plusDays(value.toLong())
        dayFormatter.format(date).replaceFirstChar { it.titlecase(currentLocale) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Kilometry za posledních 7 dní",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Chart(
                chart = columnChart(
                    columns = listOf(
                        remember(ProgressTeal) {
                            com.patrykandpatrick.vico.core.component.shape.LineComponent(
                                color = ProgressTeal.toArgb(),
                                thicknessDp = 24f,
                                shape = com.patrykandpatrick.vico.core.component.shape.Shapes.roundedCornerShape(allPercent = 30)
                            )
                        }
                    )
                ).apply {
                    axisValuesOverrider = com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider.fixed(
                        minY = 0f,
                        maxY = dynamicMaxY
                    )
                },
                chartModelProducer = chartEntryModelProducer,
                startAxis = rememberStartAxis(
                    itemPlacer = remember(yAxisItemCount) {
                        com.patrykandpatrick.vico.core.axis.AxisItemPlacer.Vertical.default(
                            maxItemCount = yAxisItemCount
                        )
                    },
                    valueFormatter = { value, _ -> String.format(java.util.Locale.US, "%.0f", value) }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = bottomAxisValueFormatter
                ),
                chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
            )
        }
    }
}

// Obrazovka s detailními statistikami času a grafem aktivity
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeStatsScreen(
    userStats: UserStatistics?,
    onBack: () -> Unit,
    dailyActivities: List<UserDailyActivity>
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiky času") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "Průměrná doba",
                    value = if (userStats != null) "${(userStats.avg_time / 60).toInt()}m ${(userStats.avg_time % 60).toInt()}s" else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = {}
                )
                StatCard(
                    label = "Nejdelší doba",
                    value = if (userStats != null) "${userStats.longest_time / 3600}h ${(userStats.longest_time % 3600) / 60}m" else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = {}
                )
            }
            DailyTimeChart(dailyActivities)
        }
    }
}

// Sloupcový graf času stráveného během za posledních 7 dní
@Composable
fun DailyTimeChart(activities: List<UserDailyActivity>) {
    val currentLocale = remember { java.util.Locale.getDefault() }
    val dayFormatter = DateTimeFormatter.ofPattern("EEE", currentLocale)

    val today = LocalDate.now()
    val startDate = today.minusDays(6)

    val activitiesByDate = activities.associateBy { LocalDate.parse(it.day.substring(0, 10)) }

    val chartEntries = (0..6).map { i ->
        val date = startDate.plusDays(i.toLong())
        val duration = activitiesByDate[date]?.total_duration ?: 0
        entryOf(i.toFloat(), (duration.toFloat() / 60f))
    }

    val maxTimeMinutes = chartEntries.maxOfOrNull { it.y } ?: 0f
    val targetMax = maxTimeMinutes * 1.2f

    val dynamicMaxY = when {
        targetMax <= 0f -> 30f
        targetMax <= 30f -> 30f
        targetMax <= 60f -> 60f
        targetMax <= 90f -> 90f
        targetMax <= 120f -> 120f
        else -> (ceil(targetMax / 30.0) * 30).toFloat()
    }

    val yStep = when {
        dynamicMaxY <= 30f -> 5f
        dynamicMaxY <= 60f -> 10f
        dynamicMaxY <= 120f -> 20f
        else -> 30f
    }
    val yAxisItemCount = (dynamicMaxY / yStep).toInt() + 1

    val chartEntryModelProducer = remember { ChartEntryModelProducer() }
    LaunchedEffect(chartEntries) {
        chartEntryModelProducer.setEntries(chartEntries)
    }

    val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        val date = startDate.plusDays(value.toLong())
        dayFormatter.format(date).replaceFirstChar { it.titlecase(currentLocale) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Minuty za posledních 7 dní",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Chart(
                chart = columnChart(
                    columns = listOf(
                        remember(UpceBlue) {
                            com.patrykandpatrick.vico.core.component.shape.LineComponent(
                                color = UpceBlue.toArgb(),
                                thicknessDp = 24f,
                                shape = com.patrykandpatrick.vico.core.component.shape.Shapes.roundedCornerShape(allPercent = 30)
                            )
                        }
                    )
                ).apply {
                    axisValuesOverrider = com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider.fixed(
                        minY = 0f,
                        maxY = dynamicMaxY
                    )
                },
                chartModelProducer = chartEntryModelProducer,
                startAxis = rememberStartAxis(
                    itemPlacer = remember(yAxisItemCount) {
                        com.patrykandpatrick.vico.core.axis.AxisItemPlacer.Vertical.default(
                            maxItemCount = yAxisItemCount
                        )
                    },
                    valueFormatter = { value, _ -> String.format(java.util.Locale.US, "%.0f min", value) }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = bottomAxisValueFormatter
                ),
                chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
            )
        }
    }
}

// Seznam odznaků a úspěchů uživatele
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    achievements: UserAchievements?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val metadataList = listOf(
        AchievementMetadata("badge_patriot", "Srdcař", "Nasbírej celkem 50 km.", 500),
        AchievementMetadata("badge_charity", "Dobré srdce", "Zaznamenej aktivitu 11. října.", 1000),
        AchievementMetadata("badge_morning", "Ranní ptáče", "Začni aktivitu před 6:00.", 300),
        AchievementMetadata("badge_sprinter", "Sprinter", "Uraz aspoň 1 km rychlostí nad 15 km/h.", 400),
        AchievementMetadata("badge_weekend", "Víkendový dříč", "Zaznamenej aktivitu v sobotu i v neděli.", 250),
        AchievementMetadata("badge_marathon", "Maratonec", "Uraz 42,2 km v jedné aktivitě.", 2000),
        AchievementMetadata("badge_halfmarathon", "Půlmaratonec", "Uraz 21,1 km v jedné aktivitě.", 1000),
        AchievementMetadata("badge_unstoppable", "Nezastavitelný", "Zaznamenej aktivitu v 7 různých dnech.", 750)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moje odznaky") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(metadataList) { meta ->
                val isEarned = when (meta.key) {
                    "badge_patriot" -> (achievements?.badge_patriot ?: 0) > 0
                    "badge_charity" -> (achievements?.badge_charity ?: 0) > 0
                    "badge_morning" -> (achievements?.badge_morning ?: 0) > 0
                    "badge_sprinter" -> (achievements?.badge_sprinter ?: 0) > 0
                    "badge_weekend" -> (achievements?.badge_weekend ?: 0) > 0
                    "badge_marathon" -> (achievements?.badge_marathon ?: 0) > 0
                    "badge_halfmarathon" -> (achievements?.badge_halfmarathon ?: 0) > 0
                    "badge_unstoppable" -> (achievements?.badge_unstoppable ?: 0) > 0
                    else -> false
                }

                AchievementItem(meta, isEarned)
            }
        }
    }
}

// Jednotlivá položka odznaku v seznamu
@Composable
fun AchievementItem(meta: AchievementMetadata, isEarned: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEarned) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isEarned) BorderStroke(2.dp, UpceRed) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        if (isEarned) UpceRed.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isEarned) "🏅" else "🔒", fontSize = 24.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                @Suppress("DEPRECATION")
                Text(
                    text = meta.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = if (isEarned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = meta.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+${meta.xp} XP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isEarned) ProgressTeal else Color.Gray
                )
            }
        }
    }
}

data class AchievementMetadata(
    val key: String,
    val name: String,
    val description: String,
    val xp: Int
)