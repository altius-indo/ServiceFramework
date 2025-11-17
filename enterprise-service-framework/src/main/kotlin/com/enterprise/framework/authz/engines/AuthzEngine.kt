package com.enterprise.framework.authz.engines

import com.enterprise.framework.authz.models.AuthzDecision
import com.enterprise.framework.authz.models.AuthorizationContext

/**
 * Base authorization engine interface
 * All authorization engines must implement this interface
 */
interface AuthzEngine {
    /**
     * Authorize a request based on the provided context
     * @param context The authorization context containing subject, resource, action, and attributes
     * @return Authorization decision (allowed/denied with reason)
     */
    suspend fun authorize(context: AuthorizationContext): AuthzDecision
    
    /**
     * Check if this engine supports the given context
     * @param context The authorization context
     * @return true if this engine can evaluate the context
     */
    suspend fun supports(context: AuthorizationContext): Boolean
}

