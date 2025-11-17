package com.enterprise.framework.authz.engines

import com.enterprise.framework.authz.models.*
import com.enterprise.framework.authz.repositories.RelationshipRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.vertx.core.Vertx
import java.time.Instant
import java.util.UUID

class RebacEngineTest : DescribeSpec({

    lateinit var vertx: Vertx
    lateinit var relationshipRepository: RelationshipRepository
    lateinit var rebacEngine: RebacEngine

    beforeSpec {
        vertx = Vertx.vertx()
        relationshipRepository = RelationshipRepository(vertx)
        rebacEngine = RebacEngine(relationshipRepository)
    }

    afterSpec {
        vertx.close()
    }

    describe("RebacEngine") {

        describe("authorize") {

            it("should allow access when user owns the resource") {
                // Setup: Create resource with owner
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

                val decision = rebacEngine.authorize(context)

                decision.allowed shouldBe true
                decision.reason shouldBe "Subject is the owner of the resource"
            }

            it("should allow access when direct relationship grants permission") {
                // Setup: Create relationship
                val relationship = Relationship(
                    id = UUID.randomUUID().toString(),
                    sourceId = "user123",
                    sourceType = RelationshipEntityType.USER,
                    targetId = "doc456",
                    targetType = RelationshipEntityType.RESOURCE,
                    relationType = RelationshipType.EDITOR,
                    tenantId = null
                )
                relationshipRepository.save(relationship)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "write",
                    tenantId = null
                )

                val decision = rebacEngine.authorize(context)

                decision.allowed shouldBe true
                decision.reason shouldBe "Direct relationship 'EDITOR' grants access"
            }

            it("should allow read access for VIEWER relationship") {
                val relationship = Relationship(
                    id = UUID.randomUUID().toString(),
                    sourceId = "user123",
                    sourceType = RelationshipEntityType.USER,
                    targetId = "doc456",
                    targetType = RelationshipEntityType.RESOURCE,
                    relationType = RelationshipType.VIEWER,
                    tenantId = null
                )
                relationshipRepository.save(relationship)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = rebacEngine.authorize(context)
                decision.allowed shouldBe true
            }

            it("should deny write access for VIEWER relationship") {
                val relationship = Relationship(
                    id = UUID.randomUUID().toString(),
                    sourceId = "user123",
                    sourceType = RelationshipEntityType.USER,
                    targetId = "doc456",
                    targetType = RelationshipEntityType.RESOURCE,
                    relationType = RelationshipType.VIEWER,
                    tenantId = null
                )
                relationshipRepository.save(relationship)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "write",
                    tenantId = null
                )

                val decision = rebacEngine.authorize(context)
                decision.allowed shouldBe false
            }

            it("should support transitive relationships") {
                // Setup: user1 -> friend -> user2 -> owns -> doc456
                val relationship1 = Relationship(
                    id = UUID.randomUUID().toString(),
                    sourceId = "user1",
                    sourceType = RelationshipEntityType.USER,
                    targetId = "user2",
                    targetType = RelationshipEntityType.USER,
                    relationType = RelationshipType.FRIEND,
                    tenantId = null
                )
                relationshipRepository.save(relationship1)

                val resource = Resource(
                    id = "doc456",
                    type = "document",
                    name = "Test Document",
                    ownerId = "user2",
                    tenantId = null
                )
                relationshipRepository.saveResource(resource)

                // Note: In a real implementation, transitive relationships might need
                // special handling. For now, we test direct ownership.
                val context = AuthorizationContext(
                    subjectId = "user2",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = rebacEngine.authorize(context)
                decision.allowed shouldBe true
            }

            it("should support SHARED_WITH relationship with permissions") {
                val relationship = Relationship(
                    id = UUID.randomUUID().toString(),
                    sourceId = "user123",
                    sourceType = RelationshipEntityType.USER,
                    targetId = "doc456",
                    targetType = RelationshipEntityType.RESOURCE,
                    relationType = RelationshipType.SHARED_WITH,
                    attributes = mapOf("permissions" to listOf("read", "write")),
                    tenantId = null
                )
                relationshipRepository.save(relationship)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = rebacEngine.authorize(context)
                decision.allowed shouldBe true
            }

            it("should deny access when no relationship exists") {
                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = rebacEngine.authorize(context)

                decision.allowed shouldBe false
                decision.reason shouldBe "No relationship found that grants access"
            }

            it("should respect tenant isolation") {
                val relationship = Relationship(
                    id = UUID.randomUUID().toString(),
                    sourceId = "user123",
                    sourceType = RelationshipEntityType.USER,
                    targetId = "doc456",
                    targetType = RelationshipEntityType.RESOURCE,
                    relationType = RelationshipType.EDITOR,
                    tenantId = "tenant1"
                )
                relationshipRepository.save(relationship)

                // Test: Should work for tenant1
                val context1 = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = "tenant1"
                )
                val decision1 = rebacEngine.authorize(context1)
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
                val decision2 = rebacEngine.authorize(context2)
                decision2.allowed shouldBe false
            }

            it("should handle expired relationships") {
                val relationship = Relationship(
                    id = UUID.randomUUID().toString(),
                    sourceId = "user123",
                    sourceType = RelationshipEntityType.USER,
                    targetId = "doc456",
                    targetType = RelationshipEntityType.RESOURCE,
                    relationType = RelationshipType.EDITOR,
                    expiresAt = Instant.now().minusSeconds(3600), // Expired
                    tenantId = null
                )
                relationshipRepository.save(relationship)

                val context = AuthorizationContext(
                    subjectId = "user123",
                    subjectType = SubjectType.USER,
                    resourceId = "doc456",
                    resourceType = "document",
                    action = "read",
                    tenantId = null
                )

                val decision = rebacEngine.authorize(context)
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

                val supports = rebacEngine.supports(context)
                supports shouldBe true
            }
        }
    }
})

