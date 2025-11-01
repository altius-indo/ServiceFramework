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
}
