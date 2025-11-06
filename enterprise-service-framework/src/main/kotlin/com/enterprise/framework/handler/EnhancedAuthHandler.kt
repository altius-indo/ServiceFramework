package com.enterprise.framework.handler

import com.enterprise.framework.model.TokenRefreshRequest
import com.enterprise.framework.model.User
import com.enterprise.framework.repository.UserRepository
import com.enterprise.framework.service.*
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Enhanced authentication handler with comprehensive security features.
 *
 * This handler provides login, logout, token refresh, session management,
 * and integrated security controls including rate limiting, brute force
 * protection, and audit logging.
 *
 * @property userRepository User repository
 * @property credentialService Credential service for password operations
 * @property tokenService Token service for JWT operations
 * @property sessionService Session service for session management
 * @property rateLimitService Rate limiting service
 * @property bruteForceProtectionService Brute force protection service
 * @property auditLogService Audit logging service
 */
class EnhancedAuthHandler(
    private val userRepository: UserRepository,
    private val credentialService: CredentialService,
    private val tokenService: TokenService,
    private val sessionService: SessionService,
    private val rateLimitService: RateLimitService,
    private val bruteForceProtectionService: BruteForceProtectionService,
    private val auditLogService: AuditLogService
) {

    /**
     * Handles user login with comprehensive security checks.
     *
     * @param ctx Routing context
     */
    fun handleLogin(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()
        val username = body?.getString("username")
        val password = body?.getString("password")
        val ipAddress = ctx.request().remoteAddress()?.host()
        val userAgent = ctx.request().getHeader("User-Agent")

        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            respondError(ctx, 400, "Username and password are required")
            return
        }

        // Check rate limit
        rateLimitService.checkRateLimit(ipAddress ?: "unknown", "/auth/login")
            .compose { allowed ->
                if (!allowed) {
                    auditLogService.logRateLimitExceeded(username, ipAddress, "/auth/login")
                    return@compose io.vertx.core.Future.failedFuture<Unit>(
                        Exception("Rate limit exceeded")
                    )
                }

                // Find user by username
                userRepository.findByUsername(username)
            }
            .compose { userAndCredential ->
                if (userAndCredential == null) {
                    auditLogService.logLoginFailure(username, ipAddress, userAgent, "User not found")
                    return@compose io.vertx.core.Future.failedFuture<User>(
                        Exception("Invalid credentials")
                    )
                }

                val (user, credential) = userAndCredential

                // Check if user is enabled
                if (!user.enabled) {
                    auditLogService.logLoginFailure(username, ipAddress, userAgent, "Account disabled")
                    return@compose io.vertx.core.Future.failedFuture<User>(
                        Exception("Account disabled")
                    )
                }

                // Check if account is locked
                if (bruteForceProtectionService.isAccountLocked(user)) {
                    val remaining = bruteForceProtectionService.getRemainingLockoutDuration(user)
                    auditLogService.logLoginFailure(
                        username,
                        ipAddress,
                        userAgent,
                        "Account locked for $remaining seconds"
                    )
                    return@compose io.vertx.core.Future.failedFuture<User>(
                        Exception("Account locked. Try again in $remaining seconds")
                    )
                }

                // Verify password
                credentialService.verifyPassword(password, credential)
                    .compose { isValid ->
                        if (!isValid) {
                            // Record failed attempt
                            auditLogService.logLoginFailure(username, ipAddress, userAgent, "Invalid password")

                            bruteForceProtectionService.recordFailedAttempt(user)
                                .compose {
                                    io.vertx.core.Future.failedFuture<User>(Exception("Invalid credentials"))
                                }
                        } else {
                            // Reset failed attempts on successful login
                            bruteForceProtectionService.resetFailedAttempts(user)
                                .map { updatedUser ->
                                    updatedUser.copy(lastLoginAt = Instant.now())
                                }
                                .compose { updatedUser ->
                                    userRepository.updateUser(updatedUser)
                                }
                        }
                    }
            }
            .compose { user ->
                // Generate tokens
                val claims = mapOf(
                    "username" to user.username,
                    "roles" to user.roles.joinToString(",")
                )

                tokenService.generateTokenPair(user.userId, claims)
                    .compose { tokenResponse ->
                        // Create session
                        sessionService.createSession(
                            userId = user.userId,
                            accessToken = tokenResponse.accessToken,
                            refreshToken = tokenResponse.refreshToken,
                            ipAddress = ipAddress,
                            userAgent = userAgent
                        ).map { session ->
                            Pair(user, tokenResponse)
                        }
                    }
            }
            .onSuccess { (user, tokenResponse) ->
                // Log successful login
                auditLogService.logLoginSuccess(user.userId, user.username, ipAddress, userAgent)

                val response = JsonObject()
                    .put("accessToken", tokenResponse.accessToken)
                    .put("refreshToken", tokenResponse.refreshToken)
                    .put("tokenType", "Bearer")
                    .put("expiresIn", tokenResponse.expiresIn)
                    .put("user", JsonObject()
                        .put("userId", user.userId)
                        .put("username", user.username)
                        .put("email", user.email)
                        .put("roles", user.roles.joinToString(","))
                    )

                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(response.encode())
            }
            .onFailure { error ->
                logger.error(error) { "Login failed for user: $username" }

                // Record rate limit violation
                rateLimitService.recordViolation(ipAddress ?: "unknown", "/auth/login")

                respondError(ctx, 401, error.message ?: "Authentication failed")
            }
    }

    /**
     * Handles token refresh.
     *
     * @param ctx Routing context
     */
    fun handleTokenRefresh(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()
        val refreshToken = body?.getString("refreshToken")

        if (refreshToken.isNullOrBlank()) {
            respondError(ctx, 400, "Refresh token is required")
            return
        }

        tokenService.validateRefreshToken(refreshToken)
            .compose { userId ->
                // Find user
                userRepository.findByUserId(userId)
                    .compose { user ->
                        if (user == null) {
                            return@compose io.vertx.core.Future.failedFuture<JsonObject>(
                                Exception("User not found")
                            )
                        }

                        // Generate new token pair
                        val claims = mapOf(
                            "username" to user.username,
                            "roles" to user.roles.joinToString(",")
                        )

                        tokenService.refreshAccessToken(refreshToken, claims)
                            .map { tokenResponse ->
                                auditLogService.logTokenRefresh(user.userId, user.username)

                                JsonObject()
                                    .put("accessToken", tokenResponse.accessToken)
                                    .put("refreshToken", tokenResponse.refreshToken)
                                    .put("tokenType", "Bearer")
                                    .put("expiresIn", tokenResponse.expiresIn)
                            }
                    }
            }
            .onSuccess { response ->
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(response.encode())
            }
            .onFailure { error ->
                logger.error(error) { "Token refresh failed" }
                respondError(ctx, 401, "Invalid or expired refresh token")
            }
    }

    /**
     * Handles user logout.
     *
     * @param ctx Routing context
     */
    fun handleLogout(ctx: RoutingContext) {
        val user = ctx.get<io.vertx.ext.auth.User>("user")
        val principal = user?.principal()
        val userId = principal?.getString("sub")
        val username = principal?.getString("username")
        val tokenId = principal?.getString("jti")

        if (userId == null || tokenId == null) {
            respondError(ctx, 401, "Unauthorized")
            return
        }

        // Terminate all sessions for the user
        sessionService.terminateAllUserSessions(userId)
            .compose {
                // Revoke the token
                val expiresAt = Instant.ofEpochSecond(principal.getLong("exp"))
                tokenService.revokeToken(tokenId, expiresAt)
            }
            .onSuccess {
                auditLogService.logLogout(userId, username ?: "unknown", "all")

                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject().put("message", "Logged out successfully").encode())
            }
            .onFailure { error ->
                logger.error(error) { "Logout failed for user: $userId" }
                respondError(ctx, 500, "Logout failed")
            }
    }

    /**
     * Handles listing active sessions for the authenticated user.
     *
     * @param ctx Routing context
     */
    fun handleListSessions(ctx: RoutingContext) {
        val user = ctx.get<io.vertx.ext.auth.User>("user")
        val userId = user?.principal()?.getString("sub")

        if (userId == null) {
            respondError(ctx, 401, "Unauthorized")
            return
        }

        sessionService.getUserSessions(userId)
            .onSuccess { sessions ->
                val sessionsJson = sessions.map { session ->
                    JsonObject()
                        .put("sessionId", session.sessionId)
                        .put("createdAt", session.createdAt.toString())
                        .put("lastAccessedAt", session.lastAccessedAt.toString())
                        .put("expiresAt", session.expiresAt.toString())
                        .put("ipAddress", session.ipAddress)
                        .put("userAgent", session.userAgent)
                }

                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject().put("sessions", sessionsJson).encode())
            }
            .onFailure { error ->
                logger.error(error) { "Failed to list sessions for user: $userId" }
                respondError(ctx, 500, "Failed to retrieve sessions")
            }
    }

    /**
     * Handles terminating a specific session.
     *
     * @param ctx Routing context
     */
    fun handleTerminateSession(ctx: RoutingContext) {
        val user = ctx.get<io.vertx.ext.auth.User>("user")
        val userId = user?.principal()?.getString("sub")
        val sessionId = ctx.pathParam("sessionId")

        if (userId == null) {
            respondError(ctx, 401, "Unauthorized")
            return
        }

        if (sessionId.isNullOrBlank()) {
            respondError(ctx, 400, "Session ID is required")
            return
        }

        // Verify session belongs to user
        sessionService.getSession(sessionId)
            .compose { session ->
                if (session == null) {
                    return@compose io.vertx.core.Future.failedFuture<Void>(
                        Exception("Session not found")
                    )
                }

                if (session.userId != userId) {
                    return@compose io.vertx.core.Future.failedFuture<Void>(
                        Exception("Unauthorized")
                    )
                }

                sessionService.terminateSession(sessionId)
            }
            .onSuccess {
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject().put("message", "Session terminated successfully").encode())
            }
            .onFailure { error ->
                logger.error(error) { "Failed to terminate session: $sessionId" }
                respondError(ctx, 500, error.message ?: "Failed to terminate session")
            }
    }

    /**
     * Handles token introspection.
     *
     * @param ctx Routing context
     */
    fun handleTokenIntrospection(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()
        val token = body?.getString("token")

        if (token.isNullOrBlank()) {
            respondError(ctx, 400, "Token is required")
            return
        }

        tokenService.introspectToken(token)
            .onSuccess { introspectionResult ->
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json")
                    .end(introspectionResult.encode())
            }
            .onFailure { error ->
                logger.error(error) { "Token introspection failed" }
                respondError(ctx, 500, "Introspection failed")
            }
    }

    /**
     * Middleware for authenticating requests using JWT.
     *
     * @param ctx Routing context
     */
    fun authenticate(ctx: RoutingContext) {
        val authHeader = ctx.request().getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            respondError(ctx, 401, "Missing or invalid authorization header")
            return
        }

        val token = authHeader.substring(7)

        tokenService.validateAccessToken(token)
            .onSuccess { payload ->
                // Create a simple user object for downstream handlers
                val user = object : io.vertx.ext.auth.User {
                    override fun principal() = payload
                    override fun attributes() = JsonObject()
                    override fun isAuthorized(authority: String) =
                        io.vertx.core.Future.succeededFuture(false)
                    override fun merge(other: io.vertx.ext.auth.User) = this
                }

                ctx.put("user", user)
                ctx.next()
            }
            .onFailure { error ->
                logger.warn(error) { "Authentication failed" }
                respondError(ctx, 401, "Invalid or expired token")
            }
    }

    /**
     * Helper method to send error responses.
     */
    private fun respondError(ctx: RoutingContext, statusCode: Int, message: String) {
        ctx.response()
            .setStatusCode(statusCode)
            .putHeader("content-type", "application/json")
            .end(JsonObject()
                .put("error", getErrorName(statusCode))
                .put("message", message)
                .encode()
            )
    }

    /**
     * Maps status codes to error names.
     */
    private fun getErrorName(statusCode: Int): String {
        return when (statusCode) {
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            else -> "Error"
        }
    }
}
