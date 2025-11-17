package com.enterprise.framework.authz.models

import java.time.Instant

/**
 * Role model for RBAC
 * Supports hierarchical role structures with inheritance
 */
data class Role(
    val id: String,
    val name: String,
    val description: String? = null,
    val permissions: List<String> = emptyList(),
    val parentRoleId: String? = null, // For hierarchical roles
    val tenantId: String? = null, // For multi-tenancy
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 1L
)

/**
 * Role assignment at user or group level
 */
data class RoleAssignment(
    val id: String,
    val roleId: String,
    val subjectId: String, // User or group ID
    val subjectType: SubjectType,
    val tenantId: String? = null,
    val assignedBy: String,
    val assignedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null, // For temporary assignments
    val metadata: Map<String, Any> = emptyMap()
)

enum class SubjectType {
    USER, GROUP
}

