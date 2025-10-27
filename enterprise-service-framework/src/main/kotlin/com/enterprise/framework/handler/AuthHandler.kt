package com.enterprise.framework.handler

import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.RoutingContext
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Authentication handler for JWT-based authentication
 */
class AuthHandler(private val jwtAuth: JWTAuth) {

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
