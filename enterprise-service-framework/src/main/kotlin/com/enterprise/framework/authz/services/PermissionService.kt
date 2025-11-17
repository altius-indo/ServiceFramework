package com.enterprise.framework.authz.services

import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.PermissionRepository
import com.enterprise.framework.authz.repositories.RoleRepository
import io.vertx.core.Vertx
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Permission management service
 * Handles permission grants, delegation, and inheritance
 */
class PermissionService(
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository
) {

    suspend fun createPermission(permission: Permission): Permission {
        val permissionWithId = permission.copy(
            id = permission.id.ifEmpty { UUID.randomUUID().toString() }
        )
        permissionRepository.save(permissionWithId)
        logger.info { "Created permission: ${permissionWithId.id}" }
        return permissionWithId
    }

    suspend fun grantPermission(grant: PermissionGrant): PermissionGrant {
        val grantWithId = grant.copy(
            id = grant.id.ifEmpty { UUID.randomUUID().toString() },
            grantedAt = Instant.now()
        )
        permissionRepository.grantPermission(grantWithId)
        logger.info { "Granted permission ${grantWithId.permissionId} to ${grantWithId.subjectId}" }
        return grantWithId
    }

    suspend fun delegatePermission(
        permissionId: String,
        fromSubjectId: String,
        toSubjectId: String,
        delegatedBy: String,
        expiresAt: Instant? = null,
        scope: String? = null
    ): PermissionGrant {
        // Check if delegator has the permission
        val delegatorGrants = permissionRepository.findBySubject(
            fromSubjectId,
            SubjectType.USER,
            null
        )
        
        val delegatorHasPermission = delegatorGrants.any { it.permissionId == permissionId }
        if (!delegatorHasPermission) {
            throw IllegalArgumentException("Delegator does not have permission to delegate")
        }

        val delegation = PermissionGrant(
            id = UUID.randomUUID().toString(),
            permissionId = permissionId,
            subjectId = toSubjectId,
            subjectType = SubjectType.USER,
            grantedBy = delegatedBy,
            delegatedFrom = fromSubjectId,
            expiresAt = expiresAt,
            scope = scope
        )

        permissionRepository.grantPermission(delegation)
        logger.info { "Delegated permission $permissionId from $fromSubjectId to $toSubjectId" }
        return delegation
    }

    suspend fun revokePermission(grantId: String) {
        permissionRepository.revokePermission(grantId)
        logger.info { "Revoked permission grant: $grantId" }
    }

    suspend fun getSubjectPermissions(
        subjectId: String,
        subjectType: SubjectType,
        tenantId: String?
    ): List<Permission> {
        val grants = permissionRepository.findBySubject(subjectId, subjectType, tenantId)
        return grants.mapNotNull { grant ->
            permissionRepository.findById(grant.permissionId)
        }
    }

    suspend fun checkPermission(
        subjectId: String,
        resourceId: String,
        action: String,
        tenantId: String?
    ): Boolean {
        val grants = permissionRepository.findBySubject(subjectId, SubjectType.USER, tenantId)
        
        return grants.any { grant ->
            val permission = permissionRepository.findById(grant.permissionId)
            permission != null && 
            matchesResource(permission.resource, resourceId) &&
            permission.action == action &&
            (grant.resourceId == null || grant.resourceId == resourceId)
        }
    }

    private fun matchesResource(pattern: String, resourceId: String): Boolean {
        if (pattern == "*") return true
        if (pattern.contains("*")) {
            val regex = pattern.replace("*", ".*").toRegex()
            return regex.matches(resourceId)
        }
        return pattern == resourceId
    }
}

