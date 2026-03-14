package cz.st72504.unitrack2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import cz.st72504.unitrack2.ui.theme.UniTrack2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cz.st72504.unitrack2.ui.theme.*
import java.util.Locale

// --- DESIGN SYSTÉM ---
val UpceRed = Color(0xFFE32A22)
val UpceBlue = Color(0xFF009EE3)
val UpceGreen = Color(0xFF00A651) // Přidáno pro potvrzené stavy
val StravaOrange = Color(0xFFFC4C02)
val LightBg = Color(0xFFF9FAFB)

class MainActivity : ComponentActivity() {

    private var statusText by mutableStateOf("Stav: Nepřihlášen")
    private var activeTab by mutableStateOf("moje_vysledky")
    private var showRegistrationForm by mutableStateOf(false)

    private var isStravaLinked by mutableStateOf(false)
    private var currentCodeVerifier = ""
    private var currentProvider = ""
    private var loggedInPbToken = ""
    private var loggedInUserId = ""
    private var activitiesList by mutableStateOf<List<ActivityRecord>>(emptyList())

    private val pbClient = PocketBaseClient()
    private val redirectUri = "https://unitrack.xdzubox.xyz/redirect.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("UniTrackPrefs", MODE_PRIVATE)
        loggedInPbToken = prefs.getString("pbToken", "") ?: ""
        loggedInUserId = prefs.getString("userId", "") ?: ""
        val savedName = prefs.getString("userName", "") ?: ""
        val savedTeam = prefs.getString("userTeam", "") ?: ""

        // Načtení stavu Stravy z paměti
        val savedStravaId = prefs.getString("stravaId", "") ?: ""
        isStravaLinked = savedStravaId.isNotEmpty()

        if (loggedInPbToken.isNotEmpty()) {
            statusText = "✅ Přihlášen jako: $savedName"
            if (savedTeam.isEmpty()) showRegistrationForm = true
        }

