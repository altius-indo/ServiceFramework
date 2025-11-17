package com.enterprise.framework.authz.repositories

import com.enterprise.framework.authz.models.Relationship
import com.enterprise.framework.authz.models.Resource
import io.vertx.core.Vertx
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Repository for relationship management (ReBAC)
 */
class RelationshipRepository(
    private val vertx: Vertx
) {
    private val relationships = mutableMapOf<String, Relationship>()
    private val relationshipsBySource = mutableMapOf<String, MutableList<String>>()
    private val relationshipsByTarget = mutableMapOf<String, MutableList<String>>()
    private val resources = mutableMapOf<String, Resource>()

    suspend fun save(relationship: Relationship) {
        relationships[relationship.id] = relationship
        relationshipsBySource.getOrPut(relationship.sourceId) { mutableListOf() }
            .add(relationship.id)
        relationshipsByTarget.getOrPut(relationship.targetId) { mutableListOf() }
            .add(relationship.id)
        logger.debug { "Saved relationship: ${relationship.id}" }
    }

    suspend fun findRelationships(
        sourceId: String? = null,
        targetId: String? = null,
        relationType: com.enterprise.framework.authz.models.RelationshipType? = null,
        tenantId: String?
    ): List<Relationship> {
        val results = when {
            sourceId != null && targetId != null -> {
                // Find relationships between specific source and target
                val sourceIds = relationshipsBySource[sourceId] ?: return emptyList()
                val targetIds = relationshipsByTarget[targetId] ?: return emptyList()
                val commonIds = sourceIds.intersect(targetIds.toSet())
                commonIds.mapNotNull { relationships[it] }
            }
            sourceId != null -> {
                val ids = relationshipsBySource[sourceId] ?: return emptyList()
                ids.mapNotNull { relationships[it] }
            }
            targetId != null -> {
                val ids = relationshipsByTarget[targetId] ?: return emptyList()
                ids.mapNotNull { relationships[it] }
            }
            else -> relationships.values.toList()
        }

        return results.filter { relationship ->
            (relationType == null || relationship.relationType == relationType) &&
            (tenantId == null || relationship.tenantId == tenantId) &&
            (relationship.expiresAt == null || relationship.expiresAt.isAfter(java.time.Instant.now()))
        }
    }

    suspend fun findRelationshipsBySource(
        sourceId: String,
        tenantId: String?
    ): List<Relationship> {
        val ids = relationshipsBySource[sourceId] ?: return emptyList()
        return ids.mapNotNull { id ->
            val rel = relationships[id]
            if (rel != null && (tenantId == null || rel.tenantId == tenantId) &&
                (rel.expiresAt == null || rel.expiresAt.isAfter(java.time.Instant.now()))) {
                rel
            } else {
                null
            }
        }
    }

    suspend fun saveResource(resource: Resource) {
        resources[resource.id] = resource
        logger.debug { "Saved resource: ${resource.id}" }
    }

    suspend fun findResource(resourceId: String, tenantId: String?): Resource? {
        val resource = resources[resourceId]
        return if (resource != null && (tenantId == null || resource.tenantId == tenantId)) {
            resource
        } else {
            null
        }
    }

    suspend fun delete(relationshipId: String) {
        val relationship = relationships.remove(relationshipId)
        if (relationship != null) {
            relationshipsBySource[relationship.sourceId]?.remove(relationshipId)
            relationshipsByTarget[relationship.targetId]?.remove(relationshipId)
            logger.debug { "Deleted relationship: $relationshipId" }
        }
    }
}

