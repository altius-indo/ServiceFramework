package com.enterprise.framework.authz.utils

import com.enterprise.framework.authz.handlers.AuthorizationHandler
import com.enterprise.framework.authz.services.*
import io.vertx.core.Vertx

/**
 * Utility functions for authorization
 */
object AuthorizationUtils {

    /**
     * Get authorization services from vertx shared data
     */
    fun getAuthorizationService(vertx: Vertx): AuthorizationService? {
        return vertx.sharedData().getLocalMap<String, Any>("authz.services")
            .get("authorizationService") as? AuthorizationService
    }

    fun getPolicyService(vertx: Vertx): PolicyService? {
        return vertx.sharedData().getLocalMap<String, Any>("authz.services")
            .get("policyService") as? PolicyService
    }

    fun getPermissionService(vertx: Vertx): PermissionService? {
        return vertx.sharedData().getLocalMap<String, Any>("authz.services")
            .get("permissionService") as? PermissionService
    }

    fun getAuditService(vertx: Vertx): AuditService? {
        return vertx.sharedData().getLocalMap<String, Any>("authz.services")
            .get("auditService") as? AuditService
    }

    fun getDynamicAuthzService(vertx: Vertx): DynamicAuthorizationService? {
        return vertx.sharedData().getLocalMap<String, Any>("authz.services")
            .get("dynamicAuthzService") as? DynamicAuthorizationService
    }

    /**
     * Create an authorization handler with required action
     */
    fun createAuthorizationHandler(
        vertx: Vertx,
        action: String,
        resourceType: String? = null
    ): AuthorizationHandler? {
        val authzService = getAuthorizationService(vertx)
        val auditService = getAuditService(vertx)
        val dynamicAuthzService = getDynamicAuthzService(vertx)
        
        return if (authzService != null && auditService != null && dynamicAuthzService != null) {
            AuthorizationHandler(authzService, auditService, dynamicAuthzService, action, resourceType)
        } else {
            null
        }
    }
}

