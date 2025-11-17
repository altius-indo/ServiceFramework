package com.enterprise.framework.authz.verticles

import com.enterprise.framework.authz.engines.*
import com.enterprise.framework.authz.repositories.*
import com.enterprise.framework.authz.services.*
import com.enterprise.framework.config.ConfigLoader
import com.enterprise.framework.service.RedisCacheService
import com.enterprise.framework.verticle.BaseVerticle
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Authorization Verticle
 * Initializes and manages authorization services
 */
class AuthorizationVerticle : BaseVerticle() {

    private lateinit var authorizationService: AuthorizationService
    private lateinit var policyService: PolicyService
    private lateinit var permissionService: PermissionService
    private lateinit var auditService: AuditService
    private lateinit var dynamicAuthzService: DynamicAuthorizationService

    override suspend fun initialize() {
        // Load configuration
        val config = config() ?: JsonObject()
        val authzConfig = config.getJsonObject("authorization") ?: JsonObject()

        // Initialize repositories
        val roleRepository = RoleRepository(vertx)
        val permissionRepository = PermissionRepository(vertx)
        val policyRepository = PolicyRepository(vertx)
        val relationshipRepository = RelationshipRepository(vertx)
        val tenantRepository = TenantRepository(vertx)
        val resourceRepository = ResourceRepository(vertx)

        // Initialize cache service if configured
        val cacheService = if (authzConfig.getBoolean("cache.enabled", true)) {
            val redisConfig = config.getJsonObject("redis")
            if (redisConfig != null) {
                val redisCacheService = RedisCacheService(
                    vertx,
                    com.enterprise.framework.config.RedisConfig(
                        host = redisConfig.getString("host", "localhost"),
                        port = redisConfig.getInteger("port", 6379),
                        password = redisConfig.getString("password")
                    )
                )
                redisCacheService.initialize()
                redisCacheService
            } else {
                null
            }
        } else {
            null
        }

        // Initialize authorization engines
        val rbacEngine = RbacEngine(roleRepository, permissionRepository)
        val abacEngine = AbacEngine(policyRepository)
        val rebacEngine = RebacEngine(relationshipRepository)

        // Initialize services
        authorizationService = AuthorizationService(
            vertx,
            rbacEngine,
            abacEngine,
            rebacEngine,
            cacheService
        )
        policyService = PolicyService(policyRepository)
        permissionService = PermissionService(permissionRepository, roleRepository)
        auditService = AuditService(vertx)
        dynamicAuthzService = DynamicAuthorizationService(vertx)

        // Register event bus consumers
        registerEventBusConsumers()

        // Store services in verticle context for access by handlers
        vertx.sharedData().getLocalMap<String, Any>("authz.services").apply {
            put("authorizationService", authorizationService)
            put("policyService", policyService)
            put("permissionService", permissionService)
            put("auditService", auditService)
            put("dynamicAuthzService", dynamicAuthzService)
        }
    }

    private suspend fun registerEventBusConsumers() {
        // Register consumer for authorization requests
        vertx.eventBus().consumer<JsonObject>("authz.authorize") { message ->
            CoroutineScope(vertx.dispatcher()).launch {
                try {
                    val body = message.body()
                    val context = com.enterprise.framework.authz.models.AuthorizationContext(
                        subjectId = body.getString("subjectId"),
                        resourceId = body.getString("resourceId"),
                        resourceType = body.getString("resourceType", "resource"),
                        action = body.getString("action"),
                        tenantId = body.getString("tenantId"),
                        userAttributes = body.getJsonObject("userAttributes")?.map?.toMap() ?: emptyMap(),
                        resourceAttributes = body.getJsonObject("resourceAttributes")?.map?.toMap() ?: emptyMap(),
                        environmentalAttributes = body.getJsonObject("environmentalAttributes")?.map?.toMap() ?: emptyMap()
                    )
                    
                    val decision = authorizationService.authorize(context)
                    message.reply(JsonObject()
                        .put("allowed", decision.allowed)
                        .put("reason", decision.reason)
                        .put("appliedPolicies", JsonObject().apply {
                            decision.appliedPolicies.forEachIndexed { index, policy ->
                                put(index.toString(), policy)
                            }
                        })
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Error handling authorization request" }
                    message.fail(500, e.message ?: "Internal error")
                }
            }
        }

        // Register consumer for audit logging
        vertx.eventBus().consumer<JsonObject>("authz.audit") { message ->
            CoroutineScope(vertx.dispatcher()).launch {
                try {
                    // In production, persist audit log to database
                    logger.debug { "Audit log received: ${message.body()}" }
                } catch (e: Exception) {
                    logger.error(e) { "Error handling audit log" }
                }
            }
        }

        logger.info { "Event bus consumers registered" }
    }
}

