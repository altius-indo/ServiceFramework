package com.enterprise.framework.verticle

import com.enterprise.framework.config.ConfigLoader
import com.enterprise.framework.handler.ApiHandler
import com.enterprise.framework.handler.EnhancedAuthHandler
import com.enterprise.framework.handler.HealthCheckHandler
import com.enterprise.framework.repository.AuditLogRepository
import com.enterprise.framework.repository.UserRepository
import com.enterprise.framework.service.*
import io.vertx.core.Future
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.LoggerHandler
import io.vertx.ext.web.handler.TimeoutHandler
import io.vertx.kotlin.coroutines.await
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.RedisOptions
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.net.URI

/**
 * Enhanced HTTP server verticle with integrated authentication services.
 *
 * This verticle sets up the HTTP server with comprehensive authentication,
 * security controls, and session management capabilities.
 */
class EnhancedHttpServerVerticle : BaseVerticle() {

    private lateinit var router: Router
    private lateinit var enhancedAuthHandler: EnhancedAuthHandler
    private lateinit var redisClient: Redis
    private lateinit var dynamoDbClient: DynamoDbAsyncClient
    private lateinit var passwordHashingService: PasswordHashingService

    override suspend fun initialize() {
        router = Router.router(vertx)

        // Initialize infrastructure
        initializeInfrastructure()

        // Initialize authentication services
        initializeAuthenticationServices()

        // Setup middleware and routes
        setupMiddleware()
        setupRoutes()
        startHttpServer()
    }

    /**
     * Initializes infrastructure components (Redis, DynamoDB).
     */
    private suspend fun initializeInfrastructure() {
        logger.info { "Initializing infrastructure..." }

        val configLoader = ConfigLoader(vertx)
        val redisConfig = configLoader.getRedisConfig(config)
        val databaseConfig = configLoader.getDatabaseConfig(config)

        // Initialize Redis
        val redisOptions = RedisOptions()
            .setConnectionString("redis://${redisConfig.host}:${redisConfig.port}")

        redisConfig.password?.let { password ->
            redisOptions.setPassword(password)
        }

        redisClient = Redis.createClient(vertx, redisOptions)
        redisClient.connect().await()
        logger.info { "Redis client connected successfully" }

        // Initialize DynamoDB
        dynamoDbClient = DynamoDbAsyncClient.builder()
            .region(Region.of(databaseConfig.region))
            .endpointOverride(URI.create(databaseConfig.dynamoDbEndpoint))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()

        logger.info { "DynamoDB client initialized successfully" }
    }

    /**
     * Initializes all authentication services.
     */
    private fun initializeAuthenticationServices() {
        logger.info { "Initializing authentication services..." }

        val configLoader = ConfigLoader(vertx)

        // Load configurations
        val jwtConfig = configLoader.getJwtConfig(config)
        val passwordPolicy = configLoader.getPasswordPolicy(config)
        val sessionConfig = configLoader.getSessionConfig(config)
        val rateLimitingConfig = configLoader.getRateLimitingConfig(config)
        val bruteForceConfig = configLoader.getBruteForceConfig(config)
        val databaseConfig = configLoader.getDatabaseConfig(config)

        // Create JWT auth provider with algorithm support
        val jwtKeyProvider = JwtKeyProvider(vertx)
        val jwtAuth = jwtKeyProvider.createJwtAuth(jwtConfig)

        // Initialize Redis API
        val redisApi = RedisAPI.api(redisClient)

        // Initialize core services
        passwordHashingService = PasswordHashingService()
        val passwordPolicyValidator = PasswordPolicyValidator(passwordPolicy)

        // Initialize repository
        val userRepository = UserRepository(vertx, dynamoDbClient, databaseConfig.tableName)
        val auditLogRepository = AuditLogRepository(databaseConfig)
        val auditLogService = AuditLogService(auditLogRepository)

        // Initialize credential service
        val credentialService = CredentialService(
            passwordHashingService = passwordHashingService,
            passwordPolicyValidator = passwordPolicyValidator,
            userRepository = userRepository
        )

        // Initialize token services
        val tokenRevocationService = TokenRevocationService(redisApi)
        val tokenService = TokenService(
            jwtAuth = jwtAuth,
            config = jwtConfig,
            revocationService = tokenRevocationService
        )

        // Initialize session service
        val sessionService = SessionService(
            redisApi = redisApi,
            config = sessionConfig
        )

        // Initialize security services
        val rateLimitService = RateLimitService(
            redisApi = redisApi,
            config = rateLimitingConfig
        )

        val bruteForceProtectionService = BruteForceProtectionService(
            userRepository = userRepository,
            config = bruteForceConfig
        )

        // Initialize enhanced auth handler
        enhancedAuthHandler = EnhancedAuthHandler(
            userRepository = userRepository,
            credentialService = credentialService,
            tokenService = tokenService,
            sessionService = sessionService,
            rateLimitService = rateLimitService,
            bruteForceProtectionService = bruteForceProtectionService,
            auditLogService = auditLogService
        )

        logger.info { "Authentication services initialized successfully" }
    }

