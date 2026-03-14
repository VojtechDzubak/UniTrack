package cz.st72504.unitrack2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import cz.st72504.unitrack2.ui.theme.UniTrack2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// --- DESIGN SYSTÉM ---
val UpceRed = Color(0xFFE32A22)
val UpceBlue = Color(0xFF009EE3)
val UpceGreen = Color(0xFF00A651)
val StravaOrange = Color(0xFFFC4C02)
val LightBg = Color(0xFFF9FAFB)

class MainActivity : ComponentActivity() {

    private var statusText by mutableStateOf("Stav: Nepřihlášen")
    private var activeTab by mutableStateOf("moje_vysledky")
    private var showRegistrationForm by mutableStateOf(false)
    private var userName by mutableStateOf("")
    private var userTeam by mutableStateOf("")
    private var userIsPublic by mutableStateOf(true)
    private var userAvatarUrl by mutableStateOf("")
    private var userStats by mutableStateOf<UserStatistics?>(null)

    private var isStravaLinked by mutableStateOf(false)
    private var loggedInPbToken by mutableStateOf("")
    private var loggedInUserId by mutableStateOf("")
    private var activitiesList by mutableStateOf<List<ActivityRecord>>(emptyList())
    private var showSettingsScreen by mutableStateOf(false)

    private val pbClient = PocketBaseClient()
    private val redirectUri = "https://unitrack.xdzubox.xyz/redirect.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            fetchUserStats()
        }

        setContent {
            UniTrack2Theme {
                val isLoggedIn = loggedInPbToken.isNotEmpty()
                val snackbarHostState = remember { SnackbarHostState() }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showSettingsScreen) {
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
                            onLinkStrava = { openStravaBrowser() }
                        )
                    } else {
                        MainScreen(
                            isLoggedIn = isLoggedIn,
                            activeTab = activeTab,
                            onTabChange = { activeTab = it },
                            showRegistration = showRegistrationForm,
                            activities = activitiesList,
                            userStats = userStats,
                            onMicrosoftClick = { startOAuthLogin("oidc") },
                            onSaveProfile = { name, team, isPublic, avatarBytes -> saveProfile(name, team, isPublic, avatarBytes) },
                            userName = userName,
                            userTeam = userTeam,
                            userAvatarUrl = userAvatarUrl,
                            isStravaLinked = isStravaLinked,
                            onSyncClick = { triggerSync(snackbarHostState) },
                            onSettingsClick = { showSettingsScreen = true },
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        }
    }
    
    private fun fetchUserStats() {
        if (loggedInPbToken.isNotEmpty() && loggedInUserId.isNotEmpty()) {
            lifecycleScope.launch {
                val stats = pbClient.getUserStatistics(loggedInPbToken, loggedInUserId)
                withContext(Dispatchers.Main) {
                    userStats = stats
                }
            }
        }
    }

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
            val fetchedActs = pbClient.getUserActivities(loggedInPbToken, loggedInUserId)
            withContext(Dispatchers.Main) {
                activitiesList = fetchedActs
                fetchUserStats()
                val message = if (sync != null) "Hotovo! Uloženo ${sync.saved} nových aktivit." else "Načteno z DB."
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    private fun saveProfile(name: String, team: String, isPublic: Boolean, avatarBytes: ByteArray?) {
        statusText = "Ukládám profil na server..."
        lifecycleScope.launch {
            val updatedRecord = pbClient.updateUserProfile(loggedInPbToken, loggedInUserId, name, team, isPublic, avatarBytes)
            withContext(Dispatchers.Main) {
                if (updatedRecord != null) {
                    val avatarUrl = updatedRecord.avatar?.let { "https://unitrack.xdzubox.xyz/api/files/_pb_users_auth_/${updatedRecord.id}/$it" } ?: ""
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
                    val avatarUrl = updatedRecord.avatar?.let { "https://unitrack.xdzubox.xyz/api/files/_pb_users_auth_/${updatedRecord.id}/$it" } ?: ""
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
                    showSettingsScreen = false // Navigate back
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
                    statusText = "Účet smazán."
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
        activitiesList = emptyList()
        isStravaLinked = false
        showRegistrationForm = false
        showSettingsScreen = false
        userStats = null
        statusText = "Stav: Nepřihlášen"
        getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().clear().apply()
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
                            if(user != null) {
                                prefs.edit().putString("stravaId", user.strava_athlete_id ?: "").apply()
                                statusText = "✅ Strava připojena!"
                                isStravaLinked = true
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
                            userAvatarUrl = auth.record.avatar?.let { "https://unitrack.xdzubox.xyz/api/files/_pb_users_auth_/${auth.record.id}/$it" } ?: ""
                            statusText = "✅ Přihlášen"
                            isStravaLinked = !auth.record.strava_athlete_id.isNullOrEmpty()

                            if (auth.record.team.isNullOrEmpty()) {
                                showRegistrationForm = true
                            } else {
                                fetchUserStats()
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
                        }
                    }
                }
            }
        }
    }
}

// --- UI KOMPONENTY ---

