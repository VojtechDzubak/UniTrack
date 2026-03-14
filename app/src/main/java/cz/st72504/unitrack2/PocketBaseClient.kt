package cz.st72504.unitrack2

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class AuthMethodsResponse(val authProviders: List<AuthProvider>)
data class StravaTokenResponse(val access_token: String, val refresh_token: String, val athlete: StravaRawUser)
data class AuthProvider(
    val name: String,
    val authUrl: String,
    val codeVerifier: String,
    val state: String
)

// Rozšířené třídy pro rozluštění odpovědi (včetně originálních tokenů ze Stravy)
data class AuthResponse(val token: String, val record: UserRecord, val meta: AuthMeta?)
data class AuthMeta(val accessToken: String?, val refreshToken: String?, val rawUser: StravaRawUser?)
data class StravaRawUser(val id: Long)
data class UserRecord(val id: String, val name: String, val email: String)

// Třídy pro odpověď z tvého /api/strava-fetch
data class SyncResponse(val message: String, val saved: Int, val total: Int)

// Třídy pro čtení aktivit z databáze
data class ActivityListResponse(val items: List<ActivityRecord>)
data class ActivityRecord(
    val id: String,
    val name: String,
    val distance: Double,
    val duration: Int,
    val start_date: String,
    val activity_type: String
)



class PocketBaseClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://unitrack.xdzubox.xyz"

    // Používáme suspendCancellableCoroutine pro bezpečné čekání na odpověď od OkHttp
    suspend fun getAuthProvider(providerName: String): AuthProvider? {
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d("PBClient", "Začínám volat server: $baseUrl/api/collections/users/auth-methods")

                val request = Request.Builder()
                    .url("$baseUrl/api/collections/users/auth-methods")
                    .build()

                // Zavoláme OkHttp a "uspíme" naši coroutine, dokud nepřijde odpověď
                val call = client.newCall(request)

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("PBClient", "Spadla síťová komunikace: ${e.message}")
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        Log.d("PBClient", "Odpověď serveru: OK") // zkráceno, ať nám to nespamuje logy

                        if (!response.isSuccessful || responseBody == null) {
                            Log.e("PBClient", "Server vrátil chybu nebo je body prázdné!")
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }

                        try {
                            val authMethods = gson.fromJson(responseBody, AuthMethodsResponse::class.java)
                            val provider = authMethods.authProviders.find { it.name == providerName }

                            if (provider != null) {
                                Log.d("PBClient", "Úspěšně vracím providera: ${provider.name}")
                                // Resume() vrátí hodnotu zpět do MainActivity a probudí ho
                                if (continuation.isActive) continuation.resume(provider)
                            } else {
                                Log.e("PBClient", "Provider '$providerName' nebyl nalezen.")
                                if (continuation.isActive) continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            Log.e("PBClient", "Chyba při parsování JSONu: ${e.message}")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                })

                // Pokud by byla coroutine zrušena, zrušíme i request na síti
                continuation.invokeOnCancellation {
                    call.cancel()
                }

            } catch (e: Exception) {
                Log.e("PBClient", "Neočekávaná chyba: ${e.message}")
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    // Funkce pro finální potvrzení přihlášení (Odeslání kódu na backend)
    suspend fun authWithOAuth2(providerName: String, code: String, codeVerifier: String, redirectUrl: String): AuthResponse? {
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d("PBClient", "Odesílám kód na server pro ověření...")

                // Vytvoříme JSON tělo požadavku
                val jsonBody = """
                    {
                        "provider": "$providerName",
                        "code": "$code",
                        "codeVerifier": "$codeVerifier",
                        "redirectUrl": "$redirectUrl"
                    }
                """.trimIndent()

                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/api/collections/users/auth-with-oauth2")
                    .post(requestBody)
                    .build()

                val call = client.newCall(request)

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("PBClient", "Chyba při finálním přihlášení: ${e.message}")
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()

                        if (!response.isSuccessful || responseBody == null) {
                            Log.e("PBClient", "Server odmítl kód. HTTP ${response.code}: $responseBody")
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }

                        try {
                            // Přeložíme úspěšnou odpověď do našeho objektu
                            val authResponse = gson.fromJson(responseBody, AuthResponse::class.java)
                            Log.d("PBClient", "Vše klaplo! Uživatel přihlášen: ${authResponse.record.name}")
                            if (continuation.isActive) continuation.resume(authResponse)
                        } catch (e: Exception) {
                            Log.e("PBClient", "Chyba parsování finálního JSONu: ${e.message}")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                })

                continuation.invokeOnCancellation { call.cancel() }

            } catch (e: Exception) {
                Log.e("PBClient", "Neočekávaná chyba: ${e.message}")
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    // Funkce pro uložení Strava tokenů do tvého zabezpečeného trezoru
    // Funkce pro uložení Strava tokenů do tvého zabezpečeného trezoru
    suspend fun saveStravaTokens(pbToken: String, accessToken: String, refreshToken: String, athleteId: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d("PBClient", "Posílám Strava tokeny do trezoru...")

                val jsonBody = """
                    {
                        "access_token": "$accessToken",
                        "refresh_token": "$refreshToken",
                        "athlete_id": "$athleteId"
                    }
                """.trimIndent()

                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$baseUrl/api/strava-save-tokens")
                    .post(requestBody)
                    // ZDE JE KLÍČOVÉ PŘIDAT TVŮJ POCKETBASE TOKEN PRO AUTORIZACI!
                    .addHeader("Authorization", "Bearer $pbToken")
                    .build()

                // OPRAVA: Zde si požadavek musíme uložit do proměnné 'call'
                val call = client.newCall(request)

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("PBClient", "Chyba sítě při ukládání Strava dat: ${e.message}")
                        if (continuation.isActive) continuation.resume(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        Log.d("PBClient", "Odpověď trezoru: ${response.code}")
                        // Pokud server vrátí 200, uložení proběhlo úspěšně
                        if (continuation.isActive) continuation.resume(response.isSuccessful)
                    }
                })

                // Nyní už ví, co je to 'call' a může to správně zrušit
                continuation.invokeOnCancellation { call.cancel() }
            } catch (e: Exception) {
                Log.e("PBClient", "Neočekávaná chyba (Strava trezor): ${e.message}")
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    // Nová funkce: Výměna kódu za tokeny PŘÍMO u Stravy (mimo PocketBase)
    suspend fun exchangeStravaCode(code: String): StravaTokenResponse? {
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d("PBClient", "Měním Strava kód za finální tokeny...")

                val jsonBody = """
                    {
                        "client_id": "182093",
                        "client_secret": "c740db2fa7c9effbd21a387f5440d872a97602e1",
                        "code": "$code",
                        "grant_type": "authorization_code"
                    }
                """.trimIndent()

                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://www.strava.com/api/v3/oauth/token")
                    .post(requestBody)
                    .build()

                val call = client.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("PBClient", "Chyba při komunikaci se Stravou: ${e.message}")
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        if (!response.isSuccessful || responseBody == null) {
                            Log.e("PBClient", "Strava odmítla kód: $responseBody")
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }
                        try {
                            val stravaData = gson.fromJson(responseBody, StravaTokenResponse::class.java)
                            if (continuation.isActive) continuation.resume(stravaData)
                        } catch (e: Exception) {
                            Log.e("PBClient", "Chyba parsování Strava JSONu: ${e.message}")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                })
                continuation.invokeOnCancellation { call.cancel() }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    // 1. Zatahá za páku na backendu (spustí strava.pb.js)
    suspend fun triggerStravaSync(pbToken: String): SyncResponse? {
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d("PBClient", "Spouštím synchronizaci Stravy na backendu...")
                val request = Request.Builder()
                    .url("$baseUrl/api/strava-fetch")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $pbToken")
                    .build()

                val call = client.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("PBClient", "Chyba sítě při synchronizaci: ${e.message}")
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string()
                        if (response.isSuccessful && body != null) {
                            try {
                                val syncData = gson.fromJson(body, SyncResponse::class.java)
                                if (continuation.isActive) continuation.resume(syncData)
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        } else {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                })
                continuation.invokeOnCancellation { call.cancel() }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    // 2. Stáhne uložené aktivity uživatele z databáze PocketBase
    suspend fun getUserActivities(pbToken: String, userId: String): List<ActivityRecord> {
        return suspendCancellableCoroutine { continuation ->
            try {
                // Filtrujeme jen na aktivity tohoto uživatele a řadíme od nejnovějších
                val url = "$baseUrl/api/collections/activities/records?sort=-start_date&filter=(user='$userId')"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer $pbToken")
                    .build()

                val call = client.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string()
                        if (response.isSuccessful && body != null) {
                            try {
                                val data = gson.fromJson(body, ActivityListResponse::class.java)
                                if (continuation.isActive) continuation.resume(data.items)
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resume(emptyList())
                            }
                        } else {
                            if (continuation.isActive) continuation.resume(emptyList())
                        }
                    }
                })
                continuation.invokeOnCancellation { call.cancel() }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(emptyList())
            }
        }
    }

}