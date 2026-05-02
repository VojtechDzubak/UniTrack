package cz.st72504.unitrack2.model

/**
 * Modely pro autentizaci a uživatelské účty.
 */

data class AuthMethodsResponse(val authProviders: List<AuthProvider>)

data class AuthProvider(
    val name: String,
    val authUrl: String,
    val codeVerifier: String,
    val state: String
)

data class AuthResponse(
    val token: String,
    val record: UserRecord
)