    private fun setupMiddleware() {
        logger.info { "Setting up middleware..." }

        router.route().handler(LoggerHandler.create())
        router.route().handler(BodyHandler.create())
        router.route().handler(TimeoutHandler.create(30000))

        router.route().handler(CorsHandler.create()
            .addOrigin("*")
            .allowedMethod(io.vertx.core.http.HttpMethod.GET)
            .allowedMethod(io.vertx.core.http.HttpMethod.POST)
            .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
            .allowedMethod(io.vertx.core.http.HttpMethod.DELETE)
            .allowedMethod(io.vertx.core.http.HttpMethod.PATCH)
            .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
            .allowedHeader("Content-Type")
            .allowedHeader("Authorization")
        )
    }

    private fun setupRoutes() {
        logger.info { "Setting up routes..." }

        val healthCheckHandler = HealthCheckHandler(vertx)
        val apiHandler = ApiHandler()

        // Health check routes (public)
        router.get("/health").handler(healthCheckHandler::handle)
        router.get("/ready").handler(healthCheckHandler::handleReadiness)
        router.get("/live").handler(healthCheckHandler::handleLiveness)

        // Authentication routes (public)
        val authRouter = Router.router(vertx)
        authRouter.post("/login").handler(enhancedAuthHandler::handleLogin)
        authRouter.post("/refresh").handler(enhancedAuthHandler::handleTokenRefresh)
        authRouter.post("/introspect").handler(enhancedAuthHandler::handleTokenIntrospection)

        // Protected auth routes (require authentication)
        authRouter.post("/logout")
            .handler(enhancedAuthHandler::authenticate)
            .handler(enhancedAuthHandler::handleLogout)

        authRouter.get("/sessions")
            .handler(enhancedAuthHandler::authenticate)
            .handler(enhancedAuthHandler::handleListSessions)

        authRouter.delete("/sessions/:sessionId")
            .handler(enhancedAuthHandler::authenticate)
            .handler(enhancedAuthHandler::handleTerminateSession)

        router.route("/auth/*").subRouter(authRouter)

        // API routes (protected)
        val apiRouter = Router.router(vertx)
        apiRouter.route().handler(enhancedAuthHandler::authenticate)
        apiRouter.get("/").handler(apiHandler::handleList)
        apiRouter.post("/").handler(apiHandler::handleCreate)
        apiRouter.get("/:id").handler(apiHandler::handleGet)
        apiRouter.put("/:id").handler(apiHandler::handleUpdate)
        apiRouter.delete("/:id").handler(apiHandler::handleDelete)

        router.route("/api/v1/*").subRouter(apiRouter)

        // 404 handler (must be last)
        router.route().last().handler { ctx ->
            ctx.response()
                .setStatusCode(404)
                .putHeader("content-type", "application/json")
                .end("""{"error": "Not Found", "path": "${ctx.request().path()}"}""")
        }

        logger.info { "Routes configured successfully" }
    }

    private suspend fun startHttpServer() {
        val serverConfig = config.getJsonObject("server")
        val host = serverConfig?.getString("host") ?: "0.0.0.0"
        val port = serverConfig?.getInteger("port") ?: 8080

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port, host)
            .await()

        logger.info { "HTTP server started on $host:$port" }
        logger.info { "" }
        logger.info { "=== Available Endpoints ===" }
        logger.info { "Health: GET /health, /ready, /live" }
        logger.info { "Auth:   POST /auth/login" }
        logger.info { "        POST /auth/refresh" }
        logger.info { "        POST /auth/logout (protected)" }
        logger.info { "        GET  /auth/sessions (protected)" }
        logger.info { "        DELETE /auth/sessions/:sessionId (protected)" }
        logger.info { "        POST /auth/introspect" }
        logger.info { "API:    /api/v1/* (all protected)" }
        logger.info { "=========================" }
    }

    override suspend fun cleanup() {
        logger.info { "Cleaning up HTTP server..." }

        // Cleanup password hashing service
        passwordHashingService.cleanup()

        // Close Redis client
        redisClient.close()

        // Close DynamoDB client
        dynamoDbClient.close()

        logger.info { "Cleanup complete" }
    }
}
