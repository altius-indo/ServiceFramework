package com.enterprise.framework.model

import java.time.Instant

/**
 * Token type enumeration.
 */
enum class TokenType {
    ACCESS,
    REFRESH,
    API_KEY
}

/**
 * Represents a token in the authentication system.
 *
 * @property tokenId Unique identifier for the token
 * @property userId User identifier this token belongs to
 * @property type Type of token (access, refresh, API key)
 * @property value The actual token value (JWT or API key)
 * @property claims Custom claims included in the token
 * @property issuedAt Timestamp when the token was issued
 * @property expiresAt Timestamp when the token expires
 * @property revoked Whether the token has been revoked
 * @property revokedAt Timestamp when the token was revoked
 * @property scopes Scopes/permissions granted by this token
 */
data class Token(
    val tokenId: String,
    val userId: String,
    val type: TokenType,
    val value: String,
    val claims: Map<String, Any> = emptyMap(),
    val issuedAt: Instant = Instant.now(),
    val expiresAt: Instant,
    val revoked: Boolean = false,
    val revokedAt: Instant? = null,
    val scopes: Set<String> = emptySet()
)

/**
 * Response model for token generation.
 *
 * @property accessToken The access token
 * @property refreshToken The refresh token
 * @property tokenType Token type (always "Bearer")
 * @property expiresIn Number of seconds until the access token expires
 */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long
)

/**
 * Request model for token refresh.
 *
 * @property refreshToken The refresh token to use for obtaining a new access token
 */
data class TokenRefreshRequest(
    val refreshToken: String
)

/**
 * Response model for token introspection.
 *
 * @property active Whether the token is active
 * @property userId User identifier if the token is active
 * @property scopes Scopes granted by the token
 * @property expiresAt Expiration timestamp
 * @property issuedAt Issuance timestamp
 */
data class TokenIntrospectionResponse(
    val active: Boolean,
    val userId: String? = null,
    val scopes: Set<String>? = null,
    val expiresAt: Long? = null,
    val issuedAt: Long? = null
)
