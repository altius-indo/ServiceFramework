package com.enterprise.framework.authz.handlers

import com.enterprise.framework.authz.models.AuthorizationContext
import com.enterprise.framework.authz.models.SubjectType
import com.enterprise.framework.authz.services.AuthorizationService
import com.enterprise.framework.authz.services.AuditService
import com.enterprise.framework.authz.services.DynamicAuthorizationService
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.CoroutineScope
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Authorization handler for HTTP requests
 * Implements Policy Enforcement Point (PEP) pattern
 */
class AuthorizationHandler(
    private val authorizationService: AuthorizationService,
    private val auditService: AuditService,
    private val dynamicAuthzService: DynamicAuthorizationService,
    private val requiredAction: String,
    private val resourceType: String? = null
) : Handler<RoutingContext>, CoroutineScope {

    override fun handle(context: RoutingContext) {
        launch(context.vertx().dispatcher()) {
            try {
                // Extract subject from request (typically from JWT token)
                val subjectId = extractSubjectId(context)
                if (subjectId == null) {
                    context.response()
                        .setStatusCode(401)
                        .putHeader("Content-Type", "application/json")
                        .end("""{"error": "Unauthorized: Subject not found"}""")
                    return@launch
                }

                // Extract resource ID from path or request
                val resourceId = extractResourceId(context) ?: "default"
                val actualResourceType = resourceType ?: extractResourceType(context)

                // Extract tenant ID if present
                val tenantId = extractTenantId(context)

                // Build authorization context
                val authzContext = AuthorizationContext(
                    subjectId = subjectId,
                    subjectType = SubjectType.USER,
                    resourceId = resourceId,
                    resourceType = actualResourceType,
                    action = requiredAction,
                    tenantId = tenantId,
                    userAttributes = extractUserAttributes(context),
                    resourceAttributes = extractResourceAttributes(context),
                    environmentalAttributes = extractEnvironmentalAttributes(context),
                    requestMetadata = mapOf(
                        "path" to context.request().path(),
                        "method" to context.request().method().name()
                    )
                )

                // Check dynamic authorization conditions
                val contextAllowed = dynamicAuthzService.evaluateContextConditions(authzContext)
                if (!contextAllowed) {
                    val decision = com.enterprise.framework.authz.models.AuthzDecision(
                        allowed = false,
                        reason = "Context-based authorization failed"
                    )
                    auditService.logAuthorization(
                        authzContext,
                        decision,
                        context.request().getHeader("X-Request-ID"),
                        context.request().remoteAddress()?.host(),
                        context.request().getHeader("User-Agent")
                    )
                    context.response()
                        .setStatusCode(403)
                        .putHeader("Content-Type", "application/json")
                        .end("""{"error": "Forbidden: Context-based authorization failed"}""")
                    return@launch
                }

                // Perform authorization check
                val decision = authorizationService.authorize(authzContext)

                // Audit the decision
                auditService.logAuthorization(
                    authzContext,
                    decision,
                    context.request().getHeader("X-Request-ID"),
                    context.request().remoteAddress()?.host(),
                    context.request().getHeader("User-Agent")
                )

                if (decision.allowed) {
                    // Store decision in context for downstream handlers
                    context.put("authzDecision", decision)
                    context.put("authzContext", authzContext)
                    context.next()
                } else {
                    context.response()
                        .setStatusCode(403)
                        .putHeader("Content-Type", "application/json")
                        .end("""{"error": "Forbidden", "reason": "${decision.reason}"}""")
                }

            } catch (e: Exception) {
                logger.error(e) { "Error in authorization handler" }
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end("""{"error": "Internal server error"}""")
            }
        }
    }

    private fun extractSubjectId(context: RoutingContext): String? {
        // Try to get from JWT token (set by authentication handler)
        val user = context.user()
        if (user != null) {
            return user.principal().getString("sub") ?: user.principal().getString("userId")
        }
        
        // Fallback to header
        return context.request().getHeader("X-User-ID")
    }

    private fun extractResourceId(context: RoutingContext): String? {
        // Try path parameter
        val resourceId = context.pathParam("resourceId") 
            ?: context.pathParam("id")
            ?: context.pathParam("resource")
        
        return resourceId
    }

    private fun extractResourceType(context: RoutingContext): String {
        // Infer from path
        val path = context.request().path()
        val segments = path.split("/").filter { it.isNotBlank() }
        return segments.firstOrNull() ?: "resource"
    }

    private fun extractTenantId(context: RoutingContext): String? {
        return context.request().getHeader("X-Tenant-ID")
            ?: context.user()?.principal()?.getString("tenantId")
    }

    private fun extractUserAttributes(context: RoutingContext): Map<String, Any> {
        val attributes = mutableMapOf<String, Any>()
        
        context.user()?.principal()?.let { principal ->
            principal.fieldNames().forEach { key ->
                attributes[key] = principal.getValue(key)
            }
        }
        
        return attributes
    }

    private fun extractResourceAttributes(context: RoutingContext): Map<String, Any> {
        // In production, fetch resource attributes from database
        return emptyMap()
    }

    private fun extractEnvironmentalAttributes(context: RoutingContext): Map<String, Any> {
        val attributes = mutableMapOf<String, Any>()
        
        // IP address
        context.request().remoteAddress()?.host()?.let {
            attributes["ipAddress"] = it
        }
        
        // User agent
        context.request().getHeader("User-Agent")?.let {
            attributes["userAgent"] = it
        }
        
        // Time
        attributes["timestamp"] = System.currentTimeMillis()
        attributes["currentHour"] = java.time.Instant.now()
            .atZone(java.time.ZoneId.systemDefault()).hour
        
        // Extract from headers
        context.request().getHeader("X-Device-ID")?.let {
            attributes["deviceId"] = it
        }
        context.request().getHeader("X-Is-Managed-Device")?.let {
            attributes["isManagedDevice"] = it.toBoolean()
        }
        context.request().getHeader("X-Is-VPN")?.let {
            attributes["isVPN"] = it.toBoolean()
        }
        
        return attributes
    }
}