@Composable
fun MainScreen(
    isLoggedIn: Boolean,
    activeTab: String,
    onTabChange: (String) -> Unit,
    showRegistration: Boolean,
    activities: List<ActivityRecord>,
    userStats: UserStatistics?,
    onMicrosoftClick: () -> Unit,
    onSaveProfile: (String, String, Boolean, ByteArray?) -> Unit,
    userName: String,
    userTeam: String,
    userAvatarUrl: String,
    isStravaLinked: Boolean,
    onSyncClick: (snackbarHostState: SnackbarHostState) -> Unit,
    onSettingsClick: () -> Unit,
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
                    label = { Text("Celkové", fontWeight = FontWeight.Bold) },
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
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (activeTab == "celkove") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Zde budou celkové výsledky...", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
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
                        isStravaLinked = isStravaLinked,
                        onSyncClick = onSyncClick,
                        onSettingsClick = onSettingsClick,
                        activities = activities,
                        userStats = userStats,
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}

@Composable
fun LoggedOutView(onMicrosoftClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Tato sekce je uzamčena", fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text(
            "Pro zobrazení statistik se musíš přihlásit.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Button(
            onClick = onMicrosoftClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F2F2F))
        ) {
            Text("Přihlásit se přes Microsoft", fontWeight = FontWeight.ExtraBold)
        }
    }
}

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

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Dokončení profilu", fontSize = 26.sp, fontWeight = FontWeight.Black, color = UpceRed)
        Text("Nastav si údaje pro univerzitní výzvu", color = Color.Gray, modifier = Modifier.padding(bottom = 32.dp))

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
                modifier = Modifier.menuAnchor().fillMaxWidth(),
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
                Text("Veřejný profil", fontWeight = FontWeight.Bold)
                Text("Ostatní uvidí tvé jméno v žebříčku", fontSize = 12.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (name.isNotEmpty() && team.isNotEmpty()) onSave(name, team, isPublic, avatarBytes) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = UpceRed)
        ) {
            Text("Uložit a začít běhat", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun MyResultsView(
    userName: String,
    userTeam: String,
    userAvatarUrl: String,
    isStravaLinked: Boolean,
    onSyncClick: (snackbarHostState: SnackbarHostState) -> Unit,
    onSettingsClick: () -> Unit,
    activities: List<ActivityRecord>,
    userStats: UserStatistics?,
    snackbarHostState: SnackbarHostState
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
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
                placeholder = painterResource(id = R.drawable.ic_launcher_background), // Placeholder image
                error = painterResource(id = R.drawable.ic_launcher_background) // Error image
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text("Nastavení", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Button(
                onClick = {
                    if (isStravaLinked) {
                        onSyncClick(snackbarHostState)
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            snackbarHostState.showSnackbar("Pro synchronizaci nejprve připojte Stravu v nastavení!")
                        }
                    }
                },
                enabled = isStravaLinked,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Sync", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                label = "Úroveň - LVL a XP",
                value = if (userStats != null) "${userStats.level}. LVL (${userStats.current_level_xp}/${userStats.xp_for_next_level} XP)" else "Načítání...",
                modifier = Modifier.fillMaxWidth(),
                onClick = { /* TODO: Handle click */ }
            )
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "Celková vzdálenost",
                    value = if (userStats != null) String.format(Locale.US, "%.2f km", userStats.total_distance / 1000) else "Načítání...",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = {}
                )
                StatCard(
                    label = "Celková doba",
                    value = if (userStats != null) "${userStats.total_time / 3600}h ${ (userStats.total_time % 3600) / 60}m" else "Načítání...",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = {}
                )
            }
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "Umístění",
                    value = userStats?.rank?.toString() ?: "Načítání...",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = {}
                )
                StatCard(
                    label = "Počet odznaků",
                    value = "N/A",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = {}
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "NEDÁVNÉ BĚHY",
            modifier = Modifier.padding(bottom = 12.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(activities) { act ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).background(UpceRed.copy(0.1f), CircleShape),
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
                                text = "${String.format("%.2f", act.distance / 1000.0)} km • ${act.duration / 60} min",
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
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

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
    onLinkStrava: () -> Unit
) {
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Profile Section ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
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
                                placeholder = painterResource(id = R.drawable.ic_launcher_background),
                                error = painterResource(id = R.drawable.ic_launcher_background)
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
                        Text("Veřejný profil")
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onSave(currentName, currentIsPublic, avatarBytes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Uložit změny")
                    }
                }
            }

            // --- Connections Section ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Připojení stravy",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onLinkStrava,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStravaLinked) UpceGreen else StravaOrange
                        ),
                        enabled = !isStravaLinked
                    ) {
                        Text(if (isStravaLinked) "Strava připojena ✅" else "Připojit Strava")
                    }
                }
            }

            // --- Account Actions Section ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Odhlášení a smazání profilu",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface // Explicitly set text color
                        )
                    ) {
                        Text("Odhlásit se")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Smazat účet", color = Color.White) // Explicitly set text color
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) { Text("Smazat") }
                    },
                    dismissButton = {
                        Button(onClick = { showDeleteDialog = false }) { Text("Zrušit") }
                    }
                )
            }
        }
    }
}