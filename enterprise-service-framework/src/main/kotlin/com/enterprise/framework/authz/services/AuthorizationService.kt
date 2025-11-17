package com.enterprise.framework.authz.services

import com.enterprise.framework.authz.engines.*
import com.enterprise.framework.authz.models.*
import com.enterprise.framework.service.RedisCacheService
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Main authorization service that orchestrates multiple authorization engines
 * Implements Policy Decision Point (PDP) pattern
 */
class AuthorizationService(
    private val vertx: Vertx,
    private val rbacEngine: RbacEngine,
    private val abacEngine: AbacEngine,
    private val rebacEngine: RebacEngine,
    private val cacheService: RedisCacheService? = null
) {
    private val engines = listOf(rbacEngine, abacEngine, rebacEngine)

    /**
     * Authorize a request using all applicable engines
     * Returns ALLOW if any engine allows, DENY if all engines deny or any engine explicitly denies
     */
    suspend fun authorize(context: AuthorizationContext): AuthzDecision {
        val startTime = System.currentTimeMillis()
        
        try {
            // Check cache first
            val cacheKey = generateCacheKey(context)
            val cachedDecision = cacheService?.let { 
                val cached = it.get(cacheKey)
                if (cached != null) {
                    // Parse cached decision (simplified - in production use JSON)
                    logger.debug { "Cache hit for authorization: $cacheKey" }
                    return AuthzDecision(
                        allowed = cached.toBoolean(),
                        reason = "Cached decision",
                        cached = true,
                        evaluationTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                null
            }

            // Get applicable engines
            val applicableEngines = engines.filter { engine ->
                engine.supports(context)
            }

            if (applicableEngines.isEmpty()) {
                return AuthzDecision(
                    allowed = false,
                    reason = "No applicable authorization engines",
                    evaluationTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Evaluate with each engine
            val decisions = mutableListOf<AuthzDecision>()
            var explicitDeny = false

            for (engine in applicableEngines) {
                val decision = engine.authorize(context)
                decisions.add(decision)

                // Explicit deny takes precedence
                if (!decision.allowed && decision.reason?.contains("denies") == true) {
                    explicitDeny = true
                }
            }

            // Combine decisions: ALLOW if any allows (unless explicit deny), DENY otherwise
            val finalDecision = if (explicitDeny) {
                decisions.firstOrNull { !it.allowed && it.reason?.contains("denies") == true }
                    ?: decisions.first()
            } else {
                decisions.firstOrNull { it.allowed } ?: decisions.first()
            }

            val combinedDecision = AuthzDecision(
                allowed = finalDecision.allowed && !explicitDeny,
                reason = finalDecision.reason,
                appliedPolicies = decisions.flatMap { it.appliedPolicies }.distinct(),
                appliedRoles = decisions.flatMap { it.appliedRoles }.distinct(),
                appliedPermissions = decisions.flatMap { it.appliedPermissions }.distinct(),
                evaluationTimeMs = System.currentTimeMillis() - startTime
            )

            // Cache the decision (TTL: 60 seconds)
            cacheService?.set(cacheKey, combinedDecision.allowed.toString(), 60)

            logger.debug { 
                "Authorization decision: ${combinedDecision.allowed} for ${context.subjectId} on ${context.resourceId}"
            }

            return combinedDecision

        } catch (e: Exception) {
            logger.error(e) { "Error in authorization service" }
            return AuthzDecision(
                allowed = false,
                reason = "Authorization error: ${e.message}",
                evaluationTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Bulk authorization check for efficiency
     */
    suspend fun authorizeBulk(contexts: List<AuthorizationContext>): List<AuthzDecision> {
        return contexts.map { context ->
            authorize(context)
        }
    }

    /**
     * Generate cache key for authorization context
     */
    private fun generateCacheKey(context: AuthorizationContext): String {
        return "authz:${context.subjectId}:${context.resourceId}:${context.action}:${context.tenantId ?: "default"}"
    }
}

