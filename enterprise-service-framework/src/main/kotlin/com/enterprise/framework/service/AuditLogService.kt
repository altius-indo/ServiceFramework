package com.enterprise.framework.service

import com.enterprise.framework.model.AuditLog
import com.enterprise.framework.repository.AuditLogRepository
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for logging authentication-related audit events.
 *
 * This service provides structured logging for security-relevant events
 * that can be ingested by SIEM systems for compliance and security monitoring.
 * It also persists the logs to a repository.
 */
class AuditLogService(
    private val auditLogRepository: AuditLogRepository? = null
) {

    /**
     * Logs a successful login event.
     *
     * @param userId User identifier
     * @param username Username
     * @param ipAddress IP address from which the login originated
     * @param userAgent User agent string
     */
    fun logLoginSuccess(userId: String, username: String, ipAddress: String?, userAgent: String?) {
        val eventType = "LOGIN_SUCCESS"
        val message = "User logged in successfully"

        logger.info {
            buildAuditLogString(
                eventType = eventType,
                userId = userId,
                username = username,
                ipAddress = ipAddress,
                userAgent = userAgent,
                message = message
            )
        }

        persistAuditLog(
            eventType = eventType,
            userId = userId,
            username = username,
            ipAddress = ipAddress,
            userAgent = userAgent,
            message = message
        )
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
        val eventType = "LOGIN_FAILURE"
        val message = "Login failed: $reason"
        val additionalData = mapOf("reason" to reason)

        logger.warn {
            buildAuditLogString(
                eventType = eventType,
                username = username,
                ipAddress = ipAddress,
                userAgent = userAgent,
                message = message,
                additionalData = additionalData
            )
        }

        persistAuditLog(
            eventType = eventType,
            username = username,
            ipAddress = ipAddress,
            userAgent = userAgent,
            message = message,
            additionalData = additionalData
        )
    }

    /**
     * Logs a logout event.
     *
     * @param userId User identifier
     * @param username Username
     * @param sessionId Session identifier
     */
    fun logLogout(userId: String, username: String, sessionId: String) {
        val eventType = "LOGOUT"
        val message = "User logged out"
        val additionalData = mapOf("sessionId" to sessionId)

        logger.info {
            buildAuditLogString(
                eventType = eventType,
                userId = userId,
                username = username,
                message = message,
                additionalData = additionalData
            )
        }

        persistAuditLog(
            eventType = eventType,
            userId = userId,
            username = username,
            message = message,
            additionalData = additionalData
        )
    }

    /**
     * Logs a token refresh event.
     *
     * @param userId User identifier
     * @param username Username
     */
    fun logTokenRefresh(userId: String, username: String) {
        val eventType = "TOKEN_REFRESH"
        val message = "Token refreshed"

        logger.info {
            buildAuditLogString(
                eventType = eventType,
                userId = userId,
                username = username,
                message = message
            )
        }

        persistAuditLog(
            eventType = eventType,
            userId = userId,
            username = username,
            message = message
        )
    }

    /**
     * Logs a token revocation event.
     *
     * @param userId User identifier
     * @param username Username
     * @param tokenId Token identifier that was revoked
     */
    fun logTokenRevocation(userId: String, username: String, tokenId: String) {
        val eventType = "TOKEN_REVOCATION"
        val message = "Token revoked"
        val additionalData = mapOf("tokenId" to tokenId)

        logger.info {
            buildAuditLogString(
                eventType = eventType,
                userId = userId,
                username = username,
                message = message,
                additionalData = additionalData
            )
        }

        persistAuditLog(
            eventType = eventType,
            userId = userId,
            username = username,
            message = message,
            additionalData = additionalData
        )
    }

    /**
     * Logs an account lockout event.
     *
     * @param userId User identifier
     * @param username Username
     * @param reason Reason for lockout
     */
    fun logAccountLockout(userId: String, username: String, reason: String) {
        val eventType = "ACCOUNT_LOCKOUT"
        val message = "Account locked: $reason"
        val additionalData = mapOf("reason" to reason)

        logger.warn {
            buildAuditLogString(
                eventType = eventType,
                userId = userId,
                username = username,
                message = message,
                additionalData = additionalData
            )
        }

        persistAuditLog(
            eventType = eventType,
            userId = userId,
            username = username,
            message = message,
            additionalData = additionalData
        )
    }

    /**
     * Logs a password change event.
     *
     * @param userId User identifier
     * @param username Username
     */
    fun logPasswordChange(userId: String, username: String) {
        val eventType = "PASSWORD_CHANGE"
        val message = "Password changed"

        logger.info {
            buildAuditLogString(
                eventType = eventType,
                userId = userId,
                username = username,
                message = message
            )
        }

        persistAuditLog(
            eventType = eventType,
            userId = userId,
            username = username,
            message = message
        )
    }

    /**
     * Logs an MFA enrollment event.
     *
     * @param userId User identifier
     * @param username Username
     */
    fun logMfaEnrollment(userId: String, username: String) {
        val eventType = "MFA_ENROLLMENT"
        val message = "MFA enrolled"

        logger.info {
            buildAuditLogString(
                eventType = eventType,
                userId = userId,
                username = username,
                message = message
            )
        }

        persistAuditLog(
            eventType = eventType,
            userId = userId,
            username = username,
            message = message
        )
    }

    /**
     * Logs an MFA verification event.
     *
     * @param userId User identifier
     * @param username Username
     * @param success Whether verification succeeded
     */
    fun logMfaVerification(userId: String, username: String, success: Boolean) {
        val eventType = "MFA_VERIFICATION"
        val message = if (success) "MFA verification successful" else "MFA verification failed"
        val additionalData = mapOf("success" to success.toString())

        val auditLogString = buildAuditLogString(
            eventType = eventType,
            userId = userId,
            username = username,
            message = message,
            additionalData = additionalData
        )

        if (success) {
            logger.info { auditLogString }
        } else {
            logger.warn { auditLogString }
        }

        persistAuditLog(
            eventType = eventType,
            userId = userId,
            username = username,
            message = message,
            additionalData = additionalData
        )
    }

    /**
     * Logs a rate limit exceeded event.
     *
     * @param username Username (if available)
     * @param ipAddress IP address
     * @param endpoint Endpoint that was rate limited
     */
    fun logRateLimitExceeded(username: String?, ipAddress: String?, endpoint: String) {
        val eventType = "RATE_LIMIT_EXCEEDED"
        val message = "Rate limit exceeded for endpoint: $endpoint"
        val additionalData = mapOf("endpoint" to endpoint)

        logger.warn {
            buildAuditLogString(
                eventType = eventType,
                username = username,
                ipAddress = ipAddress,
                message = message,
                additionalData = additionalData
            )
        }

        persistAuditLog(
            eventType = eventType,
            username = username,
            ipAddress = ipAddress,
            message = message,
            additionalData = additionalData
        )
    }

    private fun persistAuditLog(
        eventType: String,
        userId: String? = null,
        username: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        message: String,
        additionalData: Map<String, String> = emptyMap()
    ) {
        try {
            val logEntry = AuditLog(
                timestamp = Instant.now(),
                eventType = eventType,
                userId = userId,
                username = username,
                ipAddress = ipAddress,
                userAgent = userAgent,
                message = message,
                additionalData = additionalData
            )

            // Since save is synchronous in DynamoDbRepository and might be blocking,
            // in a Vert.x app we should ideally run this on a worker thread.
            // However, DynamoDbRepository uses the sync client.
            // For now, we'll execute it directly but wrap in try-catch to not affect the flow.
            // Ideally, AuditLogRepository should also offer an async save or we should wrap it.
            // Given the current structure, we will just call it.

            auditLogRepository?.save(logEntry)

        } catch (e: Exception) {
            logger.error(e) { "Failed to persist audit log" }
        }
    }

    /**
     * Builds a structured audit log message string.
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
    private fun buildAuditLogString(
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
