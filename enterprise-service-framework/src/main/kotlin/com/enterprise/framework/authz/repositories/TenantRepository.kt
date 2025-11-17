package com.enterprise.framework.authz.repositories

import com.enterprise.framework.authz.models.Tenant
import io.vertx.core.Vertx
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Repository for tenant management
 */
class TenantRepository(
    private val vertx: Vertx
) {
    private val tenants = mutableMapOf<String, Tenant>()

    suspend fun save(tenant: Tenant) {
        tenants[tenant.id] = tenant
        logger.debug { "Saved tenant: ${tenant.id}" }
    }

    suspend fun findById(tenantId: String): Tenant? {
        return tenants[tenantId]
    }

    suspend fun findByDomain(domain: String): Tenant? {
        return tenants.values.firstOrNull { it.domain == domain }
    }

    suspend fun findAll(): List<Tenant> {
        return tenants.values.toList()
    }

    suspend fun delete(tenantId: String) {
        tenants.remove(tenantId)
        logger.debug { "Deleted tenant: $tenantId" }
    }
}

