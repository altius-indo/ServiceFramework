package com.enterprise.framework.authz.repositories

import com.enterprise.framework.authz.models.Permission
import com.enterprise.framework.authz.models.PermissionGrant
import com.enterprise.framework.authz.models.SubjectType
import io.vertx.core.Vertx
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Repository for permission management
 */
class PermissionRepository(
    private val vertx: Vertx
) {
    private val permissions = mutableMapOf<String, Permission>()
    private val grants = mutableMapOf<String, PermissionGrant>()
    private val grantsBySubject = mutableMapOf<String, MutableList<String>>()

    suspend fun save(permission: Permission) {
        permissions[permission.id] = permission
        logger.debug { "Saved permission: ${permission.id}" }
    }

    suspend fun findById(permissionId: String): Permission? {
        return permissions[permissionId]
    }

    suspend fun findByResource(resource: String, tenantId: String?): List<Permission> {
        return permissions.values.filter {
            it.resource == resource && (tenantId == null || it.tenantId == tenantId)
        }
    }

    suspend fun grantPermission(grant: PermissionGrant) {
        grants[grant.id] = grant
        val key = "${grant.subjectId}:${grant.subjectType}"
        grantsBySubject.getOrPut(key) { mutableListOf() }.add(grant.id)
        logger.debug { "Granted permission ${grant.permissionId} to ${grant.subjectId}" }
    }

    suspend fun findBySubject(
        subjectId: String,
        subjectType: SubjectType,
        tenantId: String?
    ): List<PermissionGrant> {
        val key = "$subjectId:$subjectType"
        val grantIds = grantsBySubject[key] ?: return emptyList()
        
        return grantIds.mapNotNull { id ->
            val grant = grants[id]
            if (grant != null && (tenantId == null || grant.permissionId.let { 
                permissions[it]?.tenantId == tenantId 
            })) {
                // Check expiration
                if (grant.expiresAt == null || grant.expiresAt.isAfter(java.time.Instant.now())) {
                    grant
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    suspend fun revokePermission(grantId: String) {
        val grant = grants.remove(grantId)
        if (grant != null) {
            val key = "${grant.subjectId}:${grant.subjectType}"
            grantsBySubject[key]?.remove(grantId)
            logger.debug { "Revoked permission grant: $grantId" }
        }
    }
}

