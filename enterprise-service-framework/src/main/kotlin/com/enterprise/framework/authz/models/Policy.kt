package com.enterprise.framework.authz.models

import java.time.Instant

/**
 * Policy model for ABAC
 * Supports declarative policy definitions
 */
data class Policy(
    val id: String,
    val name: String,
    val description: String? = null,
    val effect: PolicyEffect,
    val actions: List<String>,
    val resources: List<String>, // Resource patterns
    val subjects: List<String>? = null, // Subject patterns (users, roles, groups)
    val conditions: Map<String, PolicyCondition>? = null, // Attribute-based conditions
    val priority: Int = 0, // Higher priority policies evaluated first
    val version: String = "1.0",
    val tenantId: String? = null,
    val enabled: Boolean = true,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class PolicyEffect {
    ALLOW, DENY
}

/**
 * Policy condition for attribute evaluation
 */
data class PolicyCondition(
    val attribute: String, // e.g., "user.department", "resource.classification"
    val operator: ConditionOperator,
    val value: Any
)

enum class ConditionOperator {
    EQUALS, NOT_EQUALS, IN, NOT_IN, GREATER_THAN, LESS_THAN,
    GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL, CONTAINS, STARTS_WITH, ENDS_WITH,
    REGEX, EXISTS, NOT_EXISTS
}

/**
 * Policy evaluation result
 */
data class PolicyEvaluationResult(
    val policyId: String,
    val effect: PolicyEffect,
    val matched: Boolean,
    val reason: String? = null,
    val evaluatedConditions: Map<String, Boolean> = emptyMap()
)

