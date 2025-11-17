package com.enterprise.framework.authz.models

import java.time.Instant

/**
 * Relationship model for ReBAC
 * Models relationships between users and resources
 */
data class Relationship(
    val id: String,
    val sourceId: String, // User or resource ID
    val sourceType: RelationshipEntityType,
    val targetId: String, // User or resource ID
    val targetType: RelationshipEntityType,
    val relationType: RelationshipType,
    val tenantId: String? = null,
    val attributes: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null
)

enum class RelationshipEntityType {
    USER, RESOURCE, GROUP, TENANT
}

enum class RelationshipType {
    // User-User relationships
    FRIEND, COLLEAGUE, MANAGER, REPORTS_TO, MEMBER_OF,
    
    // User-Resource relationships
    OWNER, EDITOR, VIEWER, ADMIN, SHARED_WITH,
    
    // Resource-Resource relationships
    PARENT, CHILD, RELATED_TO, DEPENDS_ON,
    
    // Group relationships
    MEMBER, ADMIN_OF
}

/**
 * Relationship path for transitive relationships
 */
data class RelationshipPath(
    val sourceId: String,
    val targetId: String,
    val path: List<Relationship>,
    val distance: Int
)

