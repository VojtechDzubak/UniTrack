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

// Poskytovatelé přihlášení (Microsoft atd.)
data class AuthMethodsResponse(val authProviders: List<AuthProvider>)
data class AuthProvider(val name: String, val authUrl: String, val codeVerifier: String, val state: String)

// Odpověď po přihlášení (token + data uživatele)
data class AuthResponse(val token: String, val record: UserRecord)

// Základní záznam uživatele
data class UserRecord(
    val id: String,
    val name: String,
    val email: String,
    val team: String?,
    val public: Boolean?,
    val avatar: String?,
    val strava_athlete_id: String?
)

// Statistiky uživatele (vzdálenost, XP, level)
data class UserStatistics(
    val id: String,
    val name: String,
    val team: String,
    val avatar: String,
    val total_distance: Double,
    val total_time: Int,
    val longest_run: Double,
    val avg_distance: Double,
    val avg_time: Double,
    val longest_time: Int,
    val calories: Int,
    val total_xp: Int,
    val level: Int,
    val current_level_xp: Int,
    val xp_for_next_level: Int,
    val rank: Int = 0
)

data class UserStatisticsListResponse(val items: List<UserStatistics>)

// Statistiky fakult
data class TeamStatistics(
    val id: String,
    val team: String,
    val runners_count: Int,
    val total_distance: Double,
    val run_distance: Double,
    val walk_distance: Double,
    val ride_distance: Double,
    var rank: Int = 0
)

data class TeamStatisticsListResponse(val items: List<TeamStatistics>)

// Odpověď po synchronizaci se Stravou
data class SyncResponse(val message: String, val saved: Int, val total: Int)

// Záznam jedné aktivity
data class ActivityListResponse(val items: List<ActivityRecord>)
data class ActivityRecord(
    val id: String,
    val name: String,
    val distance: Double,
    val duration: Int,
    val start_date: String,
    val activity_type: String
)

// Záznam veřejné aktivity
data class PublicActivityListResponse(val items: List<PublicActivityRecord>)
data class PublicActivityRecord(
    val id: String,
    val user_id: String,
    val user_name: String,
    val user_avatar: String,
    val user_team: String,
    val distance: Double,
    val activity_type: String,
    val duration: Int,
    val start_date: String
)

// Denní souhrny pro grafy
data class UserDailyActivity(
    val day: String,
    val total_distance: Double,
    val total_duration: Int
)

data class UserDailyActivityListResponse(val items: List<UserDailyActivity>)

// Úspěchy a odznaky
data class UserAchievements(
    val id: String,
    val badge_patriot: Int,
    val badge_charity: Int,
    val badge_morning: Int,
    val badge_sprinter: Int,
    val badge_weekend: Int,
    val badge_marathon: Int,
    val badge_halfmarathon: Int,
    val badge_unstoppable: Int
) {
    fun earnedCount(): Int {
        return listOf(
            badge_patriot, badge_charity, badge_morning, badge_sprinter,
            badge_weekend, badge_marathon, badge_halfmarathon, badge_unstoppable
        ).count { it > 0 }
    }
    
    fun totalCount(): Int = 8
}

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
    suspend fun getPublicActivities(pbToken: String): List<PublicActivityRecord> {
        return suspendCancellableCoroutine { continuation ->
            try {
                val url = "$baseUrl/api/collections/activities_public/records?sort=-start_date&perPage=100"
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
