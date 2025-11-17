package com.enterprise.framework.authz.repositories

import com.enterprise.framework.authz.models.Resource
import com.enterprise.framework.authz.models.ResourceShare
import io.vertx.core.Vertx
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Repository for resource management
 */
class ResourceRepository(
    private val vertx: Vertx
) {
    private val resources = mutableMapOf<String, Resource>()
    private val shares = mutableMapOf<String, ResourceShare>()
    private val sharesByResource = mutableMapOf<String, MutableList<String>>()

    suspend fun save(resource: Resource) {
        resources[resource.id] = resource
        logger.debug { "Saved resource: ${resource.id}" }
    }

    suspend fun findById(resourceId: String, tenantId: String?): Resource? {
        val resource = resources[resourceId]
        return if (resource != null && (tenantId == null || resource.tenantId == tenantId)) {
            resource
        } else {
            null
        }
    }

    suspend fun findByOwner(ownerId: String, tenantId: String?): List<Resource> {
        return resources.values.filter {
            it.ownerId == ownerId && (tenantId == null || it.tenantId == tenantId)
        }
    }

    suspend fun findByParent(parentResourceId: String, tenantId: String?): List<Resource> {
        return resources.values.filter {
            it.parentResourceId == parentResourceId && (tenantId == null || it.tenantId == tenantId)
        }
    }

    suspend fun shareResource(share: ResourceShare) {
        shares[share.id] = share
        sharesByResource.getOrPut(share.resourceId) { mutableListOf() }.add(share.id)
        logger.debug { "Shared resource ${share.resourceId} with ${share.sharedWith}" }
    }

    suspend fun findSharesByResource(resourceId: String): List<ResourceShare> {
        val shareIds = sharesByResource[resourceId] ?: return emptyList()
        return shareIds.mapNotNull { id ->
            val share = shares[id]
            if (share != null && (share.expiresAt == null || share.expiresAt.isAfter(java.time.Instant.now()))) {
                share
            } else {
                null
            }
        }
    }

    suspend fun findSharesByUser(userId: String): List<ResourceShare> {
        return shares.values.filter {
            it.sharedWith == userId && 
            (it.expiresAt == null || it.expiresAt.isAfter(java.time.Instant.now()))
        }
    }

    suspend fun revokeShare(shareId: String) {
        val share = shares.remove(shareId)
        if (share != null) {
            sharesByResource[share.resourceId]?.remove(shareId)
            logger.debug { "Revoked share: $shareId" }
        }
    }

    suspend fun delete(resourceId: String) {
        resources.remove(resourceId)
        // Also remove all shares
        sharesByResource[resourceId]?.forEach { shareId ->
            shares.remove(shareId)
        }
        sharesByResource.remove(resourceId)
        logger.debug { "Deleted resource: $resourceId" }
    }
}

