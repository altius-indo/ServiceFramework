package com.enterprise.framework.authz.services

import com.enterprise.framework.authz.models.Policy
import com.enterprise.framework.authz.repositories.PolicyRepository
import io.vertx.core.Vertx
import mu.KotlinLogging
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Policy management service
 * Handles policy CRUD operations and validation
 */
class PolicyService(
    private val policyRepository: PolicyRepository
) {

    suspend fun createPolicy(policy: Policy): Policy {
        val policyWithDefaults = policy.copy(
            id = policy.id.ifEmpty { UUID.randomUUID().toString() },
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        validatePolicy(policyWithDefaults)
        policyRepository.save(policyWithDefaults)
        logger.info { "Created policy: ${policyWithDefaults.id}" }
        return policyWithDefaults
    }

    suspend fun updatePolicy(policyId: String, updates: Policy): Policy {
        val existing = policyRepository.findById(policyId)
            ?: throw IllegalArgumentException("Policy not found: $policyId")
        
        val updated = existing.copy(
            name = updates.name.ifEmpty { existing.name },
            description = updates.description ?: existing.description,
            effect = updates.effect,
            actions = updates.actions.ifEmpty { existing.actions },
            resources = updates.resources.ifEmpty { existing.resources },
            subjects = updates.subjects ?: existing.subjects,
            conditions = updates.conditions ?: existing.conditions,
            priority = updates.priority,
            version = incrementVersion(existing.version),
            enabled = updates.enabled,
            updatedAt = Instant.now()
        )
        
        validatePolicy(updated)
        policyRepository.save(updated)
        logger.info { "Updated policy: $policyId" }
        return updated
    }

    suspend fun getPolicy(policyId: String): Policy? {
        return policyRepository.findById(policyId)
    }

    suspend fun listPolicies(tenantId: String?): List<Policy> {
        return if (tenantId != null) {
            policyRepository.findByTenant(tenantId)
        } else {
            emptyList() // In production, implement findAll
        }
    }

    suspend fun deletePolicy(policyId: String) {
        policyRepository.delete(policyId)
        logger.info { "Deleted policy: $policyId" }
    }

    suspend fun enablePolicy(policyId: String) {
        val policy = policyRepository.findById(policyId)
            ?: throw IllegalArgumentException("Policy not found: $policyId")
        
        policyRepository.save(policy.copy(enabled = true, updatedAt = Instant.now()))
    }

    suspend fun disablePolicy(policyId: String) {
        val policy = policyRepository.findById(policyId)
            ?: throw IllegalArgumentException("Policy not found: $policyId")
        
        policyRepository.save(policy.copy(enabled = false, updatedAt = Instant.now()))
    }

    private fun validatePolicy(policy: Policy) {
        require(policy.name.isNotBlank()) { "Policy name cannot be blank" }
        require(policy.actions.isNotEmpty()) { "Policy must have at least one action" }
        require(policy.resources.isNotEmpty()) { "Policy must have at least one resource" }
    }

    private fun incrementVersion(version: String): String {
        val parts = version.split(".")
        if (parts.size == 2) {
            val major = parts[0].toIntOrNull() ?: 1
            val minor = parts[1].toIntOrNull() ?: 0
            return "${major}.${minor + 1}"
        }
        return "1.0"
    }
}

