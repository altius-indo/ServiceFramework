package com.enterprise.framework.model

import java.time.Instant

/**
 * Represents an active user session.
 *
 * @property sessionId Unique identifier for the session
 * @property userId User identifier this session belongs to
 * @property accessToken Current access token for this session
 * @property refreshToken Refresh token for obtaining new access tokens
 * @property ipAddress IP address from which the session was created
 * @property userAgent User agent string from the client
 * @property createdAt Timestamp when the session was created
 * @property lastAccessedAt Timestamp when the session was last accessed
 * @property expiresAt Timestamp when the session expires
 * @property terminated Whether the session has been explicitly terminated
 * @property metadata Additional session metadata
 */
data class Session(
    val sessionId: String,
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdAt: Instant = Instant.now(),
    val lastAccessedAt: Instant = Instant.now(),
    val expiresAt: Instant,
    val terminated: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)
