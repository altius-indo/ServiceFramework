package com.enterprise.framework.handler

import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A handler for managing JWT-based authentication and token generation.
 *
 * This class provides methods for authenticating requests using JWT tokens,
 * generating new tokens, and handling login requests. It is designed to be

 * used as a set of handlers in a Vert.x web application.
 *
 * @property jwtAuth An instance of [JWTAuth] used for token operations.
 */
class AuthHandler(private val jwtAuth: JWTAuth) {

    /**
     * Authenticates an incoming request using a JWT token from the Authorization header.
     *
     * This method checks for a "Bearer" token in the "Authorization" header. If the
     * token is valid, it adds the authenticated user to the routing context and
     * proceeds to the next handler. If the token is missing or invalid, it sends
     * a 401 Unauthorized response.
     *
     * @param ctx The [RoutingContext] of the request.
     */
    fun authenticate(ctx: RoutingContext) {
        val authHeader = ctx.request().getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn { "Missing or invalid authorization header" }
            ctx.response()
                .setStatusCode(401)
                .putHeader("content-type", "application/json")
                .end("""{"error": "Unauthorized", "message": "Missing or invalid token"}""")
            return
        }

        val token = authHeader.substring(7)

        jwtAuth.authenticate(JsonObject().put("token", token))
            .onSuccess { user ->
                logger.debug { "User authenticated successfully" }
                ctx.put("user", user)
                ctx.next()
            }
            .onFailure { error ->
                logger.warn(error) { "Authentication failed" }
                ctx.response()
                    .setStatusCode(401)
                    .putHeader("content-type", "application/json")
                    .end("""{"error": "Unauthorized", "message": "${error.message}"}""")
            }
    }

    /**
     * Generates a new JWT token for a given user ID and a set of claims.
     *
     * @param userId The subject of the JWT, typically the user's unique identifier.
     * @param claims A map of custom claims to include in the JWT payload.
     * @return A string representation of the signed JWT.
     */
    fun generateToken(userId: String, claims: Map<String, Any> = emptyMap()): String {
        val jwtClaims = JsonObject()
            .put("sub", userId)

        claims.forEach { (key, value) ->
            jwtClaims.put(key, value)
        }

        return jwtAuth.generateToken(
            jwtClaims,
            JWTOptions()
                .setExpiresInSeconds(3600)
                .setIssuer("enterprise-framework")
        )
    }

    /**
     * Handles a login request from a user.
     *
     * This method expects a JSON body containing a username and password.
     * If the credentials are provided, it generates a new JWT token and sends
     * it in the response. Note that this is a simplified implementation
     * for demonstration purposes and does not perform actual password validation.
     *
     * @param ctx The [RoutingContext] of the request.
     */
    fun handleLogin(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()
        val username = body?.getString("username")
        val password = body?.getString("password")

        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("content-type", "application/json")
                .end("""{"error": "Bad Request", "message": "Username and password are required"}""")
            return
        }

        logger.info { "Login attempt for user: $username" }

        val token = generateToken(username, mapOf("role" to "user"))

        val response = JsonObject()
            .put("token", token)
            .put("type", "Bearer")
            .put("expiresIn", 3600)

        ctx.response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(response.encode())
    }
}
