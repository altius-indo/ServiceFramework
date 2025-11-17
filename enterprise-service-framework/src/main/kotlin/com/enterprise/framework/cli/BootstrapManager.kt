package com.enterprise.framework.cli

import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.*
import com.enterprise.framework.model.User
import com.enterprise.framework.repository.UserRepository
import com.enterprise.framework.service.PasswordHashingService
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Bootstrap Manager for initial authentication and authorization setup
 * Creates the first admin user and sets up initial roles and policies
 */
class BootstrapManager(
    private val vertx: Vertx,
    private val userRepository: UserRepository,
    private val passwordHashingService: PasswordHashingService,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val policyRepository: PolicyRepository
) {

    suspend fun bootstrap(
        adminUsername: String,
        adminPassword: String,
        adminEmail: String
    ): BootstrapResult {
        logger.info { "Starting bootstrap process..." }

        // Check if bootstrap already completed
        if (isBootstrapComplete()) {
            logger.warn { "Bootstrap already completed. Use --force to re-bootstrap." }
            return BootstrapResult(
                success = false,
                message = "Bootstrap already completed. System is already initialized."
            )
        }

        try {
            // 1. Create admin user
            val adminUser = createAdminUser(adminUsername, adminPassword, adminEmail)
            logger.info { "Admin user created: ${adminUser.userId}" }

            // 2. Create bootstrap roles
            val adminRole = createAdminRole()
            logger.info { "Admin role created: ${adminRole.id}" }

            // 3. Assign admin role to user
            assignAdminRole(adminUser.userId, adminRole.id)
            logger.info { "Admin role assigned to user" }

            // 4. Create initial permissions
            createInitialPermissions()
            logger.info { "Initial permissions created" }

            // 5. Create bootstrap policies
            createBootstrapPolicies()
            logger.info { "Bootstrap policies created" }

            // 6. Mark bootstrap as complete
            markBootstrapComplete()

            logger.info { "Bootstrap completed successfully" }

            return BootstrapResult(
                success = true,
                message = "Bootstrap completed successfully. Admin user created: $adminUsername",
                adminUserId = adminUser.userId,
                adminRoleId = adminRole.id
            )

        } catch (e: Exception) {
            logger.error(e) { "Bootstrap failed" }
            return BootstrapResult(
                success = false,
                message = "Bootstrap failed: ${e.message}"
            )
        }
    }

    suspend fun forceBootstrap(
        adminUsername: String,
        adminPassword: String,
        adminEmail: String
    ): BootstrapResult {
        logger.warn { "Force bootstrap requested - resetting system..." }
        
        // Clear existing bootstrap markers (in production, use proper cleanup)
        // For now, we'll proceed with bootstrap
        
        return bootstrap(adminUsername, adminPassword, adminEmail)
    }

    private suspend fun isBootstrapComplete(): Boolean {
        // Check if admin role exists
        val adminRole = roleRepository.findByName("SystemAdmin", null)
        return adminRole != null
    }

    private suspend fun createAdminUser(
        username: String,
        password: String,
        email: String
    ): User {
        val userId = UUID.randomUUID().toString()
        val (passwordHash, salt) = passwordHashingService.hashPassword(password)

        val user = User(
            userId = userId,
            username = username,
            email = email,
            enabled = true,
            locked = false,
            mfaEnabled = false,
            roles = setOf("SystemAdmin"),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            failedLoginAttempts = 0,
            lockedUntil = null,
            lastLoginAt = null,
            passwordChangedAt = Instant.now()
        )

        // Create user with credentials
        val credential = com.enterprise.framework.model.Credential(
            userId = userId,
            passwordHash = passwordHash,
            algorithm = "argon2id",
            salt = salt,
            passwordHistory = emptyList(),
            createdAt = Instant.now()
        )
        
        // Save user and credentials
        userRepository.createUser(user, credential).await()
        
        return user
    }

    private suspend fun createAdminRole(): Role {
        val roleId = UUID.randomUUID().toString()
        
        val role = Role(
            id = roleId,
            name = "SystemAdmin",
            description = "System Administrator with full access",
            permissions = listOf(
                "*:*", // Full access
                "auth:*",
                "authz:*",
                "policy:*",
                "user:*",
                "role:*",
                "permission:*"
            ),
            tenantId = null,
            metadata = mapOf("bootstrap" to true),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        roleRepository.save(role)
        return role
    }

    private suspend fun assignAdminRole(userId: String, roleId: String) {
        val assignment = RoleAssignment(
            id = UUID.randomUUID().toString(),
            roleId = roleId,
            subjectId = userId,
            subjectType = SubjectType.USER,
            assignedBy = "system",
            tenantId = null
        )

        roleRepository.assignRole(assignment)
    }

    private suspend fun createInitialPermissions() {
        val permissions = listOf(
            Permission(
                id = UUID.randomUUID().toString(),
                name = "Full Access",
                resource = "*",
                action = "*",
                description = "Full system access",
                tenantId = null
            ),
            Permission(
                id = UUID.randomUUID().toString(),
                name = "User Management",
                resource = "user",
                action = "*",
                description = "Manage users",
                tenantId = null
            ),
            Permission(
                id = UUID.randomUUID().toString(),
                name = "Role Management",
                resource = "role",
                action = "*",
                description = "Manage roles",
                tenantId = null
            ),
            Permission(
                id = UUID.randomUUID().toString(),
                name = "Policy Management",
                resource = "policy",
                action = "*",
                description = "Manage policies",
                tenantId = null
            ),
            Permission(
                id = UUID.randomUUID().toString(),
                name = "Authorization Management",
                resource = "authz",
                action = "*",
                description = "Manage authorization",
                tenantId = null
            )
        )

        permissions.forEach { permission ->
            permissionRepository.save(permission)
        }
    }

    private suspend fun createBootstrapPolicies() {
        val policies = listOf(
            Policy(
                id = UUID.randomUUID().toString(),
                name = "Bootstrap Admin Policy",
                description = "Full access for bootstrap admin",
                effect = PolicyEffect.ALLOW,
                actions = listOf("*"),
                resources = listOf("*"),
                subjects = listOf("SystemAdmin"),
                priority = 1000,
                tenantId = null,
                enabled = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            ),
            Policy(
                id = UUID.randomUUID().toString(),
                name = "Default Deny Policy",
                description = "Default deny all access",
                effect = PolicyEffect.DENY,
                actions = listOf("*"),
                resources = listOf("*"),
                priority = 0,
                tenantId = null,
                enabled = false, // Disabled by default
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        policies.forEach { policy ->
            policyRepository.save(policy)
        }
    }

    private suspend fun markBootstrapComplete() {
        // In production, store bootstrap status in database
        // For now, we rely on the existence of admin role
        logger.info { "Bootstrap marked as complete" }
    }
}

data class BootstrapResult(
    val success: Boolean,
    val message: String,
    val adminUserId: String? = null,
    val adminRoleId: String? = null
)

