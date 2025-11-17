package com.enterprise.framework.authz.engines

import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.PolicyRepository
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Attribute-Based Access Control Engine
 * Supports policy-based access decisions using attributes
 */
class AbacEngine(
    private val policyRepository: PolicyRepository
) : AuthzEngine {

    override suspend fun supports(context: AuthorizationContext): Boolean {
        // ABAC supports contexts with attributes
        return context.userAttributes.isNotEmpty() || 
               context.resourceAttributes.isNotEmpty() || 
               context.environmentalAttributes.isNotEmpty()
    }

    override suspend fun authorize(context: AuthorizationContext): AuthzDecision {
        val startTime = System.currentTimeMillis()
        
        try {
            // Get all applicable policies
            val policies = policyRepository.findApplicablePolicies(
                context.resourceType,
                context.action,
                context.tenantId
            ).filter { it.enabled }
                .sortedByDescending { it.priority }

            if (policies.isEmpty()) {
                return AuthzDecision(
                    allowed = false,
                    reason = "No applicable policies found",
                    evaluationTimeMs = System.currentTimeMillis() - startTime
                )
            }

            val appliedPolicies = mutableListOf<String>()
            var denyDecision: PolicyEvaluationResult? = null

            // Evaluate policies in priority order
            for (policy in policies) {
                val evaluation = evaluatePolicy(policy, context)
                
                if (evaluation.matched) {
                    appliedPolicies.add(policy.id)
                    
                    // DENY policies take precedence
                    if (policy.effect == PolicyEffect.DENY) {
                        denyDecision = evaluation
                        break
                    }
                    
                    // First ALLOW policy that matches grants access
                    if (policy.effect == PolicyEffect.ALLOW) {
                        return AuthzDecision(
                            allowed = true,
                            reason = "Policy '${policy.name}' allows access",
                            appliedPolicies = appliedPolicies,
                            evaluationTimeMs = System.currentTimeMillis() - startTime
                        )
                    }
                }
            }

            // If we found a DENY policy, deny access
            if (denyDecision != null) {
                return AuthzDecision(
                    allowed = false,
                    reason = "Policy '${denyDecision.policyId}' denies access: ${denyDecision.reason}",
                    appliedPolicies = appliedPolicies,
                    evaluationTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Default deny if no policy matches
            return AuthzDecision(
                allowed = false,
                reason = "No matching policy allows access",
                appliedPolicies = appliedPolicies,
                evaluationTimeMs = System.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            logger.error(e) { "Error in ABAC authorization" }
            return AuthzDecision(
                allowed = false,
                reason = "Authorization error: ${e.message}",
                evaluationTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Evaluate a policy against the authorization context
     */
    private fun evaluatePolicy(policy: Policy, context: AuthorizationContext): PolicyEvaluationResult {
        // Check if subject matches
        if (policy.subjects != null && policy.subjects.isNotEmpty()) {
            val subjectMatches = policy.subjects.any { pattern ->
                matchesPattern(context.subjectId, pattern)
            }
            if (!subjectMatches) {
                return PolicyEvaluationResult(
                    policyId = policy.id,
                    effect = policy.effect,
                    matched = false,
                    reason = "Subject does not match policy subjects"
                )
            }
        }

        // Check if resource matches
        val resourceMatches = policy.resources.any { pattern ->
            matchesResourcePattern(context.resourceId, context.resourceType, pattern)
        }
        if (!resourceMatches) {
            return PolicyEvaluationResult(
                policyId = policy.id,
                effect = policy.effect,
                matched = false,
                reason = "Resource does not match policy resources"
            )
        }

        // Check if action matches
        val actionMatches = policy.actions.any { pattern ->
            matchesPattern(context.action, pattern)
        }
        if (!actionMatches) {
            return PolicyEvaluationResult(
                policyId = policy.id,
                effect = policy.effect,
                matched = false,
                reason = "Action does not match policy actions"
            )
        }

        // Evaluate conditions
        val conditionResults = mutableMapOf<String, Boolean>()
        if (policy.conditions != null && policy.conditions.isNotEmpty()) {
            for ((key, condition) in policy.conditions) {
                val result = evaluateCondition(condition, context)
                conditionResults[key] = result
                if (!result) {
                    return PolicyEvaluationResult(
                        policyId = policy.id,
                        effect = policy.effect,
                        matched = false,
                        reason = "Condition '$key' not satisfied",
                        evaluatedConditions = conditionResults
                    )
                }
            }
        }

        return PolicyEvaluationResult(
            policyId = policy.id,
            effect = policy.effect,
            matched = true,
            evaluatedConditions = conditionResults
        )
    }

    /**
     * Evaluate a condition against the context
     */
    private fun evaluateCondition(condition: PolicyCondition, context: AuthorizationContext): Boolean {
        val attributeValue = getAttributeValue(condition.attribute, context)
        
        return when (condition.operator) {
            ConditionOperator.EQUALS -> attributeValue == condition.value
            ConditionOperator.NOT_EQUALS -> attributeValue != condition.value
            ConditionOperator.IN -> {
                val list = condition.value as? List<*>
                list?.contains(attributeValue) ?: false
            }
            ConditionOperator.NOT_IN -> {
                val list = condition.value as? List<*>
                val contains = list?.contains(attributeValue) ?: false
                !contains
            }
            ConditionOperator.GREATER_THAN -> compareValues(attributeValue, condition.value) > 0
            ConditionOperator.LESS_THAN -> compareValues(attributeValue, condition.value) < 0
            ConditionOperator.GREATER_THAN_OR_EQUAL -> compareValues(attributeValue, condition.value) >= 0
            ConditionOperator.LESS_THAN_OR_EQUAL -> compareValues(attributeValue, condition.value) <= 0
            ConditionOperator.CONTAINS -> {
                val str = attributeValue?.toString() ?: ""
                str.contains(condition.value.toString())
            }
            ConditionOperator.STARTS_WITH -> {
                val str = attributeValue?.toString() ?: ""
                str.startsWith(condition.value.toString())
            }
            ConditionOperator.ENDS_WITH -> {
                val str = attributeValue?.toString() ?: ""
                str.endsWith(condition.value.toString())
            }
            ConditionOperator.REGEX -> {
                val str = attributeValue?.toString() ?: ""
                Regex(condition.value.toString()).matches(str)
            }
            ConditionOperator.EXISTS -> attributeValue != null
            ConditionOperator.NOT_EXISTS -> attributeValue == null
        }
    }

    /**
     * Get attribute value from context
     * Supports nested attributes like "user.department", "resource.classification"
     */
    private fun getAttributeValue(attributePath: String, context: AuthorizationContext): Any? {
        val parts = attributePath.split(".")
        if (parts.isEmpty()) return null

        val source = when (parts[0]) {
            "user", "subject" -> context.userAttributes
            "resource" -> context.resourceAttributes
            "env", "environment" -> context.environmentalAttributes
            "time" -> mapOf("current" to Instant.now().toEpochMilli())
            else -> emptyMap<String, Any>()
        }

        if (parts.size == 1) {
            return source[parts[0]]
        }

        var current: Any? = source[parts[1]]
        for (i in 2 until parts.size) {
            current = (current as? Map<*, *>)?.get(parts[i])
            if (current == null) break
        }

        return current
    }

    /**
     * Compare two values for ordering
     */
    private fun compareValues(a: Any?, b: Any?): Int {
        if (a == null || b == null) return 0
        return when {
            a is Number && b is Number -> a.toDouble().compareTo(b.toDouble())
            a is Comparable<*> && b is Comparable<*> -> {
                @Suppress("UNCHECKED_CAST")
                (a as Comparable<Any>).compareTo(b as Any)
            }
            else -> a.toString().compareTo(b.toString())
        }
    }

    /**
     * Check if a value matches a pattern (supports wildcards)
     */
    private fun matchesPattern(value: String, pattern: String): Boolean {
        if (pattern == "*") return true
        if (pattern.contains("*")) {
            val regex = pattern.replace("*", ".*").toRegex()
            return regex.matches(value)
        }
        return value == pattern
    }

    /**
     * Check if a resource matches a resource pattern
     */
    private fun matchesResourcePattern(resourceId: String, resourceType: String, pattern: String): Boolean {
        if (pattern == "*") return true
        if (pattern.contains(":")) {
            val parts = pattern.split(":")
            if (parts.size == 2) {
                val (typePattern, idPattern) = parts
                return matchesPattern(resourceType, typePattern) && matchesPattern(resourceId, idPattern)
            }
        }
        return matchesPattern("$resourceType:$resourceId", pattern)
    }
}

