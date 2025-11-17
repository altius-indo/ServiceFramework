package com.enterprise.framework.cli.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.kotlin.coroutines.coAwait
import mu.KotlinLogging
import java.io.File
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * CLI Authenticator for bootstrap and session management
 */
class CLIAuthenticator(
    private val vertx: Vertx,
    private val serverUrl: String,
    private val configDir: File
) {
    private val webClient: WebClient
    private val tokenFile: File
    private val objectMapper: ObjectMapper

    init {
        webClient = WebClient.create(
            vertx,
            WebClientOptions()
                .setDefaultHost(serverUrl.removePrefix("http://").removePrefix("https://").split(":")[0])
                .setDefaultPort(
                    serverUrl.removePrefix("http://").removePrefix("https://")
                        .split(":").getOrNull(1)?.toIntOrNull() ?: 8080
                )
        )
        tokenFile = File(configDir, "token.json")
        objectMapper = jacksonObjectMapper()
    }

    /**
     * Authenticate with username and password
     * Returns access token for subsequent requests
     */
    suspend fun authenticate(username: String, password: String): AuthResult {
        try {
            val response = webClient.post("/auth/login")
                .putHeader("Content-Type", "application/json")
                .sendJson(JsonObject()
                    .put("username", username)
                    .put("password", password)
                )
                .coAwait()

            if (response.statusCode() == 200) {
                val body = response.bodyAsJsonObject()
                val accessToken = body.getString("accessToken")
                val refreshToken = body.getString("refreshToken")
                val expiresAt = Instant.now().plusSeconds(body.getLong("expiresIn", 3600))

                // Save token
                val tokenData = TokenData(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt,
                    username = username
                )
                saveToken(tokenData)

                logger.info { "Authentication successful for user: $username" }
                return AuthResult(success = true, token = accessToken, message = "Authentication successful")
            } else {
                val error = response.bodyAsString()
                logger.error { "Authentication failed: $error" }
                return AuthResult(success = false, message = "Authentication failed: $error")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error during authentication" }
            return AuthResult(success = false, message = "Error: ${e.message}")
        }
    }

    /**
     * Get current token or authenticate if needed
     */
    suspend fun getToken(): String? {
        val tokenData = loadToken()
        
        if (tokenData != null && tokenData.expiresAt.isAfter(Instant.now())) {
            return tokenData.accessToken
        }

        // Try to refresh if we have a refresh token
        if (tokenData?.refreshToken != null) {
            val refreshed = refreshToken(tokenData.refreshToken)
            if (refreshed != null) {
                return refreshed
            }
        }

        return null
    }

    /**
     * Refresh access token
     */
    private suspend fun refreshToken(refreshToken: String): String? {
        try {
            val response = webClient.post("/auth/refresh")
                .putHeader("Content-Type", "application/json")
                .sendJson(JsonObject().put("refreshToken", refreshToken))
                .coAwait()

            if (response.statusCode() == 200) {
                val body = response.bodyAsJsonObject()
                val accessToken = body.getString("accessToken")
                val newRefreshToken = body.getString("refreshToken")
                val expiresAt = Instant.now().plusSeconds(body.getLong("expiresIn", 3600))

                val tokenData = loadToken()
                if (tokenData != null) {
                    val updatedToken = tokenData.copy(
                        accessToken = accessToken,
                        refreshToken = newRefreshToken,
                        expiresAt = expiresAt
                    )
                    saveToken(updatedToken)
                }

                return accessToken
            }
        } catch (e: Exception) {
            logger.error(e) { "Error refreshing token" }
        }
        return null
    }

    /**
     * Logout and clear token
     */
    suspend fun logout() {
        val tokenData = loadToken()
        if (tokenData != null) {
            try {
                webClient.post("/auth/logout")
                    .putHeader("Authorization", "Bearer ${tokenData.accessToken}")
                    .send()
                    .coAwait()
            } catch (e: Exception) {
                logger.warn(e) { "Error during logout" }
            }
        }
        tokenFile.delete()
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        val tokenData = loadToken()
        return tokenData != null && tokenData.expiresAt.isAfter(Instant.now())
    }

    private fun saveToken(tokenData: TokenData) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(tokenFile, tokenData)
        } catch (e: Exception) {
            logger.error(e) { "Error saving token" }
        }
    }

    private fun loadToken(): TokenData? {
        return if (tokenFile.exists()) {
            try {
                objectMapper.readValue(tokenFile, TokenData::class.java)
            } catch (e: Exception) {
                logger.error(e) { "Error loading token" }
                null
            }
        } else {
            null
        }
    }
}

data class TokenData(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val username: String
)

data class AuthResult(
    val success: Boolean,
    val token: String? = null,
    val message: String
)

