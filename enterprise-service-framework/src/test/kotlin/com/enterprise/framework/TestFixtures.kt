package com.enterprise.framework

import com.enterprise.framework.model.Credential
import com.enterprise.framework.model.User
import java.time.Instant
import java.util.UUID

object TestFixtures {

    fun createTestUser(
        userId: String = UUID.randomUUID().toString(),
        username: String = "testuser",
        email: String = "test@example.com",
        enabled: Boolean = true,
        roles: Set<String> = setOf("USER"),
        failedLoginAttempts: Int = 0,
        lockedUntil: Instant? = null
    ): User {
        return User(
            userId = userId,
            username = username,
            email = email,
            enabled = enabled,
            roles = roles,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            failedLoginAttempts = failedLoginAttempts,
            lockedUntil = lockedUntil,
            lastLoginAt = null,
            passwordChangedAt = Instant.now()
        )
    }

    fun createTestCredential(
        userId: String,
        passwordHash: String = "\$argon2id\$v=19\$m=65536,t=3,p=4\$test\$hash",
        algorithm: String = "argon2id",
        salt: String = "test-salt",
        passwordHistory: List<String> = emptyList()
    ): Credential {
        return Credential(
            userId = userId,
            passwordHash = passwordHash,
            algorithm = algorithm,
            salt = salt,
            passwordHistory = passwordHistory,
            createdAt = Instant.now()
        )
    }

    fun createJwtPayload(
        sub: String = "testuser",
        userId: String = UUID.randomUUID().toString(),
        roles: List<String> = listOf("USER"),
        sessionId: String = UUID.randomUUID().toString()
    ): Map<String, Any> {
        return mapOf(
            "sub" to sub,
            "userId" to userId,
            "roles" to roles,
            "sessionId" to sessionId,
            "iat" to (System.currentTimeMillis() / 1000),
            "exp" to ((System.currentTimeMillis() / 1000) + 3600)
        )
    }

    fun createLoginRequest(
        username: String = "testuser",
        password: String = "TestPassword123!"
    ): String {
        return """
            {
                "username": "$username",
                "password": "$password"
            }
        """.trimIndent()
    }

    fun createResourceRequest(
        name: String = "Test Resource",
        description: String = "Test Description"
    ): String {
        return """
            {
                "name": "$name",
                "description": "$description",
                "data": {
                    "key1": "value1",
                    "key2": "value2"
                }
            }
        """.trimIndent()
    }
}
