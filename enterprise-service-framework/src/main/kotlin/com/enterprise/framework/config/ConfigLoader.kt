package com.enterprise.framework.config

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Handles the loading of application configuration from various sources.
 *
 * This class provides a centralized mechanism for loading configuration settings.
 * It follows a hierarchical approach, allowing for overrides from different
 * environments. The order of precedence for loading configurations is:
 * 1. Environment variables (highest priority)
 * 2. System properties
 * 3. `application.json` file (lowest priority)
 *
 * @property vertx The Vert.x instance used for asynchronous operations.
 */
class ConfigLoader(private val vertx: Vertx) {

    /**
     * Asynchronously loads the application configuration from all configured sources.
     *
     * This method combines settings from the `application.json` file, system properties,
     * and environment variables into a single `JsonObject`. It logs the result
     * upon successful loading or logs an error if the process fails.
     *
     * @return A `JsonObject` containing the merged application configuration.
     * @throws Exception if the configuration cannot be loaded.
     */
    suspend fun loadConfig(): JsonObject {
        logger.info { "Loading application configuration..." }

        val fileStore = ConfigStoreOptions()
            .setType("file")
            .setFormat("json")
            .setConfig(JsonObject().put("path", "application.json"))

        val sysPropsStore = ConfigStoreOptions()
            .setType("sys")

        val envStore = ConfigStoreOptions()
            .setType("env")

        val options = ConfigRetrieverOptions()
            .addStore(fileStore)
            .addStore(sysPropsStore)
            .addStore(envStore)

        val retriever = ConfigRetriever.create(vertx, options)

        return try {
            val config = retriever.config.await()
            logger.info { "Configuration loaded successfully" }
            logger.debug { "Configuration: ${config.encodePrettily()}" }
            config
        } catch (e: Exception) {
            logger.error(e) { "Failed to load configuration" }
            throw e
        }
    }

    /**
     * Extracts and constructs a [ServerConfig] object from the provided configuration.
     *
     * This method retrieves the server-related settings from the main configuration
     * `JsonObject` and uses them to create a `ServerConfig` data class instance.
     * It provides default values for any missing server settings.
     *
     * @param config The application's configuration `JsonObject`.
     * @return A `ServerConfig` instance populated with the server settings.
     */
    fun getServerConfig(config: JsonObject): ServerConfig {
        val serverConfig = config.getJsonObject("server") ?: JsonObject()
        return ServerConfig(
            host = serverConfig.getString("host", "0.0.0.0"),
            port = serverConfig.getInteger("port", 8080),
            ssl = serverConfig.getBoolean("ssl", false)
        )
    }

    /**
     * Extracts and constructs a [DatabaseConfig] object from the provided configuration.
     *
     * This method retrieves the DynamoDB-related settings from the main configuration
     * `JsonObject` and uses them to create a `DatabaseConfig` data class instance.
     * It provides default values for any missing database settings.
     *
     * @param config The application's configuration `JsonObject`.
     * @return A `DatabaseConfig` instance populated with the database settings.
     */
    fun getDatabaseConfig(config: JsonObject): DatabaseConfig {
        val dbConfig = config.getJsonObject("database")?.getJsonObject("dynamodb") ?: JsonObject()
        return DatabaseConfig(
            dynamoDbEndpoint = dbConfig.getString("endpoint", "http://localhost:8000"),
            region = dbConfig.getString("region", "us-east-1"),
            tableName = dbConfig.getString("tableName", "enterprise-data")
        )
    }

    /**
     * Extracts and constructs a [RedisConfig] object from the provided configuration.
     *
     * This method retrieves the Redis-related settings from the main configuration
     * `JsonObject` and uses them to create a `RedisConfig` data class instance.
     * It provides default values for any missing Redis settings.
     *
     * @param config The application's configuration `JsonObject`.
     * @return A `RedisConfig` instance populated with the Redis settings.
     */
    fun getRedisConfig(config: JsonObject): RedisConfig {
        val redisConfig = config.getJsonObject("redis") ?: JsonObject()
        return RedisConfig(
            host = redisConfig.getString("host", "localhost"),
            port = redisConfig.getInteger("port", 6379),
            password = redisConfig.getString("password")
        )
    }

