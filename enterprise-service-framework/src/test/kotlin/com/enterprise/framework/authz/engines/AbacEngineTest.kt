package com.enterprise.framework.authz.engines

import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.PolicyRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.vertx.core.Vertx
import java.time.Instant
import java.util.UUID

class AbacEngineTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var policyRepository: PolicyRepository
    lateinit var abacEngine: AbacEngine

    beforeSpec {
        vertx = Vertx.vertx()
        policyRepository = PolicyRepository(vertx)
        abacEngine = AbacEngine(policyRepository)
    }

    afterSpec {
        vertx.close()
    }

    describe("AbacEngine") {

        describe("authorize") {

            it("should allow access when policy matches and effect is ALLOW") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Allow Engineering Access",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read", "write"),
                    resources = listOf("document:*"),
                    conditions = mapOf(
                        "department" to PolicyCondition(
                            attribute = "user.department",
                            operator = ConditionOperator.EQUALS,
                            value = "Engineering"
                        )
                    ),
                    priority = 100
                )
                policyRepository.save(policy)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    userAttributes = mapOf("department" to "Engineering")
                )

                val decision = abacEngine.authorize(context)

                decision.allowed shouldBe true
                decision.appliedPolicies shouldBe listOf(policy.id)
            }

            it("should deny access when policy matches and effect is DENY") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Deny External Access",
                    effect = PolicyEffect.DENY,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    conditions = mapOf(
                        "ipAddress" to PolicyCondition(
                            attribute = "env.ipAddress",
                            operator = ConditionOperator.STARTS_WITH,
                            value = "192.168"
                        )
                    ),
                    priority = 200 // Higher priority
                )
                policyRepository.save(policy)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf("ipAddress" to "192.168.1.1")
                )

                val decision = abacEngine.authorize(context)

                decision.allowed shouldBe false
                decision.reason shouldBe "Policy '${policy.name}' denies access"
            }

            it("should deny access when no policy matches") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = abacEngine.authorize(context)

                decision.allowed shouldBe false
                decision.reason shouldBe "No applicable policies found"
            }

            it("should evaluate multiple conditions with AND logic") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Complex Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    conditions = mapOf(
                        "department" to PolicyCondition(
                            attribute = "user.department",
                            operator = ConditionOperator.EQUALS,
                            value = "Engineering"
                        ),
                        "clearance" to PolicyCondition(
                            attribute = "user.clearance",
                            operator = ConditionOperator.GREATER_THAN_OR_EQUAL,
                            value = 5
                        )
                    ),
                    priority = 100
                )
                policyRepository.save(policy)

                // Test: Both conditions met
                val context1 = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    userAttributes = mapOf(
                        "department" to "Engineering",
                        "clearance" to 5
                    )
                )
                val decision1 = abacEngine.authorize(context1)
                decision1.allowed shouldBe true

                // Test: One condition not met
                val context2 = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    userAttributes = mapOf(
                        "department" to "Engineering",
                        "clearance" to 3 // Too low
                    )
                )
                val decision2 = abacEngine.authorize(context2)
                decision2.allowed shouldBe false
            }

            it("should respect policy priority (DENY overrides ALLOW)") {
                val allowPolicy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Allow Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    priority = 100
                )
                policyRepository.save(allowPolicy)

                val denyPolicy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Deny Policy",
                    effect = PolicyEffect.DENY,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    priority = 200 // Higher priority
                )
                policyRepository.save(denyPolicy)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = abacEngine.authorize(context)

                decision.allowed shouldBe false
                decision.appliedPolicies shouldBe listOf(denyPolicy.id, allowPolicy.id)
            }

            it("should support time-based conditions") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Business Hours Only",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    conditions = mapOf(
                        "businessHours" to PolicyCondition(
                            attribute = "env.currentHour",
                            operator = ConditionOperator.GREATER_THAN_OR_EQUAL,
                            value = 9
                        )
                    ),
                    priority = 100
                )
                policyRepository.save(policy)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf("currentHour" to 10)
                )

                val decision = abacEngine.authorize(context)
                decision.allowed shouldBe true
            }

            it("should support IN operator") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Department List",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    conditions = mapOf(
                        "department" to PolicyCondition(
                            attribute = "user.department",
                            operator = ConditionOperator.IN,
                            value = listOf("Engineering", "Product", "Design")
                        )
                    ),
                    priority = 100
                )
                policyRepository.save(policy)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    userAttributes = mapOf("department" to "Engineering")
                )

                val decision = abacEngine.authorize(context)
                decision.allowed shouldBe true
            }

            it("should support resource pattern matching") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Document Pattern",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*", "folder:*"),
                    priority = 100
                )
                policyRepository.save(policy)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = abacEngine.authorize(context)
                decision.allowed shouldBe true
            }

            it("should ignore disabled policies") {
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Disabled Policy",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    enabled = false,
                    priority = 100
                )
                policyRepository.save(policy)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = abacEngine.authorize(context)
                decision.allowed shouldBe false
            }
        }

        describe("supports") {

            it("should support contexts with attributes") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    userAttributes = mapOf("department" to "Engineering")
                )

                val supports = abacEngine.supports(context)
                supports shouldBe true
            }

            it("should support contexts with environmental attributes") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf("ipAddress" to "192.168.1.1")
                )

                val supports = abacEngine.supports(context)
                supports shouldBe true
            }
        }
    }
})

