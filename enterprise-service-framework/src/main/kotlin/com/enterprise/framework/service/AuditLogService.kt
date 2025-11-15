package com.enterprise.framework.service

import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for logging authentication-related audit events.
 *
 * This service provides structured logging for security-relevant events
 * that can be ingested by SIEM systems for compliance and security monitoring.
 */
class AuditLogService {

    /**
     * Logs a successful login event.
     *
     * @param userId User identifier
     * @param username Username
     * @param ipAddress IP address from which the login originated
     * @param userAgent User agent string
     */
    fun logLoginSuccess(userId: String, username: String, ipAddress: String?, userAgent: String?) {
        logger.info {
            buildAuditLog(
                eventType = "LOGIN_SUCCESS",
                userId = userId,
                username = username,
                ipAddress = ipAddress,
                userAgent = userAgent,
                message = "User logged in successfully"
            )
        }
    }

    /**
     * Logs a failed login event.
     *
     * @param username Username that attempted to log in
     * @param ipAddress IP address from which the login originated
     * @param userAgent User agent string
     * @param reason Reason for failure
     */
    fun logLoginFailure(username: String, ipAddress: String?, userAgent: String?, reason: String) {
        logger.warn {
            buildAuditLog(
                eventType = "LOGIN_FAILURE",
                username = username,
                ipAddress = ipAddress,
                userAgent = userAgent,
                message = "Login failed: $reason",
                additionalData = mapOf("reason" to reason)
            )
        }
    }

    /**
     * Logs a logout event.
     *
     * @param userId User identifier
     * @param username Username
     * @param sessionId Session identifier
     */
    fun logLogout(userId: String, username: String, sessionId: String) {
        logger.info {
            buildAuditLog(
                eventType = "LOGOUT",
                userId = userId,
                username = username,
                message = "User logged out",
                additionalData = mapOf("sessionId" to sessionId)
            )
        }
    }

    /**
     * Logs a token refresh event.
     *
     * @param userId User identifier
     * @param username Username
     */
    fun logTokenRefresh(userId: String, username: String) {
        logger.info {
            buildAuditLog(
                eventType = "TOKEN_REFRESH",
                userId = userId,
                username = username,
                message = "Token refreshed"
            )
        }
    }

    /**
     * Logs a token revocation event.
     *
     * @param userId User identifier
     * @param username Username
     * @param tokenId Token identifier that was revoked
     */
    fun logTokenRevocation(userId: String, username: String, tokenId: String) {
        logger.info {
            buildAuditLog(
                eventType = "TOKEN_REVOCATION",
                userId = userId,
                username = username,
                message = "Token revoked",
                additionalData = mapOf("tokenId" to tokenId)
            )
        }
    }

    /**
     * Logs an account lockout event.
     *
     * @param userId User identifier
     * @param username Username
     * @param reason Reason for lockout
     */
    fun logAccountLockout(userId: String, username: String, reason: String) {
        logger.warn {
            buildAuditLog(
                eventType = "ACCOUNT_LOCKOUT",
                userId = userId,
                username = username,
                message = "Account locked: $reason",
                additionalData = mapOf("reason" to reason)
            )
        }
    }

    /**
     * Logs a password change event.
     *
     * @param userId User identifier
     * @param username Username
     */
    fun logPasswordChange(userId: String, username: String) {
        logger.info {
            buildAuditLog(
                eventType = "PASSWORD_CHANGE",
                userId = userId,
                username = username,
                message = "Password changed"
            )
        }
    }

    /**
     * Logs an MFA enrollment event.
     *
     * @param userId User identifier
     * @param username Username
     */
    fun logMfaEnrollment(userId: String, username: String) {
        logger.info {
            buildAuditLog(
                eventType = "MFA_ENROLLMENT",
                userId = userId,
                username = username,
                message = "MFA enrolled"
            )
        }
    }

    /**
     * Logs an MFA verification event.
     *
     * @param userId User identifier
     * @param username Username
     * @param success Whether verification succeeded
     */
    fun logMfaVerification(userId: String, username: String, success: Boolean) {
        val message = if (success) "MFA verification successful" else "MFA verification failed"

        val auditLog = buildAuditLog(
            eventType = "MFA_VERIFICATION",
            userId = userId,
            username = username,
            message = message,
            additionalData = mapOf("success" to success.toString())
        )

        if (success) {
            logger.info { auditLog }
        } else {
            logger.warn { auditLog }
        }
    }

    /**
     * Logs a rate limit exceeded event.
     *
     * @param username Username (if available)
     * @param ipAddress IP address
     * @param endpoint Endpoint that was rate limited
     */
    fun logRateLimitExceeded(username: String?, ipAddress: String?, endpoint: String) {
        logger.warn {
            buildAuditLog(
                eventType = "RATE_LIMIT_EXCEEDED",
                username = username,
                ipAddress = ipAddress,
                message = "Rate limit exceeded for endpoint: $endpoint",
                additionalData = mapOf("endpoint" to endpoint)
            )
        }
    }

    /**
     * Builds a structured audit log message.
     *
     * @param eventType Type of event
     * @param userId User identifier (optional)
     * @param username Username (optional)
     * @param ipAddress IP address (optional)
     * @param userAgent User agent (optional)
     * @param message Human-readable message
     * @param additionalData Additional structured data
     * @return Structured audit log string
     */
    private fun buildAuditLog(
        eventType: String,
        userId: String? = null,
        username: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        message: String,
        additionalData: Map<String, String> = emptyMap()
    ): String {
        val fields = mutableMapOf(
            "timestamp" to Instant.now().toString(),
            "event_type" to eventType,
            "message" to message
        )

        userId?.let { fields["user_id"] = it }
        username?.let { fields["username"] = it }
        ipAddress?.let { fields["ip_address"] = it }
        userAgent?.let { fields["user_agent"] = it }
        fields.putAll(additionalData)

        return fields.entries.joinToString(" | ") { (key, value) ->
            "$key=$value"
        }
    }
}
