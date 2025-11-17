package com.enterprise.framework.authz.engines

import com.enterprise.framework.authz.models.AuthzDecision
import com.enterprise.framework.authz.models.AuthorizationContext
import com.enterprise.framework.authz.models.SubjectType
import com.enterprise.framework.authz.repositories.RoleRepository
import com.enterprise.framework.authz.repositories.PermissionRepository
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Role-Based Access Control Engine
 * Supports hierarchical role structures with inheritance
 */
class RbacEngine(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository
) : AuthzEngine {

    override suspend fun supports(context: AuthorizationContext): Boolean {
        // RBAC supports all contexts, but may be overridden by other engines
        return true
    }

    override suspend fun authorize(context: AuthorizationContext): AuthzDecision {
        val startTime = System.currentTimeMillis()
        
        try {
            // Get all roles for the subject
            val roles = getSubjectRoles(context.subjectId, context.subjectType, context.tenantId)
            
            if (roles.isEmpty()) {
                return AuthzDecision(
                    allowed = false,
                    reason = "No roles assigned to subject",
                    evaluationTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Collect all permissions from roles (including inherited)
            val allPermissions = mutableSetOf<String>()
            val appliedRoles = mutableListOf<String>()

            for (role in roles) {
                appliedRoles.add(role.id)
                
                // Add direct permissions
                allPermissions.addAll(role.permissions)
                
                // Add inherited permissions from parent roles
                if (role.parentRoleId != null) {
                    val inheritedPermissions = getInheritedPermissions(role.parentRoleId, context.tenantId)
                    allPermissions.addAll(inheritedPermissions)
                }
            }

            // Check if any permission matches the required action on the resource
            val requiredPermission = "${context.resourceType}:${context.action}"
            val hasPermission = allPermissions.any { permission ->
                matchesPermission(permission, requiredPermission, context.resourceId)
            }

            val decision = AuthzDecision(
                allowed = hasPermission,
                reason = if (hasPermission) {
                    "Permission granted via role"
                } else {
                    "No matching permission found in roles: ${appliedRoles.joinToString()}"
                },
                appliedRoles = appliedRoles,
                evaluationTimeMs = System.currentTimeMillis() - startTime
            )

            logger.debug { "RBAC decision: ${decision.allowed} for ${context.subjectId} on ${context.resourceId}" }
            return decision

        } catch (e: Exception) {
            logger.error(e) { "Error in RBAC authorization" }
            return AuthzDecision(
                allowed = false,
                reason = "Authorization error: ${e.message}",
                evaluationTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Get all roles assigned to a subject
     */
    private suspend fun getSubjectRoles(
        subjectId: String,
        subjectType: SubjectType,
        tenantId: String?
    ): List<com.enterprise.framework.authz.models.Role> {
        val roleAssignments = roleRepository.findBySubject(subjectId, subjectType, tenantId)
        return roleAssignments.mapNotNull { assignment ->
            roleRepository.findById(assignment.roleId, tenantId)
        }
    }

    /**
     * Get all permissions from a role and its parent hierarchy
     */
    private suspend fun getInheritedPermissions(roleId: String, tenantId: String?): Set<String> {
        val permissions = mutableSetOf<String>()
        var currentRoleId: String? = roleId
        val visited = mutableSetOf<String>()

        while (currentRoleId != null && !visited.contains(currentRoleId)) {
            visited.add(currentRoleId)
            val role = roleRepository.findById(currentRoleId, tenantId)
            
            if (role != null) {
                permissions.addAll(role.permissions)
                currentRoleId = role.parentRoleId
            } else {
                break
            }
        }

        return permissions
    }

    /**
     * Check if a permission pattern matches the required permission
     * Supports wildcards and resource patterns
     */
    private fun matchesPermission(permission: String, requiredPermission: String, resourceId: String): Boolean {
        // Exact match
        if (permission == requiredPermission) return true
        
        // Wildcard match (e.g., "document:*" matches "document:read")
        val permissionParts = permission.split(":")
        val requiredParts = requiredPermission.split(":")
        
        if (permissionParts.size == 2 && requiredParts.size == 2) {
            val (permResource, permAction) = permissionParts
            val (reqResource, reqAction) = requiredParts
            
            // Resource wildcard
            if (permResource == "*" && permAction == reqAction) return true
            if (permResource == reqResource && permAction == "*") return true
            if (permResource == "*" && permAction == "*") return true
            
            // Resource pattern matching (e.g., "document:123" matches "document:*")
            if (permResource == reqResource && permAction == reqAction) return true
        }
        
        return false
    }
}

