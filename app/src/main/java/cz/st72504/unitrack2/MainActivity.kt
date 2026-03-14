package cz.st72504.unitrack2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import cz.st72504.unitrack2.ui.theme.UniTrack2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var statusText by mutableStateOf("Stav: Nepřihlášen")

    private var currentCodeVerifier = ""
    private var currentProvider = ""

    private var loggedInPbToken = ""
    private var loggedInUserId = "" // Přidáno: Potřebujeme ID pro filtrování běhů

    // Stavová proměnná pro seznam běhů
    private var activitiesList by mutableStateOf<List<ActivityRecord>>(emptyList())

    // Vytvoříme si naši novou síťovou třídu
    private val pbClient = PocketBaseClient()

    // Naše adresa pro návrat (Deep Link), kterou jsme nastavili v Manifestu a v PocketBase
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
                        // --- LOGIKA PRO STAŽENÍ BĚHŮ ---
                        onSyncClick = {
                            if (loggedInPbToken.isEmpty()) {
                                statusText = "Zatím nejsi přihlášený!"
                                return@MainScreen
                            }
                            statusText = "Stahuji běhy z backendu..."

                            lifecycleScope.launch {
                                // 1. Zatáhneme za páku na serveru
                                val sync = pbClient.triggerStravaSync(loggedInPbToken)

                                // 2. Počkáme a pak vytáhneme data z DB
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
                        activities = activitiesList // Předáváme data do UI
                    )
                }
            }
        }
    }

    // Nová funkce pro spuštění přihlašování
    private fun startOAuthLogin(providerName: String) {
        statusText = "Připojuji se k serveru pro $providerName..."
        currentProvider = providerName

        lifecycleScope.launch {
            val provider = pbClient.getAuthProvider(providerName)

            withContext(Dispatchers.Main) {
                if (provider != null) {
                    Log.d("OAuth2", "Mám URL od providera, jdu otevřít prohlížeč!")
                    statusText = "Otevírám prohlížeč pro $providerName..."

                    currentCodeVerifier = provider.codeVerifier

                    // -- ZAČÁTEK ELEGANTNÍ OPRAVY --
                    // 1. Vezmeme surovou URL z PocketBase a rovnou přidáme náš redirect
                    val rawUrl = provider.authUrl + redirectUri

                    // 2. Necháme Android, ať URL chytře rozebere
                    val originalUri = Uri.parse(rawUrl)
                    val cleanUriBuilder = originalUri.buildUpon().clearQuery()

                    // 3. Projdeme unikátní názvy parametrů.
                    // getQueryParameter automaticky vezme jen tu PRVNÍ hodnotu a duplikáty zahodí.
                    for (paramName in originalUri.queryParameterNames) {
                        var paramValue = originalUri.getQueryParameter(paramName)

                        // FINTA: PocketBase defaultně žádá jen o profil. My ale u Stravy potřebujeme i aktivity!
                        if (currentProvider == "strava" && paramName == "scope") {
                            paramValue = "profile:read_all,activity:read_all"
                        }

                        cleanUriBuilder.appendQueryParameter(paramName, paramValue)
                    }

                    val finalCleanUrl = cleanUriBuilder.build()
                    Log.d("OAuth2", "Vyčištěná URL: $finalCleanUrl")
                    // -- KONEC OPRAVY --

                    try {
                        // Otevíráme už tu vyčištěnou URL
                        val browserIntent = Intent(Intent.ACTION_VIEW, finalCleanUrl)
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        Log.e("OAuth2", "Chyba při otevírání prohlížeče: ${e.message}")
                        statusText = "Chyba: Nepodařilo se otevřít prohlížeč."
                    }
                } else {
                    Log.e("OAuth2", "Provider je null, přestože síť prošla")
                    statusText = "Chyba: Provider nebyl nalezen."
                }
            }
        }
    }

    // ... Zbytek třídy (onNewIntent a MainScreen) zůstává ÚPLNĚ STEJNÝ jako v předchozím kroku ...

    // Tato metoda zachytí návrat z webového prohlížeče
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val uri = intent.data
        if (uri != null && uri.scheme == "unitrack" && uri.host == "oauth2") {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state") // Zjistíme z URL, odkud se vracíme

            if (code != null) {
                lifecycleScope.launch {

                    // --- SCÉNÁŘ 1: NÁVRAT ZE STRAVY ---
// --- SCÉNÁŘ 1: NÁVRAT ZE STRAVY ---
                    if (state == "strava") {
                        withContext(Dispatchers.Main) { statusText = "Předávám Stravu backendu..." }

                        // Pošleme POUZE kód na náš nový backendový endpoint
                        val success = pbClient.linkStravaWithCode(loggedInPbToken, code)

                        withContext(Dispatchers.Main) {
                            statusText = if (success) "✅ Strava úspěšně připojena a zamčena!"
                            else "❌ Backend odmítl propojení Stravy (viz logy)."
                        }
                    }

                    // --- SCÉNÁŘ 2: NÁVRAT Z MICROSOFTU ---
                    else {
                        withContext(Dispatchers.Main) { statusText = "Ověřuji Microsoft přihlášení..." }

                        val authResponse = pbClient.authWithOAuth2(
                            providerName = currentProvider,
                            code = code,
                            codeVerifier = currentCodeVerifier,
                            redirectUrl = redirectUri
                        )

                        withContext(Dispatchers.Main) {
                            // ... (uvnitř scénáře 2 návratu z Microsoftu)
                            if (authResponse != null) {
                                loggedInPbToken = authResponse.token
                                loggedInUserId = authResponse.record.id // ZDE UKLÁDÁME ID
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

// Zde definujeme samotný vzhled aplikace pomocí Jetpack Compose
@Composable
fun MainScreen(
    status: String,
    onMicrosoftClick: () -> Unit,
    onStravaClick: () -> Unit,
    onSyncClick: () -> Unit, // Nové tlačítko pro sync
    activities: List<ActivityRecord> // Naše data
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

        // Scrollovací seznam, pokud máme nějaká data
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

                        // Přepočet ze Stravy: vzdálenost je v metrech, čas v sekundách
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