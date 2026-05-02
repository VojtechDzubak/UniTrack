package cz.st72504.unitrack2

import android.util.Log
import com.google.gson.Gson
import cz.st72504.unitrack2.model.*
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

// --- SÍŤOVÝ KLIENT ---

// Klient pro komunikaci s PocketBase API
class PocketBaseClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val baseUrl = "https://pb.unitrack.fun"

    // Získá URL pro přihlášení přes Microsoft
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

    // Dokončí OAuth2 přihlášení a získá token
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

    // Propojí účet se Stravou
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

    // Spustí synchronizaci běhů ze Stravy
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

    // Načte aktivity přihlášeného uživatele
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

    // Načte veřejné aktivity všech uživatelů
    suspend fun getPublicActivities(pbToken: String = ""): List<PublicActivityRecord> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val url = "$baseUrl/api/collections/activities_public/records?sort=-start_date&perPage=20"
                val requestBuilder = Request.Builder().url(url).get()

                // PŘIDAT TOKEN JEN POKUD EXISTUJE
                if (pbToken.isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $pbToken")
                }
                val request = requestBuilder.build()

                val call = client.newCall(request)

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val body = response.body?.string()
                        if (response.isSuccessful && body != null) {
                            try {
                                val data = gson.fromJson(body, PublicActivityListResponse::class.java)
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
    
    // Načte statistiky konkrétního uživatele
    suspend fun getUserStatistics(pbToken: String, userId: String): UserStatistics? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val url = "$baseUrl/api/collections/user_statistics/records/$userId"
                val request = Request.Builder()
                    .url(url)
                    .get()
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
                                val data = gson.fromJson(body, UserStatistics::class.java)
                                if (continuation.isActive) continuation.resume(data)
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

    // Načte statistiky všech uživatelů pro žebříček
    suspend fun getAllUserStatistics(pbToken: String): List<UserStatistics> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val url = "$baseUrl/api/collections/user_statistics/records?perPage=500"
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
                                val data = gson.fromJson(body, UserStatisticsListResponse::class.java)
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

    // Načte statistiky fakult
    suspend fun getTeamStatistics(pbToken: String): List<TeamStatistics> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val url = "$baseUrl/api/collections/team_rank_advanced/records?sort=-total_distance"
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
                                val data = gson.fromJson(body, TeamStatisticsListResponse::class.java)
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

    // Načte přehled odznaků
    suspend fun getUserAchievements(pbToken: String, userId: String): UserAchievements? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val url = "$baseUrl/api/collections/user_achievements/records/$userId"
                val request = Request.Builder()
                    .url(url)
                    .get()
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
                                val data = gson.fromJson(body, UserAchievements::class.java)
                                if (continuation.isActive) continuation.resume(data)
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

    // Načte data aktuálního uživatele
    suspend fun getMe(pbToken: String, userId: String): UserRecord? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/collections/users/records/$userId")
                    .get()
                    .addHeader("Authorization", "Bearer $pbToken")
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && responseBody != null) {
                            val userRecord = gson.fromJson(responseBody, UserRecord::class.java)
                            if (continuation.isActive) continuation.resume(userRecord)
                        } else {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                })
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    // Aktualizuje profil (jméno, tým, avatar)
    suspend fun updateUserProfile(
        pbToken: String,
        userId: String,
        name: String,
        team: String,
        isPublic: Boolean,
        imageBytes: ByteArray? = null
    ): UserRecord? {
        return suspendCancellableCoroutine { continuation ->
            try {
                val builder = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("name", name)
                    .addFormDataPart("team", team)
                    .addFormDataPart("public", isPublic.toString())

                if (imageBytes != null) {
                    builder.addFormDataPart(
                        "avatar",
                        "avatar.jpg",
                        imageBytes.toRequestBody("image/jpeg".toMediaType())
                    )
                }

                val request = Request.Builder()
                    .url("$baseUrl/api/collections/users/records/$userId")
                    .patch(builder.build())
                    .addHeader("Authorization", "Bearer $pbToken")
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && responseBody != null) {
                            val updatedRecord = gson.fromJson(responseBody, UserRecord::class.java)
                            if (continuation.isActive) continuation.resume(updatedRecord)
                        } else {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                })
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    // Smaže účet
    suspend fun deleteUser(pbToken: String, userId: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/collections/users/records/$userId")
                    .delete()
                    .addHeader("Authorization", "Bearer $pbToken")
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response.isSuccessful)
                    }
                })
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    // Načte denní aktivity za posledních 7 dní
    suspend fun getUserDailyActivities(pbToken: String, userId: String): List<UserDailyActivity> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val url = "$baseUrl/api/collections/user_daily_activities/records?filter=(user='$userId')&sort=-day&perPage=7"
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
                                val data = gson.fromJson(body, UserDailyActivityListResponse::class.java)
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
