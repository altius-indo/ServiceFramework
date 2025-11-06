package com.enterprise.framework.model

/**
 * Authentication configuration model.
 *
 * @property jwt JWT configuration
 * @property session Session configuration
 * @property password Password policy configuration
 * @property rateLimiting Rate limiting configuration
 * @property bruteForce Brute force protection configuration
 * @property mfa Multi-factor authentication configuration
 */
data class AuthConfig(
    val jwt: JwtConfig,
    val session: SessionConfig,
    val password: PasswordPolicy,
    val rateLimiting: RateLimitingConfig,
    val bruteForce: BruteForceConfig,
    val mfa: MfaConfig
)

/**
 * JWT configuration model.
 *
 * @property secret Secret key for HS256 signing
 * @property issuer Token issuer
 * @property accessTokenExpirationSeconds Access token expiration in seconds
 * @property refreshTokenExpirationSeconds Refresh token expiration in seconds
 * @property algorithm Signing algorithm (HS256, RS256, ES256)
 * @property publicKey Public key for RS256/ES256 verification (PEM format)
 * @property privateKey Private key for RS256/ES256 signing (PEM format)
 */
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val accessTokenExpirationSeconds: Long = 3600,
    val refreshTokenExpirationSeconds: Long = 604800,
    val algorithm: String = "HS256",
    val publicKey: String? = null,
    val privateKey: String? = null
)

/**
 * Session configuration model.
 *
 * @property absoluteTimeoutSeconds Maximum session duration in seconds
 * @property idleTimeoutSeconds Session idle timeout in seconds
 * @property maxConcurrentSessions Maximum concurrent sessions per user
 * @property renewOnActivity Whether to renew session on activity
 */
data class SessionConfig(
    val absoluteTimeoutSeconds: Long = 86400,
    val idleTimeoutSeconds: Long = 3600,
    val maxConcurrentSessions: Int = 5,
    val renewOnActivity: Boolean = true
)

/**
 * Rate limiting configuration model.
 *
 * @property enabled Whether rate limiting is enabled
 * @property maxRequests Maximum requests per window
 * @property windowSeconds Time window in seconds
 * @property blacklistDurationSeconds Duration to blacklist offenders in seconds
 */
data class RateLimitingConfig(
    val enabled: Boolean = true,
    val maxRequests: Int = 10,
    val windowSeconds: Long = 60,
    val blacklistDurationSeconds: Long = 300
)

/**
 * Brute force protection configuration model.
 *
 * @property enabled Whether brute force protection is enabled
 * @property maxFailedAttempts Maximum failed login attempts before lockout
 * @property lockoutDurationSeconds Account lockout duration in seconds
 * @property resetAfterSuccessfulLogin Whether to reset failed attempts on successful login
 */
data class BruteForceConfig(
    val enabled: Boolean = true,
    val maxFailedAttempts: Int = 5,
    val lockoutDurationSeconds: Long = 900,
    val resetAfterSuccessfulLogin: Boolean = true
)

/**
 * Multi-factor authentication configuration model.
 *
 * @property enabled Whether MFA is enabled
 * @property issuer TOTP issuer name
 * @property totpWindowSize TOTP time window size
 * @property enforced Whether MFA is enforced for all users
 */
data class MfaConfig(
    val enabled: Boolean = true,
    val issuer: String = "Enterprise Framework",
    val totpWindowSize: Int = 1,
    val enforced: Boolean = false
)
