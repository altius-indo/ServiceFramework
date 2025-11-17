package com.enterprise.framework.authz.services

import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.PolicyRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vertx.core.Vertx
import java.util.UUID

class PolicyServiceTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var policyRepository: PolicyRepository
    lateinit var policyService: PolicyService

    beforeSpec {
        vertx = Vertx.vertx()
        policyRepository = PolicyRepository(vertx)
        policyService = PolicyService(policyRepository)
    }

    afterSpec {
        vertx.close()
    }

    describe("PolicyService") {

        describe("createPolicy") {

            it("should create a valid policy") {
                val policy = Policy(
                    id = "",
                    name = "Test Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read", "write"),
                    resources = listOf("document:*")
                )

                val created = policyService.createPolicy(policy)

                created.id shouldNotBe ""
                created.name shouldBe "Test Policy"
                created.version shouldBe "1.0"
                created.createdAt shouldNotBe null
            }

            it("should throw exception for invalid policy") {
                val invalidPolicy = Policy(
                    id = "",
                    name = "", // Empty name
                    effect = PolicyEffect.ALLOW,
                    actions = emptyList(), // Empty actions
                    resources = listOf("document:*")
                )

                shouldThrow<IllegalArgumentException> {
                    policyService.createPolicy(invalidPolicy)
                }
            }

            it("should generate UUID if id is empty") {
                val policy = Policy(
                    id = "",
                    name = "Test Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*")
                )

                val created = policyService.createPolicy(policy)

                created.id shouldNotBe ""
                UUID.fromString(created.id) // Should be valid UUID
            }
        }

        describe("updatePolicy") {

            it("should update an existing policy") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Original Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*")
                )
                policyRepository.save(policy)

                val updates = policy.copy(
                    name = "Updated Policy",
                    actions = listOf("read", "write")
                )

                val updated = policyService.updatePolicy(policy.id, updates)

                updated.name shouldBe "Updated Policy"
                updated.actions shouldBe listOf("read", "write")
                updated.version shouldBe "1.1"
            }

            it("should throw exception when policy not found") {
                val updates = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Non-existent",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*")
                )

                shouldThrow<IllegalArgumentException> {
                    policyService.updatePolicy(updates.id, updates)
                }
            }
        }

        describe("getPolicy") {

            it("should retrieve an existing policy") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Test Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*")
                )
                policyRepository.save(policy)

                val retrieved = policyService.getPolicy(policy.id)

                retrieved shouldNotBe null
                retrieved!!.id shouldBe policy.id
                retrieved.name shouldBe "Test Policy"
            }

            it("should return null for non-existent policy") {
                val retrieved = policyService.getPolicy(UUID.randomUUID().toString())
                retrieved shouldBe null
            }
        }

        describe("enablePolicy and disablePolicy") {

            it("should enable a disabled policy") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Test Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    enabled = false
                )
                policyRepository.save(policy)

                policyService.enablePolicy(policy.id)

                val updated = policyRepository.findById(policy.id)
                updated!!.enabled shouldBe true
            }

            it("should disable an enabled policy") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Test Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    enabled = true
                )
                policyRepository.save(policy)

                policyService.disablePolicy(policy.id)

                val updated = policyRepository.findById(policy.id)
                updated!!.enabled shouldBe false
            }
        }

        describe("deletePolicy") {

            it("should delete an existing policy") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Test Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*")
                )
                policyRepository.save(policy)

                policyService.deletePolicy(policy.id)

                val deleted = policyRepository.findById(policy.id)
                deleted shouldBe null
            }
        }
    }
})

