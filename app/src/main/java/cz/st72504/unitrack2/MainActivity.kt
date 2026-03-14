package cz.st72504.unitrack2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
        setContent {
            UniTrack2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        status = statusText,
                        onMicrosoftClick = { startOAuthLogin("oidc") },
                        onStravaClick = {
                            if (loggedInPbToken.isEmpty()) {
                                statusText = "Nejdřív se musíš přihlásit!"
                            } else {
                                statusText = "Otevírám Stravu..."
                                val stravaUrl = "https://www.strava.com/oauth/mobile/authorize?client_id=182093&response_type=code&redirect_uri=$redirectUri&scope=activity:read_all&state=strava"
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(stravaUrl))
                                startActivity(browserIntent)
                            }
                        },
                        onSyncClick = {
                            if (loggedInPbToken.isEmpty()) {
                                statusText = "Zatím nejsi přihlášený!"
                                return@MainScreen
                            }
                            statusText = "Stahuji běhy z backendu..."

                            lifecycleScope.launch {
                                val sync = pbClient.triggerStravaSync(loggedInPbToken)
                                val fetchedActs = pbClient.getUserActivities(loggedInPbToken, loggedInUserId)

                                withContext(Dispatchers.Main) {
                                    activitiesList = fetchedActs
                                    if (sync != null) {
                                        statusText = "✅ Hotovo! Server uložil ${sync.saved} nových běhů z ${sync.total}."
                                    } else {
                                        statusText = "Načteno ${fetchedActs.size} běhů z databáze."
                                    }
                                }
                            }
                        },
                        activities = activitiesList
                    )
                }
            }
        }
    }

    private fun startOAuthLogin(providerName: String) {
        statusText = "Připojuji se k serveru pro $providerName..."
        currentProvider = providerName

        lifecycleScope.launch {
            val provider = pbClient.getAuthProvider(providerName)

            withContext(Dispatchers.Main) {
                if (provider != null) {
                    statusText = "Otevírám prohlížeč pro $providerName..."
                    currentCodeVerifier = provider.codeVerifier

                    val rawUrl = provider.authUrl + redirectUri
                    val originalUri = Uri.parse(rawUrl)
                    val cleanUriBuilder = originalUri.buildUpon().clearQuery()

                    for (paramName in originalUri.queryParameterNames) {
                        var paramValue = originalUri.getQueryParameter(paramName)
                        if (currentProvider == "strava" && paramName == "scope") {
                            paramValue = "profile:read_all,activity:read_all"
                        }
                        cleanUriBuilder.appendQueryParameter(paramName, paramValue)
                    }

                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, cleanUriBuilder.build())
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        statusText = "Chyba: Nepodařilo se otevřít prohlížeč."
                    }
                } else {
                    statusText = "Chyba: Provider nebyl nalezen."
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val uri = intent.data
        if (uri != null && uri.scheme == "unitrack" && uri.host == "oauth2") {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")

            if (code != null) {
                lifecycleScope.launch {
                    if (state == "strava") {
                        // Návrat ze Stravy
                        withContext(Dispatchers.Main) { statusText = "Předávám Stravu backendu..." }
                        val success = pbClient.linkStravaWithCode(loggedInPbToken, code)
                        withContext(Dispatchers.Main) {
                            statusText = if (success) "✅ Strava úspěšně připojena a zamčena!"
                            else "❌ Backend odmítl propojení Stravy."
                        }
                    } else {
                        // Návrat z Microsoftu
                        withContext(Dispatchers.Main) { statusText = "Ověřuji Microsoft přihlášení..." }
                        val authResponse = pbClient.authWithOAuth2(currentProvider, code, currentCodeVerifier, redirectUri)

                        withContext(Dispatchers.Main) {
                            if (authResponse != null) {
                                loggedInPbToken = authResponse.token
                                loggedInUserId = authResponse.record.id
                                statusText = "✅ Přihlášen přes Microsoft jako: ${authResponse.record.name}"
                            } else {
                                statusText = "❌ Přihlášení přes Microsoft selhalo."
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    status: String,
    onMicrosoftClick: () -> Unit,
    onStravaClick: () -> Unit,
    onSyncClick: () -> Unit,
    activities: List<ActivityRecord>
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = status, fontSize = 16.sp, modifier = Modifier.padding(bottom = 16.dp))

        Button(onClick = onMicrosoftClick, modifier = Modifier.fillMaxWidth()) {
            Text("1. Přihlásit přes Microsoft")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onStravaClick, modifier = Modifier.fillMaxWidth()) {
            Text("2. Propojit se Stravou")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSyncClick, modifier = Modifier.fillMaxWidth()) {
            Text("3. Stáhnout a zobrazit mé běhy")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activities) { act ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = act.name, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        val distanceKm = String.format(Locale.US, "%.2f", act.distance / 1000.0)
                        val mins = act.duration / 60
                        val secs = act.duration % 60
                        val timeFormatted = String.format(Locale.US, "%d:%02d", mins, secs)

                        Text(text = "Délka: $distanceKm km | Čas: $timeFormatted", fontSize = 14.sp)
                        Text(text = "Datum: ${act.start_date.substring(0, 10)}", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}