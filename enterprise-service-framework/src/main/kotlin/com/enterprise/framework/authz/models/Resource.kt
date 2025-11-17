package com.enterprise.framework.authz.models

import java.time.Instant

/**
 * Resource model for authorization
 */
data class Resource(
    val id: String,
    val type: String, // e.g., "document", "folder", "api", "event"
    val name: String,
    val ownerId: String,
    val tenantId: String? = null,
    val parentResourceId: String? = null, // For hierarchical resources
    val visibility: ResourceVisibility = ResourceVisibility.PRIVATE,
    val attributes: Map<String, Any> = emptyMap(), // For ABAC
    val tags: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class ResourceVisibility {
    PUBLIC, PRIVATE, SHARED
}

/**
 * Resource sharing configuration
 */
data class ResourceShare(
    val id: String,
    val resourceId: String,
    val sharedWith: String, // User or group ID
    val sharedWithType: SubjectType,
    val permissions: List<String>, // Actions allowed
    val sharedBy: String,
    val sharedAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val linkToken: String? = null, // For link-based sharing
    val linkPassword: String? = null, // Optional password for link
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Resource ownership transfer record
 */
data class OwnershipTransfer(
    val id: String,
    val resourceId: String,
    val fromOwnerId: String,
    val toOwnerId: String,
    val transferredBy: String,
    val transferredAt: Instant = Instant.now(),
    val reason: String? = null
)

