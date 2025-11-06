package com.enterprise.framework.model

import java.time.Instant

/**
 * Represents user credentials in the authentication system.
 *
 * @property userId User identifier this credential belongs to
 * @property passwordHash Hashed password using Argon2 or bcrypt
 * @property algorithm Hashing algorithm used (argon2id, bcrypt)
 * @property salt Salt used for hashing
 * @property passwordHistory List of previous password hashes to prevent reuse
 * @property createdAt Timestamp when the credential was created
 * @property expiresAt Optional expiration timestamp for the password
 */
data class Credential(
    val userId: String,
    val passwordHash: String,
    val algorithm: String,
    val salt: String,
    val passwordHistory: List<String> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null
)

/**
 * Represents password policy configuration.
 *
 * @property minLength Minimum password length
 * @property requireUppercase Require at least one uppercase letter
 * @property requireLowercase Require at least one lowercase letter
 * @property requireDigit Require at least one digit
 * @property requireSpecialChar Require at least one special character
 * @property preventReuse Number of previous passwords to check for reuse
 * @property expirationDays Number of days until password expires (null = no expiration)
 */
data class PasswordPolicy(
    val minLength: Int = 12,
    val requireUppercase: Boolean = true,
    val requireLowercase: Boolean = true,
    val requireDigit: Boolean = true,
    val requireSpecialChar: Boolean = true,
    val preventReuse: Int = 5,
    val expirationDays: Int? = null
)
