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
data class AuthProvider(
    val name: String,
    val authUrl: String,
    val codeVerifier: String,
    val state: String
)

data class AuthResponse(val token: String, val record: UserRecord)
data class UserRecord(val id: String, val name: String, val email: String)


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
}