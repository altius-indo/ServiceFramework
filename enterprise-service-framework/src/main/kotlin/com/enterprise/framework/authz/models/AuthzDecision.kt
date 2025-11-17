package com.enterprise.framework.authz.models

import java.time.Instant

/**
 * Authorization decision result
 */
data class AuthzDecision(
    val allowed: Boolean,
    val reason: String? = null,
    val appliedPolicies: List<String> = emptyList(),
    val appliedRoles: List<String> = emptyList(),
    val appliedPermissions: List<String> = emptyList(),
    val evaluationTimeMs: Long = 0,
    val cached: Boolean = false,
    val context: Map<String, Any> = emptyMap()
)

/**
 * Authorization context for evaluation
 */
data class AuthorizationContext(
    val subjectId: String,
    val subjectType: SubjectType = SubjectType.USER,
    val resourceId: String,
    val resourceType: String,
    val action: String,
    val tenantId: String? = null,
    val userAttributes: Map<String, Any> = emptyMap(),
    val resourceAttributes: Map<String, Any> = emptyMap(),
    val environmentalAttributes: Map<String, Any> = emptyMap(), // time, IP, device, etc.
    val requestMetadata: Map<String, Any> = emptyMap()
)

/**
 * Authorization audit log entry
 */
data class AuthorizationAuditLog(
    val id: String,
    val timestamp: Instant = Instant.now(),
    val subjectId: String,
    val resourceId: String,
    val action: String,
    val decision: AuthzDecision,
    val context: AuthorizationContext,
    val tenantId: String? = null,
    val requestId: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null
)