    /**
     * Extracts JWT configuration from the provided configuration.
     *
     * @param config The application's configuration `JsonObject`.
     * @return JWT configuration settings
     */
    fun getJwtConfig(config: JsonObject): com.enterprise.framework.model.JwtConfig {
        val authConfig = config.getJsonObject("auth") ?: JsonObject()
        val jwtConfig = authConfig.getJsonObject("jwt") ?: JsonObject()

        return com.enterprise.framework.model.JwtConfig(
            secret = jwtConfig.getString("secret", "change-this-secret-in-production"),
            issuer = jwtConfig.getString("issuer", "enterprise-framework"),
            accessTokenExpirationSeconds = jwtConfig.getLong("accessTokenExpirationSeconds", 3600L),
            refreshTokenExpirationSeconds = jwtConfig.getLong("refreshTokenExpirationSeconds", 604800L),
            algorithm = jwtConfig.getString("algorithm", "HS256"),
            publicKey = jwtConfig.getString("publicKey"),
            privateKey = jwtConfig.getString("privateKey")
        )
    }

    /**
     * Extracts password policy configuration from the provided configuration.
     *
     * @param config The application's configuration `JsonObject`.
     * @return Password policy configuration
     */
    fun getPasswordPolicy(config: JsonObject): com.enterprise.framework.model.PasswordPolicy {
        val authConfig = config.getJsonObject("auth") ?: JsonObject()
        val passwordConfig = authConfig.getJsonObject("password") ?: JsonObject()

        return com.enterprise.framework.model.PasswordPolicy(
            minLength = passwordConfig.getInteger("minLength", 12),
            requireUppercase = passwordConfig.getBoolean("requireUppercase", true),
            requireLowercase = passwordConfig.getBoolean("requireLowercase", true),
            requireDigit = passwordConfig.getBoolean("requireDigit", true),
            requireSpecialChar = passwordConfig.getBoolean("requireSpecialChar", true),
            preventReuse = passwordConfig.getInteger("preventReuse", 5),
            expirationDays = passwordConfig.getInteger("expirationDays")
        )
    }

    /**
     * Extracts session configuration from the provided configuration.
     *
     * @param config The application's configuration `JsonObject`.
     * @return Session configuration
     */
    fun getSessionConfig(config: JsonObject): com.enterprise.framework.model.SessionConfig {
        val authConfig = config.getJsonObject("auth") ?: JsonObject()
        val sessionConfig = authConfig.getJsonObject("session") ?: JsonObject()

        return com.enterprise.framework.model.SessionConfig(
            absoluteTimeoutSeconds = sessionConfig.getLong("absoluteTimeoutSeconds", 86400L),
            idleTimeoutSeconds = sessionConfig.getLong("idleTimeoutSeconds", 3600L),
            maxConcurrentSessions = sessionConfig.getInteger("maxConcurrentSessions", 5),
            renewOnActivity = sessionConfig.getBoolean("renewOnActivity", true)
        )
    }

    /**
     * Extracts rate limiting configuration from the provided configuration.
     *
     * @param config The application's configuration `JsonObject`.
     * @return Rate limiting configuration
     */
    fun getRateLimitingConfig(config: JsonObject): com.enterprise.framework.model.RateLimitingConfig {
        val authConfig = config.getJsonObject("auth") ?: JsonObject()
        val rateLimitConfig = authConfig.getJsonObject("rateLimiting") ?: JsonObject()

        return com.enterprise.framework.model.RateLimitingConfig(
            enabled = rateLimitConfig.getBoolean("enabled", true),
            maxRequests = rateLimitConfig.getInteger("maxRequests", 10),
            windowSeconds = rateLimitConfig.getLong("windowSeconds", 60L),
            blacklistDurationSeconds = rateLimitConfig.getLong("blacklistDurationSeconds", 300L)
        )
    }

    /**
     * Extracts brute force protection configuration from the provided configuration.
     *
     * @param config The application's configuration `JsonObject`.
     * @return Brute force protection configuration
     */
    fun getBruteForceConfig(config: JsonObject): com.enterprise.framework.model.BruteForceConfig {
        val authConfig = config.getJsonObject("auth") ?: JsonObject()
        val bruteForceConfig = authConfig.getJsonObject("bruteForce") ?: JsonObject()

        return com.enterprise.framework.model.BruteForceConfig(
            enabled = bruteForceConfig.getBoolean("enabled", true),
            maxFailedAttempts = bruteForceConfig.getInteger("maxFailedAttempts", 5),
            lockoutDurationSeconds = bruteForceConfig.getLong("lockoutDurationSeconds", 900L),
            resetAfterSuccessfulLogin = bruteForceConfig.getBoolean("resetAfterSuccessfulLogin", true)
        )
    }
}
