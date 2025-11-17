package com.enterprise.framework.authz.services

import com.enterprise.framework.authz.engines.*
import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import io.vertx.core.Vertx
import java.util.UUID

class AuthorizationServiceTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var rbacEngine: RbacEngine
    lateinit var abacEngine: AbacEngine
    lateinit var rebacEngine: RebacEngine
    lateinit var authorizationService: AuthorizationService

    beforeSpec {
        vertx = Vertx.vertx()
        val roleRepository = RoleRepository(vertx)
        val permissionRepository = PermissionRepository(vertx)
        val policyRepository = PolicyRepository(vertx)
        val relationshipRepository = RelationshipRepository(vertx)

        rbacEngine = RbacEngine(roleRepository, permissionRepository)
        abacEngine = AbacEngine(policyRepository)
        rebacEngine = RebacEngine(relationshipRepository)

        authorizationService = AuthorizationService(vertx, rbacEngine, abacEngine, rebacEngine, null)
    }

    afterSpec {
        vertx.close()
    }

    describe("AuthorizationService") {

        describe("authorize") {

            it("should combine decisions from multiple engines") {
                // Setup: Create role for RBAC
                val roleId = UUID.randomUUID().toString()
                val role = Role(
                    id = roleId,
                    name = "Editor",
                    permissions = listOf("document:read"),
                    tenantId = null
                )
                val roleRepository = RoleRepository(vertx)
                roleRepository.save(role)

                val assignment = RoleAssignment(
                    id = UUID.randomUUID().toString(),
                    roleId = roleId,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    assignedBy = "admin"
                )
                roleRepository.assignRole(assignment)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = authorizationService.authorize(context)

                decision.allowed shouldBe true
                decision.appliedRoles.isEmpty() shouldBe false
            }

            it("should deny when all engines deny") {
                val context = AuthorizationContext(
                    subjectId = "user-no-access",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = authorizationService.authorize(context)

                decision.allowed shouldBe false
            }

            it("should allow when any engine allows") {
                // Setup: Create resource with owner (ReBAC will allow)
                val relationshipRepository = RelationshipRepository(vertx)
                val resource = Resource(
                    id = "doc456",
                    type = "document",
                    name = "Test Document",
                    ownerId = "user123",
                    tenantId = null
                )
                relationshipRepository.saveResource(resource)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = authorizationService.authorize(context)

                decision.allowed shouldBe true
            }

            it("should respect explicit DENY from ABAC") {
                // Setup: Create DENY policy
                val policyRepository = PolicyRepository(vertx)
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Deny Policy",
                    effect = PolicyEffect.DENY,
                    actions = listOf("read"),
                    resources = listOf("document:*"),
                    priority = 200
                )
                policyRepository.save(policy)

                // Setup: Also create role (RBAC would allow)
                val roleRepository = RoleRepository(vertx)
                val role = Role(
                    id = UUID.randomUUID().toString(),
                    name = "Editor",
                    permissions = listOf("document:read"),
                    tenantId = null
                )
                roleRepository.save(role)

                val assignment = RoleAssignment(
                    id = UUID.randomUUID().toString(),
                    roleId = role.id,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    assignedBy = "admin"
                )
                roleRepository.assignRole(assignment)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = authorizationService.authorize(context)

                // DENY should take precedence
                decision.allowed shouldBe false
            }

            it("should measure evaluation time") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = authorizationService.authorize(context)

                (decision.evaluationTimeMs >= 0L) shouldBe true
            }
        }

        describe("authorizeBulk") {

            it("should authorize multiple contexts") {
                val contexts = listOf(
                    AuthorizationContext(
                        subjectId = "user1",
                        subjectType = SubjectType.USER,
                        resourceId = "doc1",
                        resourceType = "document",
                        action = "read",
                        tenantId = null
                    ),
                    AuthorizationContext(
                        subjectId = "user2",
                        subjectType = SubjectType.USER,
                        resourceId = "doc2",
                        resourceType = "document",
                        action = "read",
                        tenantId = null
                    )
                )

                val decisions = authorizationService.authorizeBulk(contexts)

                decisions.size shouldBe 2
                decisions.forEach { decision ->
                    (decision.evaluationTimeMs >= 0L) shouldBe true
                }
            }
        }
    }
})

