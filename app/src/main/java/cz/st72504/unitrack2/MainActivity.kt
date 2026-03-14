package cz.st72504.unitrack2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import cz.st72504.unitrack2.ui.theme.UniTrack2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var statusText by mutableStateOf("Stav: Nepřihlášen")
    private var currentCodeVerifier = ""
    private var currentProvider = ""
    private var loggedInPbToken = ""
    private var loggedInUserId = ""
    private var activitiesList by mutableStateOf<List<ActivityRecord>>(emptyList())

    private val pbClient = PocketBaseClient()
    private val redirectUri = "https://unitrack.xdzubox.xyz/redirect.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. NAČTENÍ Z PAMĚTI PŘI STARTU
        val prefs = getSharedPreferences("UniTrackPrefs", MODE_PRIVATE)
        loggedInPbToken = prefs.getString("pbToken", "") ?: ""
        loggedInUserId = prefs.getString("userId", "") ?: ""
        val savedName = prefs.getString("userName", "") ?: ""

        if (loggedInPbToken.isNotEmpty()) {
            statusText = "✅ Přihlášen jako: $savedName"
        }

        setContent {
            UniTrack2Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        status = statusText,
                        onMicrosoftClick = { startOAuthLogin("oidc") },
                        onStravaClick = {
                            if (loggedInPbToken.isEmpty()) {
                                statusText = "Nejdřív se musíš přihlásit!"
                            } else {
                                statusText = "Otevírám Stravu..."
                                val stravaUrl = "https://www.strava.com/oauth/mobile/authorize?client_id=182093&response_type=code&redirect_uri=$redirectUri&scope=activity:read_all&state=strava"
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(stravaUrl)))
                            }
                        },
                        onSyncClick = {
                            if (loggedInPbToken.isEmpty()) return@MainScreen
                            statusText = "Stahuji běhy..."
                            lifecycleScope.launch {
                                val sync = pbClient.triggerStravaSync(loggedInPbToken)
                                val fetchedActs = pbClient.getUserActivities(loggedInPbToken, loggedInUserId)
                                withContext(Dispatchers.Main) {
                                    activitiesList = fetchedActs
                                    statusText = if (sync != null) "✅ Hotovo! Uloženo ${sync.saved} běhů." else "Načteno z DB."
                                }
                            }
                        },
                        onLogoutClick = {
                            loggedInPbToken = ""
                            loggedInUserId = ""
                            activitiesList = emptyList()
                            statusText = "Stav: Nepřihlášen"
                            getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().clear().apply()
                        },
                        activities = activitiesList
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uri = intent.data ?: return
        if (uri.scheme == "unitrack" && uri.host == "oauth2") {
            val code = uri.getQueryParameter("code") ?: return
            val state = uri.getQueryParameter("state")

            lifecycleScope.launch {
                if (state == "strava") {
                    val success = pbClient.linkStravaWithCode(loggedInPbToken, code)
                    withContext(Dispatchers.Main) { statusText = if (success) "✅ Strava připojena!" else "❌ Chyba Stravy." }
                } else {
                    val auth = pbClient.authWithOAuth2(currentProvider, code, currentCodeVerifier, redirectUri)
                    withContext(Dispatchers.Main) {
                        if (auth != null) {
                            loggedInPbToken = auth.token
                            loggedInUserId = auth.record.id
                            statusText = "✅ Přihlášen jako: ${auth.record.name}"
                            getSharedPreferences("UniTrackPrefs", MODE_PRIVATE).edit().apply {
                                putString("pbToken", auth.token)
                                putString("userId", auth.record.id)
                                putString("userName", auth.record.name)
                                apply()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(status: String, onMicrosoftClick: () -> Unit, onStravaClick: () -> Unit, onSyncClick: () -> Unit, onLogoutClick: () -> Unit, activities: List<ActivityRecord>) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = status, fontSize = 16.sp, modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = onMicrosoftClick, modifier = Modifier.fillMaxWidth()) { Text("1. Přihlásit přes Microsoft") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onStravaClick, modifier = Modifier.fillMaxWidth()) { Text("2. Propojit se Stravou") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSyncClick, modifier = Modifier.fillMaxWidth()) { Text("3. Synchronizovat běhy") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onLogoutClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Odhlásit se") }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(activities) { act ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = act.name, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        Text(text = "Délka: ${String.format("%.2f", act.distance / 1000.0)} km | Čas: ${act.duration / 60}:${String.format("%02d", act.duration % 60)}", fontSize = 14.sp)
                        Text(text = "Datum: ${act.start_date.take(10)}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}