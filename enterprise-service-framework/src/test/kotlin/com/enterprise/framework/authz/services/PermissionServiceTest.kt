package com.enterprise.framework.authz.services

import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.PermissionRepository
import com.enterprise.framework.authz.repositories.RoleRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vertx.core.Vertx
import java.time.Instant
import java.util.UUID

class PermissionServiceTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var permissionRepository: PermissionRepository
    lateinit var roleRepository: RoleRepository
    lateinit var permissionService: PermissionService

    beforeSpec {
        vertx = Vertx.vertx()
        permissionRepository = PermissionRepository(vertx)
        roleRepository = RoleRepository(vertx)
        permissionService = PermissionService(permissionRepository, roleRepository)
    }

    afterSpec {
        vertx.close()
    }

    describe("PermissionService") {

        describe("createPermission") {

            it("should create a valid permission") {
                val permission = Permission(
                    id = "",
                    name = "Read Document",
                    resource = "document",
                    action = "read"
                )

                val created = permissionService.createPermission(permission)

                created.id shouldNotBe ""
                created.name shouldBe "Read Document"
                created.resource shouldBe "document"
                created.action shouldBe "read"
            }

            it("should generate UUID if id is empty") {
                val permission = Permission(
                    id = "",
                    name = "Test Permission",
                    resource = "document",
                    action = "read"
                )

                val created = permissionService.createPermission(permission)

                created.id shouldNotBe ""
                UUID.fromString(created.id) // Should be valid UUID
            }
        }

        describe("grantPermission") {

            it("should grant permission to a user") {
                val permission = Permission(
                    id = UUID.randomUUID().toString(),
                    name = "Read Document",
                    resource = "document",
                    action = "read"
                )
                permissionRepository.save(permission)

                val grant = PermissionGrant(
                    id = "",
                    permissionId = permission.id,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    grantedBy = "admin"
                )

                val created = permissionService.grantPermission(grant)

                created.id shouldNotBe ""
                created.grantedAt shouldNotBe null
            }
        }

        describe("delegatePermission") {

            it("should delegate permission when delegator has permission") {
                val permission = Permission(
                    id = UUID.randomUUID().toString(),
                    name = "Read Document",
                    resource = "document",
                    action = "read"
                )
                permissionRepository.save(permission)

                // Grant permission to delegator
                val delegatorGrant = PermissionGrant(
                    id = UUID.randomUUID().toString(),
                    permissionId = permission.id,
                    subjectId = "delegator",
                    subjectType = SubjectType.USER,
                    grantedBy = "admin"
                )
                permissionRepository.grantPermission(delegatorGrant)

                // Delegate
                val delegation = permissionService.delegatePermission(
                    permissionId = permission.id,
                    fromSubjectId = "delegator",
                    toSubjectId = "delegatee",
                    delegatedBy = "delegator"
                )

                delegation.delegatedFrom shouldBe "delegator"
                delegation.subjectId shouldBe "delegatee"
            }

            it("should throw exception when delegator doesn't have permission") {
                val permission = Permission(
                    id = UUID.randomUUID().toString(),
                    name = "Read Document",
                    resource = "document",
                    action = "read"
                )
                permissionRepository.save(permission)

                shouldThrow<IllegalArgumentException> {
                    permissionService.delegatePermission(
                        permissionId = permission.id,
                        fromSubjectId = "delegator-without-permission",
                        toSubjectId = "delegatee",
                        delegatedBy = "admin"
                    )
                }
            }
        }

        describe("checkPermission") {

            it("should return true when user has permission") {
                val permission = Permission(
                    id = UUID.randomUUID().toString(),
                    name = "Read Document",
                    resource = "document",
                    action = "read"
                )
                permissionRepository.save(permission)

                val grant = PermissionGrant(
                    id = UUID.randomUUID().toString(),
                    permissionId = permission.id,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    grantedBy = "admin"
                )
                permissionRepository.grantPermission(grant)

                val hasPermission = permissionService.checkPermission(
                    subjectId = "user123",
                    resourceId = "doc456",
                    action = "read",
                    tenantId = null
                )

                hasPermission shouldBe true
            }

            it("should return false when user doesn't have permission") {
                val hasPermission = permissionService.checkPermission(
                    subjectId = "user-no-permission",
                    resourceId = "doc456",
                    action = "read",
                    tenantId = null
                )

                hasPermission shouldBe false
            }

            it("should support wildcard resource patterns") {
                val permission = Permission(
                    id = UUID.randomUUID().toString(),
                    name = "Read Any Document",
                    resource = "document:*",
                    action = "read"
                )
                permissionRepository.save(permission)

                val grant = PermissionGrant(
                    id = UUID.randomUUID().toString(),
                    permissionId = permission.id,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    grantedBy = "admin"
                )
                permissionRepository.grantPermission(grant)

                val hasPermission = permissionService.checkPermission(
                    subjectId = "user123",
                    resourceId = "doc456",
                    action = "read",
                    tenantId = null
                )

                hasPermission shouldBe true
            }
        }

        describe("revokePermission") {

            it("should revoke a permission grant") {
                val permission = Permission(
                    id = UUID.randomUUID().toString(),
                    name = "Read Document",
                    resource = "document",
                    action = "read"
                )
                permissionRepository.save(permission)

                val grant = PermissionGrant(
                    id = UUID.randomUUID().toString(),
                    permissionId = permission.id,
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    grantedBy = "admin"
                )
                permissionRepository.grantPermission(grant)

                permissionService.revokePermission(grant.id)

                val grants = permissionRepository.findBySubject("user123", SubjectType.USER, null)
                grants.find { it.id == grant.id } shouldBe null
            }
        }
    }
})

