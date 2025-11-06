package com.enterprise.framework.model

import java.time.Instant

/**
 * Represents a user in the authentication system.
 *
 * @property userId Unique identifier for the user
 * @property username Username for authentication
 * @property email User's email address
 * @property roles Set of roles assigned to the user
 * @property enabled Whether the user account is enabled
 * @property locked Whether the user account is locked
 * @property mfaEnabled Whether MFA is enabled for this user
 * @property mfaSecret Secret for TOTP-based MFA (encrypted)
 * @property createdAt Timestamp when the user was created
 * @property updatedAt Timestamp when the user was last updated
 * @property lastLoginAt Timestamp of the last successful login
 * @property failedLoginAttempts Number of consecutive failed login attempts
 * @property lockedUntil Timestamp until which the account is locked
 * @property passwordChangedAt Timestamp when the password was last changed
 * @property metadata Additional user metadata
 */
data class User(
    val userId: String,
    val username: String,
    val email: String,
    val roles: Set<String> = emptySet(),
    val enabled: Boolean = true,
    val locked: Boolean = false,
    val mfaEnabled: Boolean = false,
    val mfaSecret: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val lastLoginAt: Instant? = null,
    val failedLoginAttempts: Int = 0,
    val lockedUntil: Instant? = null,
    val passwordChangedAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
)
