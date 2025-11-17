package com.enterprise.framework.authz.repositories

import com.enterprise.framework.authz.models.Role
import com.enterprise.framework.authz.models.RoleAssignment
import com.enterprise.framework.authz.models.SubjectType
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Repository for role management
 * In a production system, this would use DynamoDB or another persistent store
 */
class RoleRepository(
    private val vertx: Vertx
) {
    // In-memory storage for now - replace with DynamoDB in production
    private val roles = mutableMapOf<String, Role>()
    private val roleAssignments = mutableMapOf<String, RoleAssignment>()
    private val assignmentsBySubject = mutableMapOf<String, MutableList<String>>()

    suspend fun save(role: Role) {
        roles[role.id] = role
        logger.debug { "Saved role: ${role.id}" }
    }

    suspend fun findById(roleId: String, tenantId: String?): Role? {
        val role = roles[roleId]
        return if (role != null && (tenantId == null || role.tenantId == tenantId)) {
            role
        } else {
            null
        }
    }

    suspend fun findByName(name: String, tenantId: String?): Role? {
        return roles.values.firstOrNull { 
            it.name == name && (tenantId == null || it.tenantId == tenantId)
        }
    }

    suspend fun assignRole(assignment: RoleAssignment) {
        roleAssignments[assignment.id] = assignment
        val key = "${assignment.subjectId}:${assignment.subjectType}"
        assignmentsBySubject.getOrPut(key) { mutableListOf() }.add(assignment.id)
        logger.debug { "Assigned role ${assignment.roleId} to ${assignment.subjectId}" }
    }

    suspend fun findBySubject(
        subjectId: String,
        subjectType: SubjectType,
        tenantId: String?
    ): List<RoleAssignment> {
        val key = "$subjectId:$subjectType"
        val assignmentIds = assignmentsBySubject[key] ?: return emptyList()
        
        return assignmentIds.mapNotNull { id ->
            val assignment = roleAssignments[id]
            if (assignment != null && (tenantId == null || assignment.tenantId == tenantId)) {
                // Check expiration
                if (assignment.expiresAt == null || assignment.expiresAt.isAfter(java.time.Instant.now())) {
                    assignment
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    suspend fun revokeRole(assignmentId: String) {
        val assignment = roleAssignments.remove(assignmentId)
        if (assignment != null) {
            val key = "${assignment.subjectId}:${assignment.subjectType}"
            assignmentsBySubject[key]?.remove(assignmentId)
            logger.debug { "Revoked role assignment: $assignmentId" }
        }
    }

    suspend fun delete(roleId: String) {
        roles.remove(roleId)
        // Also remove all assignments
        roleAssignments.values.removeAll { it.roleId == roleId }
        logger.debug { "Deleted role: $roleId" }
    }
}

