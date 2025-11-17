package com.enterprise.framework.authz.engines

import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.PermissionRepository
import com.enterprise.framework.authz.repositories.RoleRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.mockk
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.await
import java.time.Instant
import java.util.UUID

class RbacEngineTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var roleRepository: RoleRepository
    lateinit var permissionRepository: PermissionRepository
    lateinit var rbacEngine: RbacEngine

    beforeSpec {
        vertx = Vertx.vertx()
        roleRepository = RoleRepository(vertx)
        permissionRepository = PermissionRepository(vertx)
        rbacEngine = RbacEngine(roleRepository, permissionRepository)
    }

    afterSpec {
        vertx.close()
    }

    describe("RbacEngine") {

        describe("authorize") {

            it("should allow access when user has role with matching permission") {
                // Setup: Create role with permission
                val roleId = UUID.randomUUID().toString()
                val role = Role(
                    id = roleId,
                    name = "Editor",
                    permissions = listOf("document:read", "document:write"),
                    tenantId = null
                )
                roleRepository.save(role)

                // Assign role to user
                val assignment = RoleAssignment(
                    id = UUID.randomUUID().toString(),
                    roleId = roleId,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    assignedBy = "admin"
                )
                roleRepository.assignRole(assignment)

                // Test: Authorize
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = rbacEngine.authorize(context)

                decision.allowed shouldBe true
                decision.appliedRoles shouldBe listOf(roleId)
            }

            it("should deny access when user has no roles") {
                val context = AuthorizationContext(
                    subjectId = "user-no-roles",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = rbacEngine.authorize(context)

                decision.allowed shouldBe false
                decision.reason shouldBe "No roles assigned to subject"
            }

            it("should deny access when role doesn't have required permission") {
                // Setup: Create role without required permission
                val roleId = UUID.randomUUID().toString()
                val role = Role(
                    id = roleId,
                    name = "Viewer",
                    permissions = listOf("document:read"), // No write permission
                    tenantId = null
                )
                roleRepository.save(role)

                val assignment = RoleAssignment(
                    id = UUID.randomUUID().toString(),
                    roleId = roleId,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    assignedBy = "admin"
                )
                roleRepository.assignRole(assignment)

                // Test: Try to write
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "write",
                    tenantId = null
                )

                val decision = rbacEngine.authorize(context)

                decision.allowed shouldBe false
            }

            it("should support hierarchical role inheritance") {
                // Setup: Create parent role
                val parentRoleId = UUID.randomUUID().toString()
                val parentRole = Role(
                    id = parentRoleId,
                    name = "Viewer",
                    permissions = listOf("document:read"),
                    tenantId = null
                )
                roleRepository.save(parentRole)

                // Create child role that inherits from parent
                val childRoleId = UUID.randomUUID().toString()
                val childRole = Role(
                    id = childRoleId,
                    name = "Editor",
                    permissions = listOf("document:write"),
                    parentRoleId = parentRoleId,
                    tenantId = null
                )
                roleRepository.save(childRole)

                // Assign child role to user
                val assignment = RoleAssignment(
                    id = UUID.randomUUID().toString(),
                    roleId = childRoleId,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    assignedBy = "admin"
                )
                roleRepository.assignRole(assignment)

                // Test: User should have both read (inherited) and write permissions
                val readContext = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val readDecision = rbacEngine.authorize(readContext)
                readDecision.allowed shouldBe true

                val writeContext = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "write",
                    tenantId = null
                )

                val writeDecision = rbacEngine.authorize(writeContext)
                writeDecision.allowed shouldBe true
            }

            it("should support wildcard permissions") {
                val roleId = UUID.randomUUID().toString()
                val role = Role(
                    id = roleId,
                    name = "Admin",
                    permissions = listOf("document:*"), // Wildcard permission
                    tenantId = null
                )
                roleRepository.save(role)

                val assignment = RoleAssignment(
                    id = UUID.randomUUID().toString(),
                    roleId = roleId,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    assignedBy = "admin"
                )
                roleRepository.assignRole(assignment)

                // Test: Should allow any action on document
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "delete",
                    tenantId = null
                )

                val decision = rbacEngine.authorize(context)
                decision.allowed shouldBe true
            }

            it("should respect tenant isolation") {
                val roleId = UUID.randomUUID().toString()
                val role = Role(
                    id = roleId,
                    name = "Editor",
                    permissions = listOf("document:read"),
                    tenantId = "tenant1"
                )
                roleRepository.save(role)

                val assignment = RoleAssignment(
                    id = UUID.randomUUID().toString(),
                    roleId = roleId,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    assignedBy = "admin",
                    tenantId = "tenant1"
                )
                roleRepository.assignRole(assignment)

                // Test: Should work for tenant1
                val context1 = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = "tenant1"
                )
                val decision1 = rbacEngine.authorize(context1)
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
                val decision2 = rbacEngine.authorize(context2)
                decision2.allowed shouldBe false
            }

            it("should handle expired role assignments") {
                val roleId = UUID.randomUUID().toString()
                val role = Role(
                    id = roleId,
                    name = "Editor",
                    permissions = listOf("document:read"),
                    tenantId = null
                )
                roleRepository.save(role)

                val assignment = RoleAssignment(
                    id = UUID.randomUUID().toString(),
                    roleId = roleId,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    assignedBy = "admin",
                    expiresAt = Instant.now().minusSeconds(3600) // Expired
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

                val decision = rbacEngine.authorize(context)
                decision.allowed shouldBe false
            }
        }

        describe("supports") {

            it("should support all contexts") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val supports = rbacEngine.supports(context)
                supports shouldBe true
            }
        }
    }
})

