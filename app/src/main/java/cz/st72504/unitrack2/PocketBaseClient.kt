package cz.st72504.unitrack2

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

// --- DATOVÉ TŘÍDY ---
data class AuthMethodsResponse(val authProviders: List<AuthProvider>)
data class AuthProvider(val name: String, val authUrl: String, val codeVerifier: String, val state: String)

data class AuthResponse(val token: String, val record: UserRecord)
data class UserRecord(val id: String, val name: String, val email: String)

data class SyncResponse(val message: String, val saved: Int, val total: Int)
data class ActivityListResponse(val items: List<ActivityRecord>)
data class ActivityRecord(
    val id: String,
    val name: String,
    val distance: Double,
    val duration: Int,
    val start_date: String,
    val activity_type: String
)

// --- SÍŤOVÝ KLIENT ---
class PocketBaseClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://unitrack.xdzubox.xyz"

    suspend fun getAuthProvider(providerName: String): AuthProvider? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val request = Request.Builder().url("$baseUrl/api/collections/users/auth-methods").build()
                val call = client.newCall(request)

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        if (!response.isSuccessful || responseBody == null) {
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }
                        try {
                            val authMethods = gson.fromJson(responseBody, AuthMethodsResponse::class.java)
                            val provider = authMethods.authProviders.find { it.name == providerName }
                            if (continuation.isActive) continuation.resume(provider)
                        } catch (e: Exception) {
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

    suspend fun authWithOAuth2(providerName: String, code: String, codeVerifier: String, redirectUrl: String): AuthResponse? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val jsonBody = """{"provider": "$providerName", "code": "$code", "codeVerifier": "$codeVerifier", "redirectUrl": "$redirectUrl"}"""
                val request = Request.Builder()
                    .url("$baseUrl/api/collections/users/auth-with-oauth2")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val call = client.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        if (!response.isSuccessful || responseBody == null) {
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }
                        try {
                            val authResponse = gson.fromJson(responseBody, AuthResponse::class.java)
                            if (continuation.isActive) continuation.resume(authResponse)
                        } catch (e: Exception) {
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

    suspend fun linkStravaWithCode(pbToken: String, stravaCode: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val jsonBody = """{"code": "$stravaCode"}"""
                val request = Request.Builder()
                    .url("$baseUrl/api/strava-exchange-code")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $pbToken")
                    .build()

                val call = client.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response.isSuccessful)
                    }
                })
                continuation.invokeOnCancellation { call.cancel() }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    suspend fun triggerStravaSync(pbToken: String): SyncResponse? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/strava-fetch")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $pbToken")
                    .build()

                val call = client.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
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

    suspend fun getUserActivities(pbToken: String, userId: String): List<ActivityRecord> {
        return suspendCancellableCoroutine { continuation ->
            try {
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