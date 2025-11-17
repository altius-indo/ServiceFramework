package com.enterprise.framework.authz.repositories

import com.enterprise.framework.authz.models.Policy
import io.vertx.core.Vertx
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Repository for policy management
 */
class PolicyRepository(
    private val vertx: Vertx
) {
    private val policies = mutableMapOf<String, Policy>()

    suspend fun save(policy: Policy) {
        policies[policy.id] = policy
        logger.debug { "Saved policy: ${policy.id}" }
    }

    suspend fun findById(policyId: String): Policy? {
        return policies[policyId]
    }

    suspend fun findApplicablePolicies(
        resourceType: String,
        action: String,
        tenantId: String?
    ): List<Policy> {
        return policies.values.filter { policy ->
            if (!policy.enabled) return@filter false
            if (tenantId != null && policy.tenantId != tenantId) return@filter false
            
            // Check if resource type matches
            val resourceMatches = policy.resources.any { pattern ->
                matchesResourcePattern(resourceType, pattern)
            }
            
            // Check if action matches
            val actionMatches = policy.actions.any { pattern ->
                matchesPattern(action, pattern)
            }
            
            resourceMatches && actionMatches
        }
    }

    suspend fun findByTenant(tenantId: String): List<Policy> {
        return policies.values.filter { it.tenantId == tenantId }
    }

    suspend fun delete(policyId: String) {
        policies.remove(policyId)
        logger.debug { "Deleted policy: $policyId" }
    }

    private fun matchesPattern(value: String, pattern: String): Boolean {
        if (pattern == "*") return true
        if (pattern.contains("*")) {
            val regex = pattern.replace("*", ".*").toRegex()
            return regex.matches(value)
        }
        return value == pattern
    }

    private fun matchesResourcePattern(resourceType: String, pattern: String): Boolean {
        if (pattern == "*") return true
        if (pattern.contains(":")) {
            val parts = pattern.split(":")
            if (parts.size == 2) {
                return matchesPattern(resourceType, parts[0])
            }
        }
        return matchesPattern(resourceType, pattern)
    }
}

