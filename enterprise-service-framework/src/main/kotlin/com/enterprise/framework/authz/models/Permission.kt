package com.enterprise.framework.authz.models

import java.time.Instant

/**
 * Permission model with granularity support
 * Supports resource-level, field-level, and operation-level permissions
 */
data class Permission(
    val id: String,
    val name: String,
    val resource: String, // Resource identifier or pattern
    val action: String, // read, write, delete, admin, etc.
    val description: String? = null,
    val field: String? = null, // For field-level permissions
    val conditions: Map<String, Any>? = null, // Conditional permissions
    val expiresAt: Instant? = null, // Time-based permissions
    val tenantId: String? = null,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now()
)

/**
 * Permission grant to a subject (user/group/role)
 */
data class PermissionGrant(
    val id: String,
    val permissionId: String,
    val subjectId: String,
    val subjectType: SubjectType,
    val resourceId: String? = null, // Specific resource instance
    val grantedBy: String,
    val grantedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val delegatedFrom: String? = null, // For delegation chains
    val scope: String? = null, // Scope limitations
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Permission inheritance rule
 */
data class PermissionInheritance(
    val id: String,
    val parentResourceId: String,
    val childResourceId: String,
    val inheritActions: List<String>, // Which actions to inherit
    val overrideActions: List<String> = emptyList(), // Actions to override
    val tenantId: String? = null
)

