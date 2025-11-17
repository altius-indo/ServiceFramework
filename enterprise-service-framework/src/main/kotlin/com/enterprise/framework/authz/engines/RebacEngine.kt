package com.enterprise.framework.authz.engines

import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.RelationshipRepository
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Relationship-Based Access Control Engine
 * Supports graph-based access control decisions
 */
class RebacEngine(
    private val relationshipRepository: RelationshipRepository
) : AuthzEngine {

    override suspend fun supports(context: AuthorizationContext): Boolean {
        // ReBAC supports all contexts, but is typically used for resource access
        return true
    }

    override suspend fun authorize(context: AuthorizationContext): AuthzDecision {
        val startTime = System.currentTimeMillis()
        
        try {
            // Check direct relationships
            val directRelationships = relationshipRepository.findRelationships(
                sourceId = context.subjectId,
                targetId = context.resourceId,
                tenantId = context.tenantId
            )

            // Check if any direct relationship grants the required action
            for (relationship in directRelationships) {
                if (relationshipGrantedAction(relationship, context.action)) {
                    return AuthzDecision(
                        allowed = true,
                        reason = "Direct relationship '${relationship.relationType}' grants access",
                        evaluationTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            // Check transitive relationships (e.g., friends of friends)
            val transitivePaths = findTransitivePaths(
                sourceId = context.subjectId,
                targetId = context.resourceId,
                maxDepth = 3, // Limit depth to prevent performance issues
                tenantId = context.tenantId
            )

            for (path in transitivePaths) {
                // Check if the path grants access
                if (pathGrantedAction(path, context.action)) {
                    return AuthzDecision(
                        allowed = true,
                        reason = "Transitive relationship path grants access (depth: ${path.distance})",
                        evaluationTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            // Check ownership
            val resource = relationshipRepository.findResource(context.resourceId, context.tenantId)
            if (resource != null && resource.ownerId == context.subjectId) {
                return AuthzDecision(
                    allowed = true,
                    reason = "Subject is the owner of the resource",
                    evaluationTimeMs = System.currentTimeMillis() - startTime
                )
            }

            return AuthzDecision(
                allowed = false,
                reason = "No relationship found that grants access",
                evaluationTimeMs = System.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            logger.error(e) { "Error in ReBAC authorization" }
            return AuthzDecision(
                allowed = false,
                reason = "Authorization error: ${e.message}",
                evaluationTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Check if a relationship grants a specific action
     */
    private fun relationshipGrantedAction(relationship: Relationship, action: String): Boolean {
        return when (relationship.relationType) {
            RelationshipType.OWNER -> true // Owner has all permissions
            RelationshipType.ADMIN -> action in listOf("read", "write", "delete", "admin")
            RelationshipType.EDITOR -> action in listOf("read", "write")
            RelationshipType.VIEWER -> action == "read"
            RelationshipType.SHARED_WITH -> {
                // Check attributes for specific permissions
                val permissions = relationship.attributes["permissions"] as? List<*>
                permissions?.contains(action) ?: false
            }
            else -> false
        }
    }

    /**
     * Check if a relationship path grants an action
     */
    private fun pathGrantedAction(path: RelationshipPath, action: String): Boolean {
        // For transitive paths, we need all relationships in the path to be valid
        // and the final relationship must grant the action
        if (path.path.isEmpty()) return false
        
        val finalRelationship = path.path.last()
        return relationshipGrantedAction(finalRelationship, action)
    }

    /**
     * Find transitive paths between source and target
     * Uses breadth-first search with depth limit
     */
    private suspend fun findTransitivePaths(
        sourceId: String,
        targetId: String,
        maxDepth: Int,
        tenantId: String?
    ): List<RelationshipPath> {
        val paths = mutableListOf<RelationshipPath>()
        val queue = ArrayDeque<Pair<String, List<Relationship>>>()
        val visited = mutableSetOf<String>()
        
        queue.add(Pair(sourceId, emptyList()))
        visited.add(sourceId)

        while (queue.isNotEmpty()) {
            val (currentId, currentPath) = queue.removeFirst()
            
            if (currentPath.size >= maxDepth) continue
            
            // Get all relationships from current node
            val relationships = relationshipRepository.findRelationshipsBySource(
                sourceId = currentId,
                tenantId = tenantId
            )

            for (relationship in relationships) {
                val nextId = relationship.targetId
                val newPath = currentPath + relationship

                if (nextId == targetId) {
                    // Found a path to target
                    paths.add(RelationshipPath(
                        sourceId = sourceId,
                        targetId = targetId,
                        path = newPath,
                        distance = newPath.size
                    ))
                } else if (!visited.contains(nextId) && newPath.size < maxDepth) {
                    visited.add(nextId)
                    queue.add(Pair(nextId, newPath))
                }
            }
        }

        return paths
    }
}

