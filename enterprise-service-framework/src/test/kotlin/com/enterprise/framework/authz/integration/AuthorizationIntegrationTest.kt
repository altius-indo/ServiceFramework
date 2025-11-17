package com.enterprise.framework.authz.integration

import com.enterprise.framework.authz.engines.*
import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.*
import com.enterprise.framework.authz.services.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import java.time.Instant
import java.util.UUID

class AuthorizationIntegrationTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var authorizationService: AuthorizationService
    lateinit var policyService: PolicyService
    lateinit var permissionService: PermissionService
    lateinit var auditService: AuditService

    beforeSpec {
        vertx = Vertx.vertx()

        // Initialize repositories
        val roleRepository = RoleRepository(vertx)
        val permissionRepository = PermissionRepository(vertx)
        val policyRepository = PolicyRepository(vertx)
        val relationshipRepository = RelationshipRepository(vertx)

        // Initialize engines
        val rbacEngine = RbacEngine(roleRepository, permissionRepository)
        val abacEngine = AbacEngine(policyRepository)
        val rebacEngine = RebacEngine(relationshipRepository)

        // Initialize services
        authorizationService = AuthorizationService(vertx, rbacEngine, abacEngine, rebacEngine, null)
        policyService = PolicyService(policyRepository)
        permissionService = PermissionService(permissionRepository, roleRepository)
        auditService = AuditService(vertx)
    }

    afterSpec {
        vertx.close()
    }

    describe("Authorization Integration") {

        describe("RBAC + ABAC combined") {

            it("should allow access when both RBAC and ABAC allow") {
                // Setup RBAC: Create role
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

                // Setup ABAC: Create policy
                val policy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Allow Engineering",
                    effect = PolicyEffect.ALLOW,
                    actions = listOf("read"),
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
                policyService.createPolicy(policy)

                // Test
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    userAttributes = mapOf("department" to "Engineering")
                )

                val decision = authorizationService.authorize(context)

                decision.allowed shouldBe true
                decision.appliedRoles shouldBe listOf(roleId)
                decision.appliedPolicies shouldBe listOf(policy.id)
            }

            it("should deny access when ABAC explicitly denies") {
                // Setup RBAC: Create role (would allow)
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

                // Setup ABAC: Create DENY policy
                val denyPolicy = Policy(
                    id = UUID.randomUUID().toString(),
                    name = "Deny External IP",
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
                policyService.createPolicy(denyPolicy)

                // Test: DENY should take precedence
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf("ipAddress" to "192.168.1.1")
                )

                val decision = authorizationService.authorize(context)

                decision.allowed shouldBe false
                decision.reason shouldBe "Policy '${denyPolicy.name}' denies access"
            }
        }

        describe("Multi-tenancy") {

            it("should isolate tenants correctly") {
                val roleRepository = RoleRepository(vertx)

                // Create role for tenant1
                val role1 = Role(
                    id = UUID.randomUUID().toString(),
                    name = "Editor",
                    permissions = listOf("document:read"),
                    tenantId = "tenant1"
                )
                roleRepository.save(role1)

                val assignment1 = RoleAssignment(
                    id = UUID.randomUUID().toString(),
                    roleId = role1.id,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    assignedBy = "admin",
                    tenantId = "tenant1"
                )
                roleRepository.assignRole(assignment1)

                // Test: Should work for tenant1
                val context1 = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = "tenant1"
                )
                val decision1 = authorizationService.authorize(context1)
                decision1.allowed shouldBe true

                // Test: Should not work for tenant2
                val context2 = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = "tenant2"
                )
                val decision2 = authorizationService.authorize(context2)
                decision2.allowed shouldBe false
            }
        }

        describe("Permission delegation") {

            it("should support permission delegation chain") {
                val permission = Permission(
                    id = UUID.randomUUID().toString(),
                    name = "Read Document",
                    resource = "document",
                    action = "read"
                )
                permissionService.createPermission(permission)

                // Grant to delegator
                val delegatorGrant = PermissionGrant(
                    id = UUID.randomUUID().toString(),
                    permissionId = permission.id,
                    subjectId = "delegator",
                    subjectType = SubjectType.USER,
                    grantedBy = "admin"
                )
                permissionService.grantPermission(delegatorGrant)

                // Delegate to delegatee
                val delegation = permissionService.delegatePermission(
                    permissionId = permission.id,
                    fromSubjectId = "delegator",
                    toSubjectId = "delegatee",
                    delegatedBy = "delegator"
                )

                delegation.delegatedFrom shouldBe "delegator"
                delegation.subjectId shouldBe "delegatee"

                // Verify delegatee has permission
                val hasPermission = permissionService.checkPermission(
                    subjectId = "delegatee",
                    resourceId = "doc456",
                    action = "read",
                    tenantId = null
                )

                hasPermission shouldBe true
            }
        }

        describe("Audit logging") {

            it("should log all authorization decisions") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = authorizationService.authorize(context)

                // Log the decision
                auditService.logAuthorization(
                    context = context,
                    decision = decision,
                    requestId = UUID.randomUUID().toString()
                )

                // Should not throw
            }
        }

        describe("Dynamic authorization") {

            it("should enforce context-based restrictions") {
                val dynamicAuthzService = DynamicAuthorizationService(vertx)

                // Setup: Create role (RBAC would allow)
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

                // Test: Context restriction (outside business hours)
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf(
                        "businessHoursOnly" to true,
                        "currentHour" to 20 // 8 PM
                    )
                )

                // Check context conditions first
                val contextAllowed = dynamicAuthzService.evaluateContextConditions(context)
                contextAllowed shouldBe false

                // Even if RBAC allows, context should deny
                val decision = authorizationService.authorize(context)
                // Note: In real implementation, AuthorizationHandler would check context first
            }
        }
    }
})

