package com.enterprise.framework.service

import com.enterprise.framework.model.JwtConfig
import com.enterprise.framework.model.Token
import com.enterprise.framework.model.TokenResponse
import com.enterprise.framework.model.TokenType
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.jwt.JWTAuth
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Service for managing JWT tokens including generation, validation, and refresh.
 *
 * This service handles both access tokens and refresh tokens, providing
 * functionality for secure token lifecycle management.
 *
 * @property jwtAuth JWT authentication provider
 * @property config JWT configuration
 * @property revocationService Token revocation service
 */
class TokenService(
    private val jwtAuth: JWTAuth,
    private val config: JwtConfig,
    private val revocationService: TokenRevocationService
) {

    /**
     * Generates a new access and refresh token pair.
     *
     * @param userId User identifier
     * @param claims Custom claims to include in tokens
     * @return Future with TokenResponse containing both tokens
     */
    fun generateTokenPair(userId: String, claims: Map<String, Any> = emptyMap()): Future<TokenResponse> {
        val promise = Promise.promise<TokenResponse>()

        try {
            val accessToken = generateAccessToken(userId, claims)
            val refreshToken = generateRefreshToken(userId)

            val response = TokenResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenType = "Bearer",
                expiresIn = config.accessTokenExpirationSeconds
            )

            logger.debug { "Token pair generated for user: $userId" }
            promise.complete(response)
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate token pair for user: $userId" }
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Generates a new access token.
     *
     * @param userId User identifier
     * @param claims Custom claims to include
     * @return Access token string
     */
    fun generateAccessToken(userId: String, claims: Map<String, Any> = emptyMap()): String {
        val tokenId = UUID.randomUUID().toString()
        val jwtClaims = JsonObject()
            .put("sub", userId)
            .put("jti", tokenId)
            .put("type", "access")

        claims.forEach { (key, value) ->
            jwtClaims.put(key, value)
        }

        return jwtAuth.generateToken(
            jwtClaims,
            JWTOptions()
                .setExpiresInSeconds(config.accessTokenExpirationSeconds.toInt())
                .setIssuer(config.issuer)
        )
    }

    /**
     * Generates a new refresh token.
     *
     * @param userId User identifier
     * @return Refresh token string
     */
    fun generateRefreshToken(userId: String): String {
        val tokenId = UUID.randomUUID().toString()
        val jwtClaims = JsonObject()
            .put("sub", userId)
            .put("jti", tokenId)
            .put("type", "refresh")

        return jwtAuth.generateToken(
            jwtClaims,
            JWTOptions()
                .setExpiresInSeconds(config.refreshTokenExpirationSeconds.toInt())
                .setIssuer(config.issuer)
        )
    }

    /**
     * Validates an access token.
     *
     * @param token Token to validate
     * @return Future with the token payload if valid
     */
    fun validateAccessToken(token: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        jwtAuth.authenticate(JsonObject().put("token", token))
            .compose { user ->
                val principal = user.principal()
                val tokenId = principal.getString("jti")
                val tokenType = principal.getString("type")

                if (tokenType != "access") {
                    return@compose Future.failedFuture<JsonObject>(
                        TokenValidationException("Invalid token type: expected 'access', got '$tokenType'")
                    )
                }

                // Check if token is revoked
                revocationService.isRevoked(tokenId)
                    .compose { isRevoked ->
                        if (isRevoked) {
                            Future.failedFuture(TokenValidationException("Token has been revoked"))
                        } else {
                            Future.succeededFuture(principal)
                        }
                    }
            }
            .onSuccess { payload ->
                logger.debug { "Access token validated successfully" }
                promise.complete(payload)
            }
            .onFailure { error ->
                logger.warn(error) { "Access token validation failed" }
                promise.fail(error)
            }

        return promise.future()
    }

    /**
     * Validates a refresh token.
     *
     * @param token Refresh token to validate
     * @return Future with the user ID if valid
     */
    fun validateRefreshToken(token: String): Future<String> {
        val promise = Promise.promise<String>()

        jwtAuth.authenticate(JsonObject().put("token", token))
            .compose { user ->
                val principal = user.principal()
                val tokenId = principal.getString("jti")
                val tokenType = principal.getString("type")
                val userId = principal.getString("sub")

                if (tokenType != "refresh") {
                    return@compose Future.failedFuture<String>(
                        TokenValidationException("Invalid token type: expected 'refresh', got '$tokenType'")
                    )
                }

                // Check if token is revoked
                revocationService.isRevoked(tokenId)
                    .compose { isRevoked ->
                        if (isRevoked) {
                            Future.failedFuture(TokenValidationException("Token has been revoked"))
                        } else {
                            Future.succeededFuture(userId)
                        }
                    }
            }
            .onSuccess { userId ->
                logger.debug { "Refresh token validated successfully for user: $userId" }
                promise.complete(userId)
            }
            .onFailure { error ->
                logger.warn(error) { "Refresh token validation failed" }
                promise.fail(error)
            }

        return promise.future()
    }

    /**
     * Refreshes an access token using a refresh token.
     *
     * @param refreshToken Refresh token
     * @param claims Additional claims to include in the new access token
     * @return Future with new TokenResponse
     */
    fun refreshAccessToken(refreshToken: String, claims: Map<String, Any> = emptyMap()): Future<TokenResponse> {
        return validateRefreshToken(refreshToken)
            .compose { userId ->
                generateTokenPair(userId, claims)
            }
    }

    /**
     * Revokes a token by its ID.
     *
     * @param tokenId Token ID to revoke
     * @param expiresAt When the token would naturally expire
     * @return Future indicating success
     */
    fun revokeToken(tokenId: String, expiresAt: Instant): Future<Void> {
        return revocationService.revokeToken(tokenId, expiresAt)
            .onSuccess {
                logger.info { "Token revoked: $tokenId" }
            }
            .onFailure { error ->
                logger.error(error) { "Failed to revoke token: $tokenId" }
            }
    }

    /**
     * Extracts the token ID from a JWT token.
     *
     * @param token JWT token
     * @return Future with the token ID
     */
    fun extractTokenId(token: String): Future<String> {
        val promise = Promise.promise<String>()

        jwtAuth.authenticate(JsonObject().put("token", token))
            .onSuccess { user ->
                val tokenId = user.principal().getString("jti")
                promise.complete(tokenId)
            }
            .onFailure { error ->
                logger.warn(error) { "Failed to extract token ID" }
                promise.fail(error)
            }

        return promise.future()
    }

    /**
     * Introspects a token to check its validity and extract metadata.
     *
     * @param token Token to introspect
     * @return Future with token metadata
     */
    fun introspectToken(token: String): Future<JsonObject> {
        return validateAccessToken(token)
            .map { payload ->
                JsonObject()
                    .put("active", true)
                    .put("sub", payload.getString("sub"))
                    .put("jti", payload.getString("jti"))
                    .put("type", payload.getString("type"))
                    .put("iss", payload.getString("iss"))
                    .put("exp", payload.getLong("exp"))
                    .put("iat", payload.getLong("iat"))
            }
            .recover { error ->
                Future.succeededFuture(
                    JsonObject()
                        .put("active", false)
                        .put("error", error.message)
                )
            }
    }
}

/**
 * Exception thrown when token validation fails.
 */
class TokenValidationException(message: String) : Exception(message)