        setContent {
            UniTrack2Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        status = statusText,
                        activeTab = activeTab,
                        onTabChange = { activeTab = it },
                        showRegistration = showRegistrationForm,
                        isStravaLinked = isStravaLinked,
                        activities = activitiesList,
                        onMicrosoftClick = { startOAuthLogin("oidc") },
                        onStravaClick = { openStravaBrowser() },
                        onSyncClick = { triggerSync() },
                        onLogoutClick = { logout() },
                        onSaveProfile = { name, team, isPublic -> saveProfile(name, team, isPublic) }
                    )
                }
            }
        }
    }

    private fun startOAuthLogin(providerName: String) {
        currentProvider = providerName
        lifecycleScope.launch {
            val provider = pbClient.getAuthProvider(providerName)
            withContext(Dispatchers.Main) {
                if (provider != null) {
                    currentCodeVerifier = provider.codeVerifier
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

    private fun triggerSync() {
        if (loggedInPbToken.isEmpty()) return
        statusText = "Stahuji běhy..."
        lifecycleScope.launch {
            val sync = pbClient.triggerSync(loggedInPbToken)
            val fetchedActs = pbClient.getUserActivities(loggedInPbToken, loggedInUserId)
            withContext(Dispatchers.Main) {
                activitiesList = fetchedActs
                statusText = if (sync != null) "✅ Hotovo! Uloženo ${sync.saved} běhů." else "Načteno z DB."
            }
        }
    }

    private fun saveProfile(name: String, team: String, isPublic: Boolean) {
        statusText = "Ukládám profil na server..."
        lifecycleScope.launch {
            val success = pbClient.updateUserProfile(loggedInPbToken, loggedInUserId, name, team, isPublic)
            withContext(Dispatchers.Main) {
                if (success) {
                    getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().apply {
                        putString("userName", name)
                        putString("userTeam", team)
                        apply()
                    }
                    showRegistrationForm = false
                    statusText = "✅ Profil úspěšně nastaven!"
                } else {
                    statusText = "❌ Nepodařilo se uložit data na server."
                }
            }
        }
    }

    private fun logout() {
        loggedInPbToken = ""
        loggedInUserId = ""
        activitiesList = emptyList()
        isStravaLinked = false
        showRegistrationForm = false
        statusText = "Stav: Nepřihlášen"
        getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().clear().apply()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        if (uri.scheme == "unitrack" && uri.host == "oauth2") {
            val code = uri.getQueryParameter("code") ?: return
            val state = uri.getQueryParameter("state")

            lifecycleScope.launch {
                if (state == "strava") {
                    val success = pbClient.linkStravaWithCode(loggedInPbToken, code)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            statusText = "✅ Strava připojena!"
                            isStravaLinked = true
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
                            statusText = "✅ Přihlášen"
                            isStravaLinked = !auth.record.strava_athlete_id.isNullOrEmpty()

                            if (auth.record.team.isNullOrEmpty()) {
                                showRegistrationForm = true
                            }

                            getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().apply {
                                putString("pbToken", auth.token)
                                putString("userId", auth.record.id)
                                putString("userName", auth.record.name)
                                putString("userTeam", auth.record.team ?: "")
                                putString("stravaId", auth.record.strava_athlete_id ?: "")
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
    status: String,
    activeTab: String,
    onTabChange: (String) -> Unit,
    showRegistration: Boolean,
    isStravaLinked: Boolean,
    activities: List<ActivityRecord>,
    onMicrosoftClick: () -> Unit,
    onStravaClick: () -> Unit,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSaveProfile: (String, String, Boolean) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                // OPRAVA: Místo Color.White použijeme barvu povrchu ze schématu
                containerColor = MaterialTheme.colorScheme.surface,
                // Volitelné: přidá jemný stín/tónování
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == "celkove",
                    onClick = { onTabChange("celkove") },
                    icon = { Text("🏆", fontSize = 20.sp) },
                    label = { Text("Celkové", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UpceRed,
                        selectedTextColor = UpceRed,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = UpceRed.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == "moje_vysledky",
                    onClick = { onTabChange("moje_vysledky") },
                    icon = { Text("🏃", fontSize = 20.sp) },
                    label = { Text("Moje výsledky", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = UpceRed,
                        selectedTextColor = UpceRed,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = UpceRed.copy(alpha = 0.1f)
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
        ) {
            if (activeTab == "celkove") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Zde budou celkové výsledky...", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            } else {
                if (status.contains("Nepřihlášen")) {
                    LoggedOutView(onMicrosoftClick)
                } else if (showRegistration) {
                    RegistrationForm(onSaveProfile)
                } else {
                    MyResultsView(status, isStravaLinked, onStravaClick, onSyncClick, onLogoutClick, activities)
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
fun RegistrationForm(onSave: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var team by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    val teams = listOf("DFJP", "FES", "FEI", "FCHT", "FF", "FR", "FZS", "Rektorát")

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Dokončení profilu", fontSize = 26.sp, fontWeight = FontWeight.Black, color = UpceRed)
        Text("Nastav si údaje pro univerzitní výzvu", color = Color.Gray, modifier = Modifier.padding(bottom = 32.dp))

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
            onClick = { if(name.isNotEmpty() && team.isNotEmpty()) onSave(name, team, isPublic) },
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
    status: String,
    isStravaLinked: Boolean,
    onStravaClick: () -> Unit,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit,
    activities: List<ActivityRecord>
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // Status Badge - používá barvy schématu pro text
        Surface(
            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(50.dp)
        ) {
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStravaClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStravaLinked) UpceGreen else StravaOrange
                )
            ) {
                Text(text = if (isStravaLinked) "Strava ✅" else "Strava", fontWeight = FontWeight.Black)
            }
            Button(
                onClick = onSyncClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UpceBlue)
            ) {
                Text("Sync", fontWeight = FontWeight.Black)
            }
        }

        Text(
            text = "NEDÁVNÉ BĚHY",
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
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

        TextButton(onClick = onLogoutClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text("Odhlásit se", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}
