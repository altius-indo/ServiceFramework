package com.enterprise.framework.service

import com.enterprise.framework.repository.AuditLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class AuditLogServiceTest {

    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val auditLogService = AuditLogService(auditLogRepository)

    @Test
    fun `logLoginSuccess should persist audit log`() {
        val userId = "user123"
        val username = "testuser"
        val ipAddress = "127.0.0.1"
        val userAgent = "TestAgent"

        auditLogService.logLoginSuccess(userId, username, ipAddress, userAgent)

        verify {
            auditLogRepository.save(match {
                it.eventType == "LOGIN_SUCCESS" &&
                it.userId == userId &&
                it.username == username &&
                it.ipAddress == ipAddress &&
                it.userAgent == userAgent &&
                it.message == "User logged in successfully"
            })
        }
    }

    @Test
    fun `logLoginFailure should persist audit log with reason`() {
        val username = "testuser"
        val ipAddress = "127.0.0.1"
        val userAgent = "TestAgent"
        val reason = "Invalid password"

        auditLogService.logLoginFailure(username, ipAddress, userAgent, reason)

        verify {
            auditLogRepository.save(match {
                it.eventType == "LOGIN_FAILURE" &&
                it.username == username &&
                it.ipAddress == ipAddress &&
                it.userAgent == userAgent &&
                it.message == "Login failed: $reason" &&
                it.additionalData["reason"] == reason
            })
        }
    }
}
