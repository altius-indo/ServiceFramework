package com.enterprise.framework.authz.services

import com.enterprise.framework.authz.models.AuthorizationContext
import com.enterprise.framework.authz.models.SubjectType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vertx.core.Vertx

class DynamicAuthorizationServiceTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var dynamicAuthzService: DynamicAuthorizationService

    beforeSpec {
        vertx = Vertx.vertx()
        dynamicAuthzService = DynamicAuthorizationService(vertx)
    }

    afterSpec {
        vertx.close()
    }

    describe("DynamicAuthorizationService") {

        describe("evaluateContextConditions") {

            it("should allow access during business hours") {
                val currentHour = 10 // 10 AM
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf(
                        "businessHoursOnly" to true,
                        "currentHour" to currentHour
                    )
                )

                val allowed = dynamicAuthzService.evaluateContextConditions(context)

                allowed shouldBe true
            }

            it("should deny access outside business hours") {
                val currentHour = 20 // 8 PM
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf(
                        "businessHoursOnly" to true,
                        "currentHour" to currentHour
                    )
                )

                val allowed = dynamicAuthzService.evaluateContextConditions(context)

                allowed shouldBe false
            }

            it("should allow access from allowed IP range") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf(
                        "allowedIPRanges" to listOf("192.168.1.0/24", "10.0.0.0/8"),
                        "ipAddress" to "192.168.1.100"
                    )
                )

                val allowed = dynamicAuthzService.evaluateContextConditions(context)

                allowed shouldBe true
            }

            it("should deny access from disallowed IP range") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf(
                        "allowedIPRanges" to listOf("192.168.1.0/24"),
                        "ipAddress" to "203.0.113.1"
                    )
                )

                val allowed = dynamicAuthzService.evaluateContextConditions(context)

                allowed shouldBe false
            }

            it("should allow access from managed device") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf(
                        "managedDeviceOnly" to true,
                        "isManagedDevice" to true
                    )
                )

                val allowed = dynamicAuthzService.evaluateContextConditions(context)

                allowed shouldBe true
            }

            it("should deny access from unmanaged device") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf(
                        "managedDeviceOnly" to true,
                        "isManagedDevice" to false
                    )
                )

                val allowed = dynamicAuthzService.evaluateContextConditions(context)

                allowed shouldBe false
            }

            it("should allow access when VPN is required and connected") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf(
                        "vpnRequired" to true,
                        "isVPN" to true
                    )
                )

                val allowed = dynamicAuthzService.evaluateContextConditions(context)

                allowed shouldBe true
            }

            it("should deny access when VPN is required but not connected") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null,
                    environmentalAttributes = mapOf(
                        "vpnRequired" to true,
                        "isVPN" to false
                    )
                )

                val allowed = dynamicAuthzService.evaluateContextConditions(context)

                allowed shouldBe false
            }

            it("should allow access when no restrictions are set") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val allowed = dynamicAuthzService.evaluateContextConditions(context)

                allowed shouldBe true
            }
        }

        describe("requestJITAccess") {

            it("should create JIT access request") {
                val request = dynamicAuthzService.requestJITAccess(
                    subjectId = "user123",
                    resourceId = "doc456",
                    action = "read",
                    requestedBy = "user123",
                    approvalRequired = false,
                    durationMinutes = 60
                )

                request.id shouldNotBe null
                request.subjectId shouldBe "user123"
                request.resourceId shouldBe "doc456"
                request.action shouldBe "read"
                request.status shouldBe JITAccessStatus.APPROVED
                request.expiresAt shouldNotBe null
            }

            it("should create pending approval request") {
                val request = dynamicAuthzService.requestJITAccess(
                    subjectId = "user123",
                    resourceId = "doc456",
                    action = "read",
                    requestedBy = "user123",
                    approvalRequired = true,
                    durationMinutes = 60
                )

                request.status shouldBe JITAccessStatus.PENDING_APPROVAL
                request.approvedBy shouldBe null
            }
        }

        describe("checkUsageLimits") {

            it("should return usage limit result") {
                val result = dynamicAuthzService.checkUsageLimits(
                    subjectId = "user123",
                    resourceType = "document",
                    tenantId = null
                )

                result.withinLimits shouldBe true
                (result.currentUsage >= 0L) shouldBe true
                (result.limit >= 0L) shouldBe true
                result.resetAt shouldNotBe null
            }
        }
    }
})

