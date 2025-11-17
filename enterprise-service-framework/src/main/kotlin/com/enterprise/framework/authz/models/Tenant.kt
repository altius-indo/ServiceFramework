package com.enterprise.framework.authz.models

import java.time.Instant

/**
 * Tenant model for multi-tenancy
 */
data class Tenant(
    val id: String,
    val name: String,
    val domain: String? = null,
    val parentTenantId: String? = null, // For sub-tenants
    val adminUserId: String,
    val status: TenantStatus = TenantStatus.ACTIVE,
    val quotas: TenantQuotas = TenantQuotas(),
    val configuration: Map<String, Any> = emptyMap(),
    val encryptionKeyId: String? = null, // Separate encryption key per tenant
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class TenantStatus {
    ACTIVE, SUSPENDED, DELETED
}

/**
 * Tenant quotas and limits
 */
data class TenantQuotas(
    val maxUsers: Long? = null,
    val maxResources: Long? = null,
    val maxStorageBytes: Long? = null,
    val maxApiCallsPerSecond: Long? = null,
    val maxApiCallsPerDay: Long? = null
)

/**
 * Cross-tenant collaboration configuration
 */
data class CrossTenantCollaboration(
    val id: String,
    val tenantId: String,
    val collaboratorTenantId: String,
    val sharedSpaces: List<String> = emptyList(),
    val allowedActions: List<String> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: Instant = Instant.now()
)

