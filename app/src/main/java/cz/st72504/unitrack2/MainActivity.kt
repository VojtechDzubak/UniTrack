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

class MainActivity : ComponentActivity() {

    private var statusText by mutableStateOf("Stav: Nepřihlášen")

    private var currentCodeVerifier = ""
    private var currentProvider = ""

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
                        onMicrosoftClick = {
                            startOAuthLogin("oidc") // "oidc" je jméno pro Microsoft v PocketBase
                        },
                        onStravaClick = {
                            startOAuthLogin("strava")
                        }
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
                        cleanUriBuilder.appendQueryParameter(
                            paramName,
                            originalUri.getQueryParameter(paramName)
                        )
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

            if (code != null) {
                statusText = "Ověřuji přihlášení..."
                Log.d("OAuth2", "Získán code: $code pro providera $currentProvider")

                // Spustíme úlohu na pozadí pro dokončení přihlášení
                lifecycleScope.launch {
                    val authResponse = pbClient.authWithOAuth2(
                        providerName = currentProvider,
                        code = code,
                        codeVerifier = currentCodeVerifier,
                        redirectUrl = redirectUri
                    )

                    // Přepneme se zpět na vykreslování UI
                    withContext(Dispatchers.Main) {
                        if (authResponse != null) {
                            // TADY JSME OFICIÁLNĚ PŘIHLÁŠENI!
                            statusText = "✅ Přihlášen jako: ${authResponse.record.name}"
                            Log.d("OAuth2", "Získán token: ${authResponse.token}")

                            // (Zde si později uložíme token, abychom s ním mohli volat Stravu)
                        } else {
                            statusText = "❌ Přihlášení selhalo (Zkontroluj Logcat)."
                        }
                    }
                }
            } else {
                statusText = "Chyba: Kód nebyl vrácen z prohlížeče."
            }
        }
    }
}

// Zde definujeme samotný vzhled aplikace pomocí Jetpack Compose
@Composable
fun MainScreen(status: String, onMicrosoftClick: () -> Unit, onStravaClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = status,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = onMicrosoftClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Přihlásit přes Microsoft")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStravaClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Propojit se Stravou")
        }
    }
}